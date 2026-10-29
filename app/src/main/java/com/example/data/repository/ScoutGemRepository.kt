package com.example.data.repository

import com.example.data.local.DiscoveredGemDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutGem
import com.example.data.model.toScoutGem
import com.example.data.network.GeminiSpotService
import com.example.data.network.OverpassRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Diagnostic summary produced when auto-cleanup runs on the local Room database.
 */
data class AutoCleanupReport(
    val timestamp: Long = System.currentTimeMillis(),
    val expiredRemoved: Int = 0,
    val distantRemoved: Int = 0,
    val totalRemoved: Int = 0,
    val anchorLat: Double = 0.0,
    val anchorLng: Double = 0.0
)

/**
 * Offline-First Repository for ScoutGem locations.
 * Room is the single source of truth; live spot discovery results from Firebase AI Logic (Gemini 3.8 Flash)
 * and Overpass API are inserted into Room in the background so the app remains 100% functional offline.
 */
class ScoutGemRepository(
    private val scoutDao: ScoutDao,
    private val discoveredGemDao: DiscoveredGemDao? = null,
    val overpassClient: OverpassRetrofitClient = OverpassRetrofitClient(),
    val geminiSpotService: GeminiSpotService = GeminiSpotService()
) {

    private val _lastCleanupReport = MutableStateFlow<AutoCleanupReport?>(null)
    val lastCleanupReport: StateFlow<AutoCleanupReport?> = _lastCleanupReport.asStateFlow()

    /**
     * Diagnostic telemetry log flow for the Overpass API inspector.
     */
    val lastDiagnosticLog: kotlinx.coroutines.flow.StateFlow<com.example.data.network.OverpassDiagnosticLog?> =
        overpassClient.lastDiagnosticLog

    /**
     * Reactive stream of all ScoutGems directly backed by Room database.
     */
    fun observeGems(userLat: Double = 38.7112, userLng: Double = -9.1423): Flow<List<ScoutGem>> {
        return scoutDao.getAllSpots().map { spotEntities ->
            spotEntities.map { entity -> entity.toScoutGem(userLat, userLng) }
        }
    }

    /**
     * Reactive stream of bookmarked ScoutGems backed by Room.
     */
    fun observeSavedGems(userLat: Double = 38.7112, userLng: Double = -9.1423): Flow<List<ScoutGem>> {
        return scoutDao.getSavedSpots().map { spotEntities ->
            spotEntities.map { entity -> entity.toScoutGem(userLat, userLng) }
        }
    }

    /**
     * Executes a suspendable block with exponential backoff retries.
     */
    suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelayMs
        var lastResult: Result<T> = Result.failure(IllegalStateException("No attempts made"))

        for (attempt in 1..maxAttempts) {
            lastResult = block()
            if (lastResult.isSuccess) {
                if (attempt > 1) {
                    android.util.Log.i("ScoutGemRepository", "Spot discovery succeeded on retry attempt #$attempt")
                }
                return lastResult
            }

            if (attempt < maxAttempts) {
                android.util.Log.w(
                    "ScoutGemRepository",
                    "Spot discovery attempt #$attempt failed. Retrying in ${currentDelay}ms... Error: ${lastResult.exceptionOrNull()?.message}"
                )
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }

        android.util.Log.e(
            "ScoutGemRepository",
            "All $maxAttempts attempts failed for spot discovery: ${lastResult.exceptionOrNull()?.message}"
        )
        return lastResult
    }

    /**
     * Periodically removes un-saved cached gems older than 30 days and un-saved spots outside
     * a 100km radius from the user's home/current GPS coordinates to keep the local database lean.
     * Strictly preserves all saved (isSaved=1), discovered (isDiscovered=1), and drafted (isOfflineDraft=1) gems.
     */
    suspend fun performAutoCleanup(
        userLat: Double = 38.7112,
        userLng: Double = -9.1423,
        maxAgeDays: Int = 30,
        maxRadiusKm: Double = 100.0
    ): AutoCleanupReport = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cutoffTimestamp = now - (maxAgeDays.toLong() * 24L * 60L * 60L * 1000L)

        // 1. Delete un-saved spots older than cutoff timestamp (e.g. 30 days)
        val expiredRemoved = scoutDao.deleteExpiredSpots(cutoffTimestamp)

        // 2. Fetch remaining un-saved transient spots to evaluate distance cutoff
        val transientSpots = scoutDao.getUnsavedTransientSpots()
        val maxRadiusMeters = (maxRadiusKm * 1000.0).toFloat()
        val distantSpotIds = mutableListOf<String>()

        if (userLat != 0.0 || userLng != 0.0) {
            for (spot in transientSpots) {
                val distance = com.example.data.location.LocationTracker.distanceBetween(
                    userLat, userLng, spot.latitude, spot.longitude
                )
                if (distance > maxRadiusMeters) {
                    distantSpotIds.add(spot.id)
                }
            }
        }

        val distantRemoved = if (distantSpotIds.isNotEmpty()) {
            scoutDao.deleteSpotsByIds(distantSpotIds)
        } else {
            0
        }

        val totalRemoved = expiredRemoved + distantRemoved
        val report = AutoCleanupReport(
            timestamp = now,
            expiredRemoved = expiredRemoved,
            distantRemoved = distantRemoved,
            totalRemoved = totalRemoved,
            anchorLat = userLat,
            anchorLng = userLng
        )

        _lastCleanupReport.value = report
        android.util.Log.i(
            "ScoutGemRepository",
            "Auto-cleanup completed: removed $expiredRemoved expired spots (> ${maxAgeDays}d) and $distantRemoved distant spots (> ${maxRadiusKm.toInt()}km). Total pruned: $totalRemoved."
        )

        report
    }

    /**
     * Discovers real, authentic, named spots near the user's location using Firebase AI Logic (Gemini 3.8 Flash).
     * Falls back to Overpass API / offline generator if AI discovery is offline or unavailable.
     */
    suspend fun syncLiveOsmGems(
        userLat: Double = 38.7112,
        userLng: Double = -9.1423,
        radiusMeters: Int = 1500,
        category: String = "all"
    ): Result<Int> = withContext(Dispatchers.IO) {
        // First try Firebase AI Logic (Gemini 3.8 Flash) for high quality verified spots
        val geminiResult = geminiSpotService.discoverGems(userLat, userLng, category, radiusMeters)
        if (geminiResult.isSuccess) {
            val spots = geminiResult.getOrNull().orEmpty()
            if (spots.isNotEmpty()) {
                scoutDao.clearFallbackSpots()
                deduplicateAndInsert(spots)
                return@withContext Result.success(spots.size)
            }
        }

        // Fallback to Overpass API scraper with exponential backoff if AI is unavailable or returned empty
        retryWithExponentialBackoff(maxAttempts = 2, initialDelayMs = 500L, factor = 1.5) {
            val fetchResult = overpassClient.fetchNearbyNodes(userLat, userLng, radiusMeters, category)
            fetchResult.fold(
                onSuccess = { osmEntities ->
                    if (osmEntities.isNotEmpty()) {
                        scoutDao.clearFallbackSpots()
                    }
                    val rawSpots = if (osmEntities.isNotEmpty()) {
                        osmEntities
                    } else {
                        overpassClient.generateNearbyFallbackSpots(userLat, userLng, 6, category)
                    }
                    val spotsWithinRadius = if (userLat != 0.0 || userLng != 0.0) {
                        rawSpots.filter { spot ->
                            com.example.data.location.LocationTracker.distanceBetween(userLat, userLng, spot.latitude, spot.longitude) <= 50000f
                        }
                    } else {
                        rawSpots
                    }
                    deduplicateAndInsert(spotsWithinRadius)
                    Result.success(spotsWithinRadius.size)
                },
                onFailure = { _ ->
                    val fallbackSpots = overpassClient.generateNearbyFallbackSpots(userLat, userLng, 6, category)
                    deduplicateAndInsert(fallbackSpots)
                    Result.success(fallbackSpots.size)
                }
            )
        }
    }

    private suspend fun deduplicateAndInsert(spots: List<ScoutSpotEntity>) {
        val existingSpots = scoutDao.getAllSpotsList()
        val unique = mutableListOf<ScoutSpotEntity>()

        spots.forEach { newSpot ->
            val newTitleNorm = newSpot.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            val isDuplicate = existingSpots.any { existing ->
                val existingTitleNorm = existing.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                existing.id == newSpot.id ||
                (newTitleNorm.isNotBlank() && newTitleNorm == existingTitleNorm) ||
                (com.example.data.location.LocationTracker.distanceBetween(
                    existing.latitude, existing.longitude, newSpot.latitude, newSpot.longitude
                ) < 80f && existing.category == newSpot.category)
            } || unique.any { added ->
                val addedTitleNorm = added.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                (newTitleNorm.isNotBlank() && newTitleNorm == addedTitleNorm) ||
                (com.example.data.location.LocationTracker.distanceBetween(
                    added.latitude, added.longitude, newSpot.latitude, newSpot.longitude
                ) < 80f && added.category == newSpot.category)
            }

            if (!isDuplicate) {
                unique.add(newSpot)
            }
        }

        if (unique.isNotEmpty()) {
            scoutDao.insertSpots(unique)
        }
    }

    /**
     * Toggles bookmark state in Room database.
     */
    suspend fun toggleSave(gemId: String, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        scoutDao.setSavedStatus(gemId, !currentlySaved)
    }

    /**
     * Toggles discovered state in Room database.
     */
    suspend fun toggleDiscovered(gemId: String, currentlyDiscovered: Boolean) = withContext(Dispatchers.IO) {
        val newDiscovered = !currentlyDiscovered
        val timestamp = if (newDiscovered) System.currentTimeMillis() else 0L
        scoutDao.setDiscoveredStatus(gemId, newDiscovered, timestamp)
    }

    /**
     * Executes an on-demand Overpass API diagnostic query and returns telemetry.
     */
    suspend fun runDiagnosticQuery(
        userLat: Double = 38.7112,
        userLng: Double = -9.1423,
        radiusMeters: Int = 1500
    ): com.example.data.network.OverpassDiagnosticLog = withContext(Dispatchers.IO) {
        val log = overpassClient.fetchDiagnosticLog(userLat, userLng, radiusMeters)
        if (!log.isFallbackTriggered && log.rawJson.isNotBlank()) {
            val adapter = com.squareup.moshi.Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
                .adapter(com.example.data.network.model.OverpassResponse::class.java)
            try {
                val response = adapter.fromJson(log.rawJson)
                if (response != null && response.elements.isNotEmpty()) {
                    val entities = overpassClient.mapElementsToEntities(response.elements, userLat, userLng)
                    if (entities.isNotEmpty()) {
                        scoutDao.insertSpots(entities)
                    }
                }
            } catch (_: Exception) {}
        }
        log
    }
}

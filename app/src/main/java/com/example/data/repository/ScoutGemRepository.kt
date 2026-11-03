package com.example.data.repository

import com.example.data.local.DiscoveredGemDao
import com.example.data.local.RecentSearchDao
import com.example.data.local.RecentSearchEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.location.DefaultLocationConfig
import com.example.data.model.ScoutGem
import com.example.data.model.toScoutGem
import com.example.data.network.GeminiSpotService
import com.example.data.network.OverpassRetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
open class ScoutGemRepository(
    private val scoutDao: ScoutDao,
    private val discoveredGemDao: DiscoveredGemDao? = null,
    private val recentSearchDao: RecentSearchDao? = null,
    val overpassClient: OverpassRetrofitClient = OverpassRetrofitClient(),
    val geminiSpotService: GeminiSpotService = GeminiSpotService(),
    val openTripMapService: com.example.data.network.OpenTripMapService = com.example.data.network.OpenTripMapService(),
    val geonamesService: com.example.data.network.GeonamesService = com.example.data.network.GeonamesService(),
    val unescoService: com.example.data.network.UnescoHeritageService = com.example.data.network.UnescoHeritageService(),
    val europeanaService: com.example.data.network.EuropeanaCulturalService = com.example.data.network.EuropeanaCulturalService(),
    val municipalService: com.example.data.network.MunicipalOpenDataService = com.example.data.network.MunicipalOpenDataService(),
    val wikidataSearchService: com.example.data.network.WikidataSearchService = com.example.data.network.WikidataSearchService()
) {

    open val recentSearches: Flow<List<RecentSearchEntity>> =
        recentSearchDao?.getRecentSearches() ?: flowOf(emptyList())

    open suspend fun recordRecentSearch(queryText: String, category: String = "all"): Unit = withContext(Dispatchers.IO) {
        val trimmed = queryText.trim()
        if (trimmed.isBlank() || recentSearchDao == null) return@withContext

        val existing = recentSearchDao.findByQueryText(trimmed)
        if (existing != null) {
            recentSearchDao.insertSearch(
                existing.copy(
                    timestamp = System.currentTimeMillis(),
                    category = category
                )
            )
        } else {
            recentSearchDao.insertSearch(
                RecentSearchEntity(
                    queryText = trimmed,
                    category = category,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        recentSearchDao.trimExcessSearches()
    }

    open suspend fun removeRecentSearch(id: String): Int = withContext(Dispatchers.IO) {
        recentSearchDao?.deleteSearchById(id) ?: 0
    }

    open suspend fun clearAllRecentSearches(): Int = withContext(Dispatchers.IO) {
        recentSearchDao?.clearAllSearches() ?: 0
    }

    private val _lastCleanupReport = MutableStateFlow<AutoCleanupReport?>(null)
    val lastCleanupReport: StateFlow<AutoCleanupReport?> = _lastCleanupReport.asStateFlow()

    /**
     * Diagnostic telemetry log flow for the Overpass API inspector.
     */
    val lastDiagnosticLog: kotlinx.coroutines.flow.StateFlow<com.example.data.network.OverpassDiagnosticLog?> =
        overpassClient.lastDiagnosticLog

    private val attemptedEnrichmentIds = java.util.Collections.synchronizedSet(HashSet<String>())
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    /**
     * Reactive stream of all ScoutGems directly backed by Room database.
     * Automatically triggers reactive background photo resolution via Wikimedia Commons API
     * whenever new spot entities stream into Room.
     */
    fun observeGems(
        userLat: Double = DefaultLocationConfig.activeFallbackLat,
        userLng: Double = DefaultLocationConfig.activeFallbackLng
    ): Flow<List<ScoutGem>> {
        return scoutDao.getAllSpots().map { spotEntities ->
            val unEnriched = spotEntities.filter { spot ->
                !attemptedEnrichmentIds.contains(spot.id) &&
                        com.example.util.LocationPhotoResolver.isGenericOrStockPhoto(spot.imageUrl)
            }
            if (unEnriched.isNotEmpty()) {
                unEnriched.forEach { attemptedEnrichmentIds.add(it.id) }
                repositoryScope.launch {
                    enrichSpotsPhotosInBackground(unEnriched)
                }
            }
            spotEntities.map { entity -> entity.toScoutGem(userLat, userLng) }
        }
    }

    /**
     * Reactive stream of bookmarked ScoutGems backed by Room.
     */
    fun observeSavedGems(
        userLat: Double = DefaultLocationConfig.activeFallbackLat,
        userLng: Double = DefaultLocationConfig.activeFallbackLng
    ): Flow<List<ScoutGem>> {
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
        userLat: Double = DefaultLocationConfig.activeFallbackLat,
        userLng: Double = DefaultLocationConfig.activeFallbackLng,
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
     * Manually forces a purge of all transient cached POIs from the local database.
     * Strictly preserves all saved (isSaved=1), discovered (isDiscovered=1), and drafted (isOfflineDraft=1) gems.
     */
    suspend fun clearTransientCache(): Int = withContext(Dispatchers.IO) {
        val purged = scoutDao.clearAllTransientSpots()
        android.util.Log.i("ScoutGemRepository", "Manual cache clear purged $purged transient spots from Room DB.")
        purged
    }

    /**
     * Discovers real, authentic, named spots near the user's location using Firebase AI Logic (Gemini 3.8 Flash).
     * Falls back to Overpass API / offline generator if AI discovery is offline or unavailable.
     */
    suspend fun syncLiveOsmGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        radiusMeters: Int = 1500,
        category: String = "all",
        customQuery: String = ""
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (customQuery.isNotBlank()) {
            recordRecentSearch(customQuery, category)
        }
        // First try Firebase AI Logic (Gemini 3.5 Flash-Lite / 3.8 Flash) for high quality verified spots
        val geminiResult = geminiSpotService.discoverGems(userLat, userLng, category, radiusMeters, customQuery)
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
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichSpotsPhotosInBackground(spotsWithinRadius)
                    }
                    Result.success(spotsWithinRadius.size)
                },
                onFailure = { _ ->
                    val fallbackSpots = overpassClient.generateNearbyFallbackSpots(userLat, userLng, 6, category)
                    deduplicateAndInsert(fallbackSpots)
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichSpotsPhotosInBackground(fallbackSpots)
                    }
                    Result.success(fallbackSpots.size)
                }
            )
        }
    }

    /**
     * Fetches touristic spots directly from OpenTripMap global POI service into Room.
     */
    suspend fun syncOpenTripMapGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        radiusMeters: Int = 3000
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = openTripMapService.fetchTouristicPlaces(userLat, userLng, radiusMeters)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichSpotsPhotosInBackground(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches heritage monuments and landmarks from Wikidata SPARQL graph service into Room.
     */
    suspend fun syncWikidataGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        category: String = "all"
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = wikidataSearchService.fetchNearbyWikidataSpots(userLat, userLng, category)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichSpotsPhotosInBackground(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches gazetteer geographical POIs from GeoNames database into Room.
     */
    suspend fun syncGeonamesGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = geonamesService.fetchNearbyGeonamesPOIs(userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichSpotsPhotosInBackground(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches official UNESCO World Heritage properties into Room.
     */
    suspend fun syncUnescoGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = unescoService.fetchNearbyUnescoSites(userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichSpotsPhotosInBackground(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches European cultural heritage repository items into Room.
     */
    suspend fun syncEuropeanaGems(
        locationName: String = "Lisbon",
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = europeanaService.fetchEuropeanaCulturalSpots(locationName, userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichSpotsPhotosInBackground(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches municipal open data public art, walking trails, and parks into Room.
     */
    suspend fun syncMunicipalGems(
        cityName: String = "Lisbon",
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = municipalService.fetchMunicipalDataSpots(cityName, userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichSpotsPhotosInBackground(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Queries all active touristic place sources simultaneously (OpenTripMap, Wikidata, Overpass, GeoNames, UNESCO, Europeana, Municipal Open Data, Gemini AI).
     */
    suspend fun syncMultiSourceGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        cityName: String = "Lisbon",
        sourceIds: List<String> = listOf("open_trip_map", "wikivoyage_wikidata", "overpass_osm", "geonames_gazetteer", "unesco_world_heritage", "europeana_cultural", "municipal_open_data", "firebase_gemini_ai")
    ): Result<Int> = withContext(Dispatchers.IO) {
        var totalFetched = 0

        if (sourceIds.contains("open_trip_map")) {
            totalFetched += syncOpenTripMapGems(userLat, userLng).getOrDefault(0)
        }
        if (sourceIds.contains("wikivoyage_wikidata")) {
            totalFetched += syncWikidataGems(userLat, userLng).getOrDefault(0)
        }
        if (sourceIds.contains("geonames_gazetteer")) {
            totalFetched += syncGeonamesGems(userLat, userLng).getOrDefault(0)
        }
        if (sourceIds.contains("unesco_world_heritage")) {
            totalFetched += syncUnescoGems(userLat, userLng).getOrDefault(0)
        }
        if (sourceIds.contains("europeana_cultural")) {
            totalFetched += syncEuropeanaGems(cityName, userLat, userLng).getOrDefault(0)
        }
        if (sourceIds.contains("municipal_open_data")) {
            totalFetched += syncMunicipalGems(cityName, userLat, userLng).getOrDefault(0)
        }
        if (sourceIds.contains("overpass_osm") || sourceIds.contains("firebase_gemini_ai")) {
            totalFetched += syncLiveOsmGems(userLat, userLng).getOrDefault(0)
        }

        Result.success(totalFetched)
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
            } catch (e: Exception) {
                // Safe recovery: JSON parsing of raw Overpass telemetry failed, log warning telemetry and return raw log
                android.util.Log.w("ScoutGemRepository", "Failed to parse Overpass diagnostic JSON response", e)
            }
        }
        log
    }

    /**
     * Automatically enriches spots having placeholder or generic image URLs
     * by querying live Wikipedia & Wikimedia Commons APIs for 1200px HD resolution metadata
     * and caching the authentic image URLs directly in the Room database for permanent offline availability.
     * Prioritizes saved/bookmarked spots first, followed by active feed spots.
     */
    open suspend fun enrichSpotsPhotosInBackground(spots: List<ScoutSpotEntity> = emptyList()) = withContext(Dispatchers.IO) {
        val targets = if (spots.isNotEmpty()) {
            spots.filter { com.example.util.LocationPhotoResolver.isGenericOrStockPhoto(it.imageUrl) }
        } else {
            scoutDao.getAllSpotsList().filter { com.example.util.LocationPhotoResolver.isGenericOrStockPhoto(it.imageUrl) }
        }

        if (targets.isEmpty()) return@withContext

        // Tier 1: Saved and bookmarked spots (isSaved == true) first, Tier 2: Active feed spots
        val sortedTargets = targets.sortedByDescending { it.isSaved }

        // Enrich up to 30 spots per pass with graceful pacing
        for (spot in sortedTargets.take(30)) {
            try {
                val resolved = com.example.util.LocationPhotoResolver.resolveEnrichedPhoto(
                    title = spot.title,
                    category = spot.category,
                    districtOrLocation = spot.locationName,
                    latitude = spot.latitude,
                    longitude = spot.longitude,
                    spotId = spot.id
                )
                if (resolved.url.isNotBlank() && !com.example.util.LocationPhotoResolver.isGenericOrStockPhoto(resolved.url)) {
                    scoutDao.updateImageUrl(spot.id, resolved.url)
                    android.util.Log.i(
                        "ScoutGemRepository",
                        "Wikimedia Commons Resolution SUCCESS: Validated 1200px HD photo for '${spot.title}' (isSaved=${spot.isSaved}): ${resolved.url}"
                    )
                } else {
                    android.util.Log.i(
                        "ScoutGemRepository",
                        "Wikimedia Commons Resolution FALLBACK: Retaining existing image '${spot.imageUrl}' for '${spot.title}'."
                    )
                }
                delay(120L) // Polite delay between network queries
            } catch (e: Exception) {
                android.util.Log.w("ScoutGemRepository", "Background photo enrichment error for ${spot.id} ('${spot.title}'): ${e.message}")
            }
        }
    }

    /**
     * Triggered automatically when device transitions from offline to online state.
     * Resets attempted enrichment cache and re-queries Room for spots lacking high-res photos.
     * Prioritizes saved/bookmarked spots first, then active feed spots.
     */
    suspend fun triggerPhotoMetadataReSync() = withContext(Dispatchers.IO) {
        attemptedEnrichmentIds.clear()
        val allSpots = scoutDao.getAllSpotsList()
        val unEnriched = allSpots.filter { com.example.util.LocationPhotoResolver.isGenericOrStockPhoto(it.imageUrl) }
        if (unEnriched.isNotEmpty()) {
            val (savedSpots, feedSpots) = unEnriched.partition { it.isSaved }
            android.util.Log.i(
                "ScoutGemRepository",
                "Automatic Network Reconnection Re-Sync: Found ${unEnriched.size} spots needing photos (${savedSpots.size} saved/bookmarked, ${feedSpots.size} feed)."
            )
            enrichSpotsPhotosInBackground(savedSpots + feedSpots)
        }
    }

    /**
     * On-demand photo refresh for a single spot (e.g. from the SpotDetailBottomSheet).
     */
    suspend fun refreshSpotPhoto(spotId: String): Boolean = withContext(Dispatchers.IO) {
        val spot = scoutDao.getSpotById(spotId) ?: return@withContext false
        try {
            val resolved = com.example.util.LocationPhotoResolver.resolveEnrichedPhoto(
                title = spot.title,
                category = spot.category,
                districtOrLocation = spot.locationName,
                latitude = spot.latitude,
                longitude = spot.longitude,
                spotId = spot.id
            )
            if (resolved.url.isNotBlank() && !com.example.util.LocationPhotoResolver.isGenericOrStockPhoto(resolved.url)) {
                scoutDao.updateImageUrl(spot.id, resolved.url)
                return@withContext true
            }
        } catch (e: Exception) {
            android.util.Log.w("ScoutGemRepository", "Failed on-demand photo refresh for $spotId: ${e.message}")
        }
        false
    }
}

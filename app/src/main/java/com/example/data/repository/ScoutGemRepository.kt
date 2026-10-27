package com.example.data.repository

import com.example.data.local.DiscoveredGemDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutGem
import com.example.data.model.toScoutGem
import com.example.data.network.OverpassRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-First Repository for ScoutGem locations.
 * Room is the single source of truth; live Overpass API web scraper results
 * are inserted into Room in the background so the app remains 100% functional offline.
 */
class ScoutGemRepository(
    private val scoutDao: ScoutDao,
    private val discoveredGemDao: DiscoveredGemDao? = null,
    private val overpassClient: OverpassRetrofitClient = OverpassRetrofitClient()
) {

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
     * Performs up to maxAttempts (default 3) starting at initialDelayMs (default 1000ms),
     * doubling delay per failed attempt.
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
                    android.util.Log.i("ScoutGemRepository", "Overpass sync succeeded on retry attempt #$attempt")
                }
                return lastResult
            }

            if (attempt < maxAttempts) {
                android.util.Log.w(
                    "ScoutGemRepository",
                    "Overpass sync attempt #$attempt failed. Retrying in ${currentDelay}ms... Error: ${lastResult.exceptionOrNull()?.message}"
                )
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }

        android.util.Log.e(
            "ScoutGemRepository",
            "All $maxAttempts attempts failed for Overpass sync: ${lastResult.exceptionOrNull()?.message}"
        )
        return lastResult
    }

    /**
     * Asynchronously scrapes live OpenStreetMap viewpoints, bakeries, shortcuts & historic ruins
     * around the user's GPS coordinates via Overpass API and inserts them into Room with exponential backoff.
     */
    suspend fun syncLiveOsmGems(
        userLat: Double = 38.7112,
        userLng: Double = -9.1423,
        radiusMeters: Int = 1200
    ): Result<Int> = withContext(Dispatchers.IO) {
        retryWithExponentialBackoff(maxAttempts = 3, initialDelayMs = 1000L, factor = 2.0) {
            val fetchResult = overpassClient.fetchNearbyNodes(userLat, userLng, radiusMeters)
            fetchResult.fold(
                onSuccess = { osmEntities ->
                    if (osmEntities.isNotEmpty()) {
                        scoutDao.insertSpots(osmEntities)
                    }
                    Result.success(osmEntities.size)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
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
}

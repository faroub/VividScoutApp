package com.example.data.repository

import com.example.data.local.ScoutDao
import com.example.data.location.DefaultLocationConfig
import com.example.util.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Local-cache maintenance extracted from [ScoutGemRepository]: periodic eviction
 * of stale transient spots and on-demand purge of the transient cache.
 */
internal class GemCacheMaintenance(
    private val scoutDao: ScoutDao
) {

    private val _lastCleanupReport = MutableStateFlow<AutoCleanupReport?>(null)
    val lastCleanupReport: StateFlow<AutoCleanupReport?> = _lastCleanupReport.asStateFlow()

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
        SafeLog.i(
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
        SafeLog.i("ScoutGemRepository", "Manual cache clear purged $purged transient spots from Room DB.")
        purged
    }
}

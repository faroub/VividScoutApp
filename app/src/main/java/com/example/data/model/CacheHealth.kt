package com.example.data.model

import com.example.data.local.ScoutSpotEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Health tiers representing the density and availability of locally cached Room data
 * in the user's immediate vicinity vs what needs to be fetched from Overpass.
 */
enum class CacheHealthLevel(
    val title: String,
    val badgeLabel: String,
    val colorHex: Long,
    val summary: String
) {
    OPTIMAL(
        title = "Optimal Vicinity Cache",
        badgeLabel = "Optimal",
        colorHex = 0xFF10B981, // Vibrant Green
        summary = "Dense offline coverage. Complete walking guide available without internet."
    ),
    GOOD(
        title = "Good Offline Coverage",
        badgeLabel = "Good",
        colorHex = 0xFF2DD4BF, // Vibrant Teal
        summary = "Solid offline spot density in immediate walking radius."
    ),
    FAIR(
        title = "Sparse Vicinity Cache",
        badgeLabel = "Sparse",
        colorHex = 0xFFF59E0B, // Vibrant Amber
        summary = "Limited local spots nearby. Pre-caching recommended before wandering offline."
    ),
    CRITICAL(
        title = "Needs Overpass Fetch",
        badgeLabel = "Needs Fetch",
        colorHex = 0xFFFF5A5F, // Vibrant Coral / Red
        summary = "Uncached vicinity. Fetch OpenStreetMap data to explore offline."
    )
}

/**
 * Real-time Cache Health metrics for the user's immediate walking vicinity.
 */
data class VicinityCacheHealth(
    val scorePercent: Int,
    val cachedCount: Int,
    val targetCount: Int,
    val neededToFetch: Int,
    val level: CacheHealthLevel,
    val radiusMeters: Int = 1000,
    val lastSyncedCount: Int = 0,
    val isFetching: Boolean = false,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
) {
    val isOptimal: Boolean get() = level == CacheHealthLevel.OPTIMAL
    val shortStatus: String get() = "$scorePercent% Cache"
    val detailStatus: String get() = "$cachedCount cached • $neededToFetch needed (${radiusMeters}m)"
}

object CacheHealthCalculator {
    const val DEFAULT_VICINITY_RADIUS_METERS = 1000 // 1 km walking radius
    const val TARGET_VICINITY_DENSITY = 12 // 12 local spots in 1km represents optimal exploration density

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun calculate(
        userLat: Double,
        userLng: Double,
        spots: List<ScoutSpotEntity>,
        radiusMeters: Int = DEFAULT_VICINITY_RADIUS_METERS,
        targetCount: Int = TARGET_VICINITY_DENSITY,
        lastSyncedCount: Int = 0,
        isFetching: Boolean = false
    ): VicinityCacheHealth {
        val cachedInRadius = spots.count { spot ->
            val dist = calculateDistanceMeters(userLat, userLng, spot.latitude, spot.longitude)
            dist <= radiusMeters
        }

        val rawPercent = if (targetCount > 0) {
            ((cachedInRadius.toFloat() / targetCount.toFloat()) * 100).toInt()
        } else {
            100
        }
        val scorePercent = rawPercent.coerceIn(0, 100)
        val needed = (targetCount - cachedInRadius).coerceAtLeast(0)

        val level = when {
            scorePercent >= 85 -> CacheHealthLevel.OPTIMAL
            scorePercent >= 55 -> CacheHealthLevel.GOOD
            scorePercent >= 25 -> CacheHealthLevel.FAIR
            else -> CacheHealthLevel.CRITICAL
        }

        return VicinityCacheHealth(
            scorePercent = scorePercent,
            cachedCount = cachedInRadius,
            targetCount = targetCount,
            neededToFetch = needed,
            level = level,
            radiusMeters = radiusMeters,
            lastSyncedCount = lastSyncedCount,
            isFetching = isFetching
        )
    }
}

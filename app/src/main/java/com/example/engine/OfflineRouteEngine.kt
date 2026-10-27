package com.example.engine

import androidx.annotation.Keep
import com.example.data.local.ScoutSpotEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Keep
data class OfflineWalkingTrail(
    val waypoints: List<ScoutSpotEntity>,
    val pathPoints: List<Pair<Double, Double>>,
    val totalDistanceMeters: Double,
    val estimatedWalkingMinutes: Int
) {
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
        } else {
            "${totalDistanceMeters.roundToInt()} m"
        }

    val formattedDuration: String
        get() = if (estimatedWalkingMinutes >= 60) {
            val hrs = estimatedWalkingMinutes / 60
            val mins = estimatedWalkingMinutes % 60
            "${hrs}h ${mins}m"
        } else {
            "$estimatedWalkingMinutes mins"
        }
}

object OfflineRouteEngine {
    // Average walking speed: 4.8 km/h -> ~80 meters per minute
    private const val WALKING_SPEED_METERS_PER_MIN = 80.0

    /**
     * Calculates distance in meters between two GPS points using Haversine formula.
     * Pure Kotlin math - zero framework dependencies.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Calculates an offline walking route connecting spots starting from startLat/startLng.
     * Uses Nearest Neighbor TSP heuristic to order points logically.
     */
    fun calculateTrail(
        startLat: Double,
        startLng: Double,
        spots: List<ScoutSpotEntity>
    ): OfflineWalkingTrail? {
        if (spots.isEmpty()) return null

        val unvisited = spots.toMutableList()
        val orderedWaypoints = mutableListOf<ScoutSpotEntity>()
        var currentLat = startLat
        var currentLng = startLng

        while (unvisited.isNotEmpty()) {
            val nextSpot = unvisited.minByOrNull { spot ->
                haversineMeters(currentLat, currentLng, spot.latitude, spot.longitude)
            } ?: break

            orderedWaypoints.add(nextSpot)
            unvisited.remove(nextSpot)
            currentLat = nextSpot.latitude
            currentLng = nextSpot.longitude
        }

        val pathPoints = mutableListOf<Pair<Double, Double>>()
        pathPoints.add(Pair(startLat, startLng))

        var totalDist = 0.0
        var prevLat = startLat
        var prevLng = startLng

        for (spot in orderedWaypoints) {
            val dist = haversineMeters(prevLat, prevLng, spot.latitude, spot.longitude)
            totalDist += dist

            // Interpolate 3 intermediate sub-points between waypoints for smooth line rendering
            val steps = 3
            for (i in 1..steps) {
                val fraction = i.toDouble() / steps
                val interpLat = prevLat + (spot.latitude - prevLat) * fraction
                val interpLng = prevLng + (spot.longitude - prevLng) * fraction
                pathPoints.add(Pair(interpLat, interpLng))
            }

            prevLat = spot.latitude
            prevLng = spot.longitude
        }

        val minutes = (totalDist / WALKING_SPEED_METERS_PER_MIN).roundToInt().coerceAtLeast(1)

        return OfflineWalkingTrail(
            waypoints = orderedWaypoints,
            pathPoints = pathPoints,
            totalDistanceMeters = totalDist,
            estimatedWalkingMinutes = minutes
        )
    }
}

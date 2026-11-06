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
    val estimatedWalkingMinutes: Int,
    val steps: List<RouteStep> = emptyList(),
    val originTitle: String = "Starting Point",
    val destinationTitle: String = "Destination",
    val elevationProfile: RouteElevationProfile? = null
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
        spots: List<ScoutSpotEntity>,
        elevationModel: ElevationModel = NullElevationModel
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
        val elevationProfile = elevationModel.buildElevationProfile(pathPoints)

        return OfflineWalkingTrail(
            waypoints = orderedWaypoints,
            pathPoints = pathPoints,
            totalDistanceMeters = totalDist,
            estimatedWalkingMinutes = minutes,
            originTitle = "My Location",
            destinationTitle = orderedWaypoints.lastOrNull()?.name ?: "Saved Gems Trail",
            elevationProfile = elevationProfile
        )
    }

    /**
     * Calculates the shortest A* walking route between two cached locations or GPS positions,
     * snapped to the offline walking graph.
     *
     * The graph is hand-authored: real landmark positions connected by straight-line
     * edges, with optional edges injected from offline region packs (currently
     * synthesized locally rather than retrieved from a real street-data source).
     * Distances, geometry, and the pedestrian/stairs flags behind turn-by-turn steps
     * are graph approximations, not verified street-level pedestrian data — present
     * them as estimates.
     */
    fun calculateShortestAStarRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        originName: String = "Starting Point",
        destName: String = "Destination",
        originEntity: ScoutSpotEntity? = null,
        destEntity: ScoutSpotEntity? = null,
        elevationModel: ElevationModel = NullElevationModel
    ): OfflineWalkingTrail {
        val result = AStarPathfinder.findShortestPath(
            startLat = originLat,
            startLng = originLng,
            destLat = destLat,
            destLng = destLng,
            startName = originName,
            destName = destName
        )

        val waypoints = mutableListOf<ScoutSpotEntity>()
        originEntity?.let { waypoints.add(it) }
        destEntity?.let { waypoints.add(it) }

        val elevationProfile = elevationModel.buildElevationProfile(result.pathPoints)

        return OfflineWalkingTrail(
            waypoints = waypoints,
            pathPoints = result.pathPoints,
            totalDistanceMeters = result.totalDistanceMeters,
            estimatedWalkingMinutes = result.estimatedWalkingMinutes,
            steps = result.steps,
            originTitle = result.originName,
            destinationTitle = result.destinationName,
            elevationProfile = elevationProfile
        )
    }

    /**
     * Validates whether a candidate point falls within a maximum lateral deviation distance
     * of any segment in the offline walking trail corridor.
     */
    fun isPointWithinRouteCorridor(
        pointLat: Double,
        pointLng: Double,
        trail: OfflineWalkingTrail,
        maxDeviationMeters: Double = 500.0
    ): Boolean {
        if (trail.pathPoints.isEmpty()) return false
        if (pointLat !in -90.0..90.0 || pointLng !in -180.0..180.0 || pointLat.isNaN() || pointLng.isNaN()) {
            return false
        }
        if (kotlin.math.abs(pointLat) < 0.0001 && kotlin.math.abs(pointLng) < 0.0001) {
            return false
        }

        // Fast check against each waypoint first
        for (wp in trail.waypoints) {
            if (haversineMeters(wp.latitude, wp.longitude, pointLat, pointLng) <= maxDeviationMeters) {
                return true
            }
        }

        // Check against segments in trail.pathPoints
        for (i in 0 until trail.pathPoints.size - 1) {
            val (lat1, lng1) = trail.pathPoints[i]
            val (lat2, lng2) = trail.pathPoints[i + 1]

            val d13 = haversineMeters(lat1, lng1, pointLat, pointLng)
            val d23 = haversineMeters(lat2, lng2, pointLat, pointLng)
            if (d13 <= maxDeviationMeters || d23 <= maxDeviationMeters) {
                return true
            }

            val d12 = haversineMeters(lat1, lng1, lat2, lng2)
            if (d12 > 1.0) {
                val crossTrack = com.example.util.MovementVectorUtils.calculateCrossTrackDistanceMeters(
                    pointLat = pointLat,
                    pointLng = pointLng,
                    startLat = lat1,
                    startLng = lng1,
                    endLat = lat2,
                    endLng = lng2
                )
                if (crossTrack <= maxDeviationMeters) {
                    // Check that the projection falls on the segment between (lat1, lng1) and (lat2, lng2)
                    if (d13 * d13 <= d12 * d12 + crossTrack * crossTrack &&
                        d23 * d23 <= d12 * d12 + crossTrack * crossTrack) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

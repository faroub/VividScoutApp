package com.example.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class WaypointNavigationState(
    val waypointTitle: String,
    val distanceMeters: Double,
    val bearingDegrees: Float,
    val isArrived: Boolean
)

/**
 * Navigation engine for 2D map-based walking guidance, computing target bearing angles,
 * distances, and enforcing the 20-meter waypoint arrival threshold.
 */
object MapWalkingEngine {

    const val WAYPOINT_ARRIVAL_RADIUS_METERS = 20.0

    /**
     * Calculates initial bearing in degrees (0..360) from start coordinates to target waypoint.
     */
    fun calculateBearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        if (lat1 == lat2 && lon1 == lon2) return 0f

        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)

        var bearing = Math.toDegrees(atan2(y, x)).toFloat()
        if (bearing < 0f) {
            bearing += 360f
        }
        return bearing
    }

    /**
     * Checks if user has arrived within 20 meters of the target waypoint.
     */
    fun isWaypointReached(distanceMeters: Double): Boolean {
        if (distanceMeters.isNaN() || distanceMeters < 0) return false
        return distanceMeters <= WAYPOINT_ARRIVAL_RADIUS_METERS
    }

    /**
     * Evaluates current navigation state for a target waypoint.
     */
    fun evaluateWaypointNavigation(
        userLat: Double,
        userLng: Double,
        waypointLat: Double,
        waypointLng: Double,
        waypointTitle: String
    ): WaypointNavigationState {
        val distance = Geo.calculateDistanceMeters(userLat, userLng, waypointLat, waypointLng)
        val bearing = calculateBearingDegrees(userLat, userLng, waypointLat, waypointLng)
        val arrived = isWaypointReached(distance)

        return WaypointNavigationState(
            waypointTitle = waypointTitle,
            distanceMeters = distance,
            bearingDegrees = bearing,
            isArrived = arrived
        )
    }
}

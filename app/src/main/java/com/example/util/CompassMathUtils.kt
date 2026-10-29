package com.example.util

import com.example.data.local.ScoutSpotEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CompassMathUtils {

    /**
     * Calculates initial bearing (heading) in degrees (0..360) from point 1 to point 2 using spherical trigonometry.
     */
    fun calculateBearing(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLng)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad).toFloat()
        return (bearingDeg + 360f) % 360f
    }

    /**
     * Calculates approximate straight-line distance in meters between two lat/lng points using Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    /**
     * Finds the nearest saved (or discovered) gem relative to user GPS location.
     */
    fun findNearestSavedGem(
        userLat: Double,
        userLng: Double,
        spots: List<ScoutSpotEntity>
    ): ScoutSpotEntity? {
        val savedOrDiscovered = spots.filter { it.isSaved || it.isDiscovered }
        val candidatePool = if (savedOrDiscovered.isNotEmpty()) savedOrDiscovered else spots
        if (candidatePool.isEmpty()) return null

        return candidatePool.minByOrNull { spot ->
            calculateDistanceMeters(userLat, userLng, spot.latitude, spot.longitude)
        }
    }

    /**
     * Calculates relative needle rotation angle for the target gem relative to current compass azimuth.
     */
    fun calculatePointerAngle(compassAzimuth: Float, targetBearing: Float): Float {
        return (targetBearing - compassAzimuth + 360f) % 360f
    }

    /**
     * Converts heading degrees (0..360) into standard cardinal/intercardinal direction label.
     */
    fun formatCardinalDirection(degrees: Float): String {
        val normalized = (degrees + 360f) % 360f
        return when (normalized) {
            in 337.5..360.0, in 0.0..<22.5 -> "N"
            in 22.5..<67.5 -> "NE"
            in 67.5..<112.5 -> "E"
            in 112.5..<157.5 -> "SE"
            in 157.5..<202.5 -> "S"
            in 202.5..<247.5 -> "SW"
            in 247.5..<292.5 -> "W"
            in 292.5..<337.5 -> "NW"
            else -> "N"
        }
    }

    /**
     * Formats distance in meters or kilometers for UI banners.
     */
    fun formatDistance(meters: Float): String {
        return if (meters >= 1000f) {
            String.format(java.util.Locale.US, "%.1f km", meters / 1000f)
        } else {
            "${meters.toInt()} m"
        }
    }
}

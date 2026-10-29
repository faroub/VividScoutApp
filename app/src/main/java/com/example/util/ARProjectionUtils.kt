package com.example.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Direction indicator for AR pins outside the immediate camera field of view.
 */
enum class AROffscreenDirection {
    NONE, LEFT, RIGHT
}

/**
 * Screen position and orientation data for an AR spatial marker.
 */
data class ARProjectionPoint(
    val xPx: Float,
    val yPx: Float,
    val bearing: Float,
    val deltaAngle: Float,
    val distanceMeters: Float,
    val isInsideFov: Boolean,
    val offscreenDirection: AROffscreenDirection
)

/**
 * Utility for projecting GPS coordinates onto camera screen space using device heading and pitch.
 */
object ARProjectionUtils {

    const val DEFAULT_HORIZONTAL_FOV_DEGREES = 60.0f
    const val DEFAULT_VERTICAL_FOV_DEGREES = 45.0f

    /**
     * Calculates the initial true compass bearing in degrees (0..360) from start GPS to target GPS.
     */
    fun calculateBearing(
        userLat: Double,
        userLng: Double,
        targetLat: Double,
        targetLng: Double
    ): Float {
        val lat1 = Math.toRadians(userLat)
        val lat2 = Math.toRadians(targetLat)
        val deltaLng = Math.toRadians(targetLng - userLng)

        val y = sin(deltaLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)

        val initialBearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (initialBearing + 360f) % 360f
    }

    /**
     * Normalizes an angle difference to the range [-180, +180] degrees.
     */
    fun normalizeAngleDelta(angle: Float): Float {
        var delta = angle % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /**
     * Projects GPS target coordinates to screen pixel coordinates (X, Y) based on current device heading (azimuth)
     * and tilt (pitch).
     */
    fun projectGpsToScreen(
        userLat: Double,
        userLng: Double,
        targetLat: Double,
        targetLng: Double,
        deviceAzimuth: Float,
        devicePitch: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        horizontalFovDegrees: Float = DEFAULT_HORIZONTAL_FOV_DEGREES,
        verticalFovDegrees: Float = DEFAULT_VERTICAL_FOV_DEGREES
    ): ARProjectionPoint {
        val distanceMeters = com.example.data.location.LocationTracker.distanceBetween(
            userLat, userLng, targetLat, targetLng
        )

        val bearing = calculateBearing(userLat, userLng, targetLat, targetLng)
        val deltaAngle = normalizeAngleDelta(bearing - deviceAzimuth)

        val halfHov = horizontalFovDegrees / 2.0f
        val halfVov = verticalFovDegrees / 2.0f

        val isInsideFov = deltaAngle in -halfHov..halfHov

        val offscreenDir = when {
            deltaAngle < -halfHov -> AROffscreenDirection.LEFT
            deltaAngle > halfHov -> AROffscreenDirection.RIGHT
            else -> AROffscreenDirection.NONE
        }

        // Horizontal screen projection
        val normX = deltaAngle / halfHov
        val xPx = (screenWidthPx / 2.0f) + (normX * (screenWidthPx / 2.0f))

        // Vertical screen projection (considering device pitch angle)
        // Pitch 0 is horizon, positive is tilting up
        val clampedPitch = devicePitch.coerceIn(-60f, 60f)
        val normY = clampedPitch / halfVov
        val yPx = (screenHeightPx / 2.0f) + (normY * (screenHeightPx / 3.0f))

        return ARProjectionPoint(
            xPx = xPx.coerceIn(40f, screenWidthPx - 40f),
            yPx = yPx.coerceIn(120f, screenHeightPx - 160f),
            bearing = bearing,
            deltaAngle = deltaAngle,
            distanceMeters = distanceMeters,
            isInsideFov = isInsideFov,
            offscreenDirection = offscreenDir
        )
    }
}

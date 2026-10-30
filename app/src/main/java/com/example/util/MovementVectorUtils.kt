package com.example.util

import com.example.data.offline.GeoBoundingBox
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Represents a single breadcrumb coordinate in the user's recent movement history.
 */
data class LocationBreadcrumb(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float = 0f,
    val bearingDegrees: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Calculated 5km predicted movement trajectory.
 */
data class MovementVector(
    val originLat: Double,
    val originLng: Double,
    val targetLat: Double,
    val targetLng: Double,
    val bearingDegrees: Float,
    val speedMps: Float,
    val distanceMeters: Double = 5000.0,
    val corridorBoundingBox: GeoBoundingBox,
    val calculatedAt: Long = System.currentTimeMillis()
) {
    val cardinalDirection: String
        get() = when (((bearingDegrees + 22.5f) % 360f / 45f).toInt()) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SW"
            6 -> "W"
            7 -> "NW"
            else -> "N"
        }
}

/**
 * Mathematical utilities for calculating user velocity, bearing vectors, and
 * predicting forward geographic coordinates 5km along the user's travel path.
 */
object MovementVectorUtils {

    const val EARTH_RADIUS_METERS = 6371000.0
    const val DEFAULT_PREFETCH_DISTANCE_METERS = 5000.0 // 5km
    const val CORRIDOR_BUFFER_DEGREES = 0.015 // ~1.5km lateral corridor buffer

    /**
     * Calculates the initial bearing from start coordinate to end coordinate in degrees [0, 360).
     */
    fun calculateBearing(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)

        val initialBearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (initialBearing + 360f) % 360f
    }

    /**
     * Extrapolates a destination coordinate given a starting coordinate, bearing, and distance in meters
     * using spherical earth geodesy.
     */
    fun extrapolateTargetCoordinate(
        startLat: Double,
        startLng: Double,
        bearingDegrees: Float,
        distanceMeters: Double = DEFAULT_PREFETCH_DISTANCE_METERS
    ): Pair<Double, Double> {
        val delta = distanceMeters / EARTH_RADIUS_METERS
        val theta = Math.toRadians(bearingDegrees.toDouble())
        val phi1 = Math.toRadians(startLat)
        val lambda1 = Math.toRadians(startLng)

        val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
        val phi2 = asin(sinPhi2)

        val y = sin(theta) * sin(delta) * cos(phi1)
        val x = cos(delta) - sin(phi1) * sin(phi2)
        val lambda2 = lambda1 + atan2(y, x)

        val targetLat = Math.toDegrees(phi2)
        val targetLng = ((Math.toDegrees(lambda2) + 540.0) % 360.0) - 180.0

        return Pair(targetLat, targetLng)
    }

    /**
     * Constructs a bounding box corridor encompassing the path from origin to extrapolated target,
     * including a lateral safety margin for POI retrieval.
     */
    fun calculateCorridorBoundingBox(
        startLat: Double,
        startLng: Double,
        targetLat: Double,
        targetLng: Double,
        bufferDegrees: Double = CORRIDOR_BUFFER_DEGREES
    ): GeoBoundingBox {
        val minLat = minOf(startLat, targetLat) - bufferDegrees
        val maxLat = maxOf(startLat, targetLat) + bufferDegrees
        val minLng = minOf(startLng, targetLng) - bufferDegrees
        val maxLng = maxOf(startLng, targetLng) + bufferDegrees

        return GeoBoundingBox(
            minLat = minLat,
            maxLat = maxLat,
            minLng = minLng,
            maxLng = maxLng
        )
    }

    /**
     * Evaluates a rolling list of recent GPS breadcrumbs to calculate the user's current
     * movement vector, extrapolating 5km ahead.
     */
    fun calculateMovementVector(
        breadcrumbs: List<LocationBreadcrumb>,
        fallbackBearing: Float = 45f, // Default heading Northeast toward Tagus river / Alfama
        distanceMeters: Double = DEFAULT_PREFETCH_DISTANCE_METERS
    ): MovementVector {
        if (breadcrumbs.isEmpty()) {
            val defaultOriginLat = 38.7112
            val defaultOriginLng = -9.1423
            val (targetLat, targetLng) = extrapolateTargetCoordinate(
                defaultOriginLat,
                defaultOriginLng,
                fallbackBearing,
                distanceMeters
            )
            return MovementVector(
                originLat = defaultOriginLat,
                originLng = defaultOriginLng,
                targetLat = targetLat,
                targetLng = targetLng,
                bearingDegrees = fallbackBearing,
                speedMps = 1.4f, // Standard walking speed 5km/h ~ 1.4 m/s
                distanceMeters = distanceMeters,
                corridorBoundingBox = calculateCorridorBoundingBox(
                    defaultOriginLat, defaultOriginLng, targetLat, targetLng
                )
            )
        }

        val latest = breadcrumbs.last()
        val latestBearing = latest.bearingDegrees

        val computedBearing = if (latestBearing != null && latestBearing > 0f) {
            latestBearing
        } else if (breadcrumbs.size >= 2) {
            val previous = breadcrumbs[breadcrumbs.size - 2]
            val dist = com.example.data.location.LocationTracker.distanceBetween(
                previous.latitude, previous.longitude, latest.latitude, latest.longitude
            )
            if (dist > 3.0f) {
                calculateBearing(previous.latitude, previous.longitude, latest.latitude, latest.longitude)
            } else {
                fallbackBearing
            }
        } else {
            fallbackBearing
        }

        val effectiveSpeed = if (latest.speedMps > 0.1f) latest.speedMps else 1.4f

        val (targetLat, targetLng) = extrapolateTargetCoordinate(
            latest.latitude,
            latest.longitude,
            computedBearing,
            distanceMeters
        )

        return MovementVector(
            originLat = latest.latitude,
            originLng = latest.longitude,
            targetLat = targetLat,
            targetLng = targetLng,
            bearingDegrees = computedBearing,
            speedMps = effectiveSpeed,
            distanceMeters = distanceMeters,
            corridorBoundingBox = calculateCorridorBoundingBox(
                latest.latitude, latest.longitude, targetLat, targetLng
            )
        )
    }
}

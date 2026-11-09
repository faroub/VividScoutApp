package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geodesic calculation and locale-aware distance / walk time formatting utilities.
 */
object Geo {

    const val AVERAGE_WALKING_SPEED_MPS = 1.3 // 1.3 meters per second (~4.68 km/h)

    /**
     * Rounds a coordinate to 3 decimal places (~110 m at the equator) before it is
     * embedded in a prompt sent to an external model. Three decimals keep the location
     * useful for nearby-place discovery while avoiding transmitting a precise GPS fix.
     * Exact, unrounded coordinates remain in use everywhere else: distance math, Room
     * persistence, and logs.
     */
    fun roundForPrompt(value: Double): Double =
        BigDecimal(value).setScale(3, RoundingMode.HALF_UP).toDouble()

    /**
     * A third-party endpoint must never observe the user's exact GPS fix. The query
     * center is therefore rounded DOWN to a coarse grid before it leaves the device.
     *
     * Both coordinates floor to the [gridStep] grid (0.01° ≈ 1.1 km of latitude), so the
     * endpoint learns only the grid cell the user is in. Because the rounded center can
     * sit up to one cell away from the true position in each axis, the radius grows by
     * the worst-case center shift (~1.6 km at 0.01°) — the queried area always fully
     * contains the requested area, so no nearby result is lost.
     *
     * Trade-off: the queried circle is larger than requested (radius +~1.6 km), so the
     * response may contain a few extra results slightly outside the true radius; the
     * local distance ranking and the 50 km safety filter already handle those.
     */
    data class OutwardQueryArea(
        val lat: Double,
        val lng: Double,
        val radiusMeters: Int
    )

    fun roundOutwardForEgress(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        gridStep: Double = 0.01
    ): OutwardQueryArea {
        if (!lat.isFinite() || !lng.isFinite() || radiusMeters <= 0) {
            return OutwardQueryArea(lat, lng, radiusMeters)
        }
        val roundedLat = Math.floor(lat / gridStep) * gridStep
        val roundedLng = Math.floor(lng / gridStep) * gridStep
        // Worst-case shift of the rounded center: up to gridStep in each axis, i.e.
        // gridStep * 111_320 * sqrt(2) meters (~1.6 km at 0.01°).
        val worstCaseShiftMeters = Math.ceil(gridStep * 111_320.0 * Math.sqrt(2.0)).toInt()
        return OutwardQueryArea(roundedLat, roundedLng, radiusMeters + worstCaseShiftMeters)
    }

    /**
     * Calculates straight-line geodesic distance in meters between two lat/lng points using the Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN()) return Double.NaN
        if (lat1 == 0.0 && lon1 == 0.0) return Double.NaN
        if (lat2 == 0.0 && lon2 == 0.0) return Double.NaN

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
     * Calculates estimated walking time in minutes based on 1.3 m/s walking speed.
     */
    fun calculateWalkMinutes(distanceMeters: Double): Int {
        if (distanceMeters.isNaN() || distanceMeters < 0) return 0
        return (distanceMeters / AVERAGE_WALKING_SPEED_MPS / 60.0).roundToInt()
    }

    /**
     * Formats geodesic distance into locale-aware units (Metric: m / km, Imperial: yds / mi).
     * Returns "—" when location is unavailable (NaN).
     */
    fun formatDistance(
        distanceMeters: Double,
        locale: Locale = Locale.getDefault()
    ): String {
        if (distanceMeters.isNaN() || distanceMeters < 0) return "—"

        val isImperial = when (locale.country) {
            "US", "LR", "MM" -> true
            else -> false
        }

        return if (isImperial) {
            val yards = distanceMeters * 1.09361
            if (yards < 1760.0) {
                "${yards.roundToInt()} yds"
            } else {
                val miles = yards / 1760.0
                String.format(locale, "%.1f mi", miles)
            }
        } else {
            if (distanceMeters < 1000.0) {
                "${distanceMeters.roundToInt()} m"
            } else {
                val km = distanceMeters / 1000.0
                String.format(locale, "%.1f km", km)
            }
        }
    }

    /**
     * Formats distance and walk minutes into a unified text label (e.g. "350 m • 4 min walk" or "—").
     */
    fun formatDistanceAndWalkTime(
        userLat: Double,
        userLng: Double,
        spotLat: Double,
        spotLng: Double,
        locale: Locale = Locale.getDefault()
    ): String {
        val distMeters = calculateDistanceMeters(userLat, userLng, spotLat, spotLng)
        if (distMeters.isNaN()) return "—"

        val distText = formatDistance(distMeters, locale)
        val walkMins = calculateWalkMinutes(distMeters)

        return if (walkMins > 0) {
            "$distText • $walkMins min walk"
        } else {
            distText
        }
    }
}

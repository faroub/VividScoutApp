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

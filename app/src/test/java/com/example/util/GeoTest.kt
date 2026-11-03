package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class GeoTest {

    @Test
    fun calculateDistanceMeters_knownCoordinates_withinOnePercentTolerance() {
        // Point A: Lisbon Cathedral (38.7098° N, 9.1326° W)
        // Point B: Rossio Square (38.7139° N, 9.1394° W)
        // Known geodesic distance: ~754.2 meters
        val lat1 = 38.7098
        val lon1 = -9.1326
        val lat2 = 38.7139
        val lon2 = -9.1394

        val expectedMeters = 754.2
        val actualMeters = Geo.calculateDistanceMeters(lat1, lon1, lat2, lon2)

        val onePercentTolerance = expectedMeters * 0.01 // 1% tolerance
        assertEquals(expectedMeters, actualMeters, onePercentTolerance)
    }

    @Test
    fun calculateWalkMinutes_usesOnePointThreeMpsSpeed_roundsToNearestMinute() {
        // 780 meters / 1.3 m/s = 600 seconds = 10 minutes
        assertEquals(10, Geo.calculateWalkMinutes(780.0))

        // 150 meters / 1.3 m/s = 115.38 seconds = 2 minutes (1.92 min)
        assertEquals(2, Geo.calculateWalkMinutes(150.0))
    }

    @Test
    fun formatDistance_metricLocale_formatsMetersAndKilometers() {
        val frLocale = Locale.FRANCE

        assertEquals("450 m", Geo.formatDistance(450.0, frLocale))
        assertEquals("2.5 km", Geo.formatDistance(2500.0, frLocale))
    }

    @Test
    fun formatDistance_imperialLocale_formatsYardsAndMiles() {
        val usLocale = Locale.US

        assertEquals("492 yds", Geo.formatDistance(450.0, usLocale))
        assertEquals("2.0 mi", Geo.formatDistance(3218.68, usLocale)) // 2 miles = 3218.68m
    }

    @Test
    fun formatDistance_unavailableLocation_returnsDashPlaceholder() {
        assertEquals("—", Geo.formatDistance(Double.NaN, Locale.US))
        assertEquals("—", Geo.formatDistance(-1.0, Locale.FRANCE))

        assertEquals("—", Geo.formatDistanceAndWalkTime(0.0, 0.0, 38.7112, -9.1423, Locale.US))
        assertEquals("—", Geo.formatDistanceAndWalkTime(Double.NaN, -9.14, 38.7112, -9.1423, Locale.US))
    }
}

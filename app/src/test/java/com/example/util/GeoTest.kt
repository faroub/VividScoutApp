package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GeoTest {

    @Test
    fun calculateDistanceMeters_knownCoordinates_withinOnePercentTolerance() {
        // Point A: Lisbon Cathedral (38.7098° N, 9.1326° W)
        // Point B: Rossio Square (38.7139° N, 9.1394° W)
        // Expected geodesic distance: 745.62 meters
        val lat1 = 38.7098
        val lon1 = -9.1326
        val lat2 = 38.7139
        val lon2 = -9.1394

        val expectedMeters = 745.62
        val actualMeters = Geo.calculateDistanceMeters(lat1, lon1, lat2, lon2)

        val onePercentTolerance = expectedMeters * 0.01 // 1% tolerance (7.45m)
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
        val deLocale = Locale.GERMANY
        val frLocale = Locale.FRANCE

        assertEquals("450 m", Geo.formatDistance(450.0, deLocale))
        assertEquals("2,5 km", Geo.formatDistance(2500.0, frLocale))
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

    @Test
    fun roundForPrompt_positiveCoordinate_roundsToThreeDecimals() {
        // 3 decimals ≈ 110 m — the prompt reveals the neighborhood, not the exact GPS fix.
        assertEquals(38.711, Geo.roundForPrompt(38.711247), 0.0)
        assertEquals(38.712, Geo.roundForPrompt(38.71156), 0.0) // 4th decimal safely above the midpoint
        assertEquals(38.711, Geo.roundForPrompt(38.7112), 0.0)
    }

    @Test
    fun roundForPrompt_negativeCoordinate_roundsTowardNearest() {
        // Longitude -9.14234 must round to -9.142; -9.14251 rounds to -9.143 (HALF_UP).
        assertEquals(-9.142, Geo.roundForPrompt(-9.14234), 0.0)
        assertEquals(-9.143, Geo.roundForPrompt(-9.14251), 0.0)
        assertEquals(-0.123, Geo.roundForPrompt(-0.12349), 0.0)
    }

    @Test
    fun roundForPrompt_boundaryValues_stayValidCoordinates() {
        // Boundary meridians and the equator pass through unchanged in value class.
        assertEquals(90.0, Geo.roundForPrompt(90.0), 0.0)
        assertEquals(-90.0, Geo.roundForPrompt(-90.0), 0.0)
        assertEquals(180.0, Geo.roundForPrompt(179.9999), 0.0)
        assertEquals(-180.0, Geo.roundForPrompt(-180.0), 0.0)
        assertEquals(0.0, Geo.roundForPrompt(0.0), 0.0)
        assertEquals(0.001, Geo.roundForPrompt(0.0009), 0.0) // smallest representable rounded step
    }

    @Test
    fun roundOutwardForEgress_floorsCoordinatesToCoarseGridAndGrowsRadius() {
        val area = Geo.roundOutwardForEgress(38.7112, -9.1423, 1500)

        // 0.01° grid: the endpoint learns only the cell (38.71, -9.15), never the fix.
        assertEquals(38.71, area.lat, 1e-9)
        assertEquals(-9.15, area.lng, 1e-9)
        // Worst-case center shift at 0.01° = ceil(0.01 * 111320 * sqrt(2)) = 1575 m.
        assertEquals(1500 + 1575, area.radiusMeters)
    }

    @Test
    fun roundOutwardForEgress_roundedAreaCoversTheRequestedCircle() {
        val lat = 38.7119
        val lng = -9.1401
        val radius = 1200

        val area = Geo.roundOutwardForEgress(lat, lng, radius)

        // Every point within `radius` of the true fix stays inside the queried circle:
        // dist(roundedCenter, anyPoint) <= dist(center, point) + worstCaseShift.
        assertTrue(area.radiusMeters > radius)
        val worstCaseShift = Math.ceil(0.01 * 111_320.0 * Math.sqrt(2.0))
        assertTrue(
            "rounded center must stay within one grid cell of the true position",
            Geo.calculateDistanceMeters(lat, lng, area.lat, area.lng) <= worstCaseShift + 1.0
        )
    }

    @Test
    fun roundOutwardForEgress_southernAndEasternHemispheresFloorOutward() {
        // Sydney: -33.8688, 151.2093 -> floors to -33.87, 151.20 (SW grid corner).
        val area = Geo.roundOutwardForEgress(-33.8688, 151.2093, 800)
        assertEquals(-33.87, area.lat, 1e-9)
        assertEquals(151.20, area.lng, 1e-9)
        assertEquals(800 + 1575, area.radiusMeters)
    }

    @Test
    fun roundOutwardForEgress_invalidInputPassedThroughUntouched() {
        val nan = Geo.roundOutwardForEgress(Double.NaN, -9.1423, 1500)
        assertTrue(nan.lat.isNaN())
        assertEquals(-9.1423, nan.lng, 0.0)
        assertEquals(1500, nan.radiusMeters)

        val infinite = Geo.roundOutwardForEgress(38.7112, Double.POSITIVE_INFINITY, 1500)
        assertEquals(38.7112, infinite.lat, 0.0)
        assertTrue(infinite.lng.isInfinite())

        val noRadius = Geo.roundOutwardForEgress(38.7112, -9.1423, 0)
        assertEquals(38.7112, noRadius.lat, 0.0)
        assertEquals(-9.1423, noRadius.lng, 0.0)
        assertEquals(0, noRadius.radiusMeters)
    }

    @Test
    fun roundOutwardForEgress_neverRevealsMorePreciseThanTheGrid() {
        // Any coordinate inside one 0.01° cell maps to the same SW corner.
        val a = Geo.roundOutwardForEgress(38.7101, -9.1499, 500)
        val b = Geo.roundOutwardForEgress(38.7199, -9.1401, 500)
        assertEquals(a.lat, b.lat, 1e-9)
        assertEquals(a.lng, b.lng, 1e-9)
    }
}

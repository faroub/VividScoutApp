package com.example.util

import com.example.data.location.LocationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementVectorUtilsTest {

    @Test
    fun testCalculateBearingDueNorth() {
        // Due North: same longitude, higher latitude
        val bearing = MovementVectorUtils.calculateBearing(
            lat1 = 38.7100,
            lng1 = -9.1400,
            lat2 = 38.7200,
            lng2 = -9.1400
        )
        assertEquals(0f, bearing, 0.5f)
    }

    @Test
    fun testCalculateBearingDueEast() {
        // Due East: same latitude, higher longitude
        val bearing = MovementVectorUtils.calculateBearing(
            lat1 = 38.7100,
            lng1 = -9.1400,
            lat2 = 38.7100,
            lng2 = -9.1300
        )
        assertEquals(90f, bearing, 1.0f)
    }

    @Test
    fun testExtrapolateTargetCoordinate5km() {
        val startLat = 38.7112
        val startLng = -9.1423
        val heading = 45f // Northeast
        val distance = 5000.0 // 5km

        val (targetLat, targetLng) = MovementVectorUtils.extrapolateTargetCoordinate(
            startLat, startLng, heading, distance
        )

        // Target should be northeast of origin
        assertTrue("Target latitude should be greater than start latitude", targetLat > startLat)
        assertTrue("Target longitude should be greater than start longitude", targetLng > startLng)

        // Measured distance between start and target should be ~5000m
        val measuredDistance = LocationTracker.distanceBetween(startLat, startLng, targetLat, targetLng)
        assertEquals(5000f, measuredDistance, 100f)
    }

    @Test
    fun testCalculateMovementVectorFromBreadcrumbs() {
        val breadcrumbs = listOf(
            LocationBreadcrumb(38.7100, -9.1420, 1.3f, 50f, 1000L),
            LocationBreadcrumb(38.7105, -9.1415, 1.4f, 52f, 2000L),
            LocationBreadcrumb(38.7110, -9.1410, 1.5f, 55f, 3000L)
        )

        val vector = MovementVectorUtils.calculateMovementVector(breadcrumbs)

        assertEquals(38.7110, vector.originLat, 0.0001)
        assertEquals(-9.1410, vector.originLng, 0.0001)
        assertEquals(55f, vector.bearingDegrees, 1.0f)
        assertEquals(1.5f, vector.speedMps, 0.1f)
        assertEquals(5000.0, vector.distanceMeters, 1.0)
        assertEquals("NE", vector.cardinalDirection)

        // Check corridor bounding box encompasses both points
        val bbox = vector.corridorBoundingBox
        assertTrue(bbox.contains(vector.originLat, vector.originLng))
        assertTrue(bbox.contains(vector.targetLat, vector.targetLng))
    }

    @Test
    fun testCalculateMovementVectorFallback() {
        val vector = MovementVectorUtils.calculateMovementVector(emptyList(), fallbackBearing = 180f)

        assertNotNull(vector)
        assertEquals(180f, vector.bearingDegrees, 0.01f)
        assertEquals("S", vector.cardinalDirection)
        assertEquals(5000.0, vector.distanceMeters, 0.01)
        assertTrue(vector.targetLat < vector.originLat) // South heading moves target south
    }
}

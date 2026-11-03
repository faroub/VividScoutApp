package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapWalkingEngineTest {

    @Test
    fun calculateBearingDegrees_dueEastAndDueNorth_calculatesAccurateAngles() {
        val lat1 = 38.7112
        val lon1 = -9.1423

        // Point Due East (same latitude, higher longitude)
        val latEast = 38.7112
        val lonEast = -9.1323
        val bearingEast = MapWalkingEngine.calculateBearingDegrees(lat1, lon1, latEast, lonEast)
        assertEquals(90.0f, bearingEast, 1.0f) // ~90 degrees

        // Point Due North (higher latitude, same longitude)
        val latNorth = 38.7212
        val lonNorth = -9.1423
        val bearingNorth = MapWalkingEngine.calculateBearingDegrees(lat1, lon1, latNorth, lonNorth)
        assertEquals(0.0f, bearingNorth, 1.0f) // ~0/360 degrees
    }

    @Test
    fun isWaypointReached_twentyMeterArrivalCheck_evaluatesAccurately() {
        assertTrue("15 meters is within 20m arrival radius", MapWalkingEngine.isWaypointReached(15.0))
        assertTrue("20 meters exact boundary is within arrival radius", MapWalkingEngine.isWaypointReached(20.0))

        assertFalse("21 meters is outside 20m arrival radius", MapWalkingEngine.isWaypointReached(21.0))
        assertFalse("50 meters is outside 20m arrival radius", MapWalkingEngine.isWaypointReached(50.0))
        assertFalse("NaN distance is invalid", MapWalkingEngine.isWaypointReached(Double.NaN))
    }
}

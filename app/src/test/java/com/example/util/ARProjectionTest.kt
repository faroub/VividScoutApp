package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ARProjectionTest {

    @Test
    fun testNormalizeAngleDelta() {
        assertEquals(0f, ARProjectionUtils.normalizeAngleDelta(0f), 0.01f)
        assertEquals(90f, ARProjectionUtils.normalizeAngleDelta(90f), 0.01f)
        assertEquals(-90f, ARProjectionUtils.normalizeAngleDelta(270f), 0.01f)
        assertEquals(180f, ARProjectionUtils.normalizeAngleDelta(180f), 0.01f)
        assertEquals(-170f, ARProjectionUtils.normalizeAngleDelta(190f), 0.01f)
    }

    @Test
    fun testCalculateBearingNorth() {
        // Due north test: same longitude, higher latitude
        val bearing = ARProjectionUtils.calculateBearing(
            userLat = 38.7112,
            userLng = -9.1423,
            targetLat = 38.7212,
            targetLng = -9.1423
        )
        assertEquals(0f, bearing, 1.0f)
    }

    @Test
    fun testProjectGpsToScreenInsideFov() {
        // User looking due north (azimuth 0) at a spot due north
        val projection = ARProjectionUtils.projectGpsToScreen(
            userLat = 38.7112,
            userLng = -9.1423,
            targetLat = 38.7200,
            targetLng = -9.1423,
            deviceAzimuth = 0f,
            devicePitch = 0f,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f
        )

        assertTrue("Spot due north should be inside FOV when facing north", projection.isInsideFov)
        assertEquals("X coordinate should be centered horizontally", 540f, projection.xPx, 5.0f)
    }

    @Test
    fun testProjectGpsToScreenOffscreenLeft() {
        // User facing East (azimuth 90) looking at spot due North (bearing 0) -> spot is to the left (-90 deg)
        val projection = ARProjectionUtils.projectGpsToScreen(
            userLat = 38.7112,
            userLng = -9.1423,
            targetLat = 38.7200,
            targetLng = -9.1423,
            deviceAzimuth = 90f,
            devicePitch = 0f,
            screenWidthPx = 1080f,
            screenHeightPx = 1920f
        )

        assertFalse("Spot to the left should be outside FOV", projection.isInsideFov)
        assertEquals("Direction should be LEFT", AROffscreenDirection.LEFT, projection.offscreenDirection)
    }
}

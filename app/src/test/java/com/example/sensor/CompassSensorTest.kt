package com.example.sensor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ScoutSpotEntity
import com.example.util.CompassMathUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompassSensorTest {

    @Test
    fun testBearingCalculationCardinalDirections() {
        // Due North
        val northBearing = CompassMathUtils.calculateBearing(0.0, 0.0, 1.0, 0.0)
        assertEquals(0f, northBearing, 1.0f)

        // Due East
        val eastBearing = CompassMathUtils.calculateBearing(0.0, 0.0, 0.0, 1.0)
        assertEquals(90f, eastBearing, 1.0f)

        // Due South
        val southBearing = CompassMathUtils.calculateBearing(1.0, 0.0, 0.0, 0.0)
        assertEquals(180f, southBearing, 1.0f)

        // Due West
        val westBearing = CompassMathUtils.calculateBearing(0.0, 1.0, 0.0, 0.0)
        assertEquals(270f, westBearing, 1.0f)
    }

    @Test
    fun testCardinalDirectionLabelFormatting() {
        assertEquals("N", CompassMathUtils.formatCardinalDirection(0f))
        assertEquals("N", CompassMathUtils.formatCardinalDirection(355f))
        assertEquals("NE", CompassMathUtils.formatCardinalDirection(45f))
        assertEquals("E", CompassMathUtils.formatCardinalDirection(90f))
        assertEquals("SE", CompassMathUtils.formatCardinalDirection(135f))
        assertEquals("S", CompassMathUtils.formatCardinalDirection(180f))
        assertEquals("SW", CompassMathUtils.formatCardinalDirection(225f))
        assertEquals("W", CompassMathUtils.formatCardinalDirection(270f))
        assertEquals("NW", CompassMathUtils.formatCardinalDirection(315f))
    }

    @Test
    fun testPointerAngleCalculation() {
        // Heading 45, Target 90 -> Relative 45
        val p1 = CompassMathUtils.calculatePointerAngle(45f, 90f)
        assertEquals(45f, p1, 0.1f)

        // Heading 350, Target 10 -> Relative 20
        val p2 = CompassMathUtils.calculatePointerAngle(350f, 10f)
        assertEquals(20f, p2, 0.1f)

        // Heading 180, Target 0 -> Relative 180
        val p3 = CompassMathUtils.calculatePointerAngle(180f, 0f)
        assertEquals(180f, p3, 0.1f)
    }

    @Test
    fun testFindNearestSavedGem() {
        val userLat = 38.7112
        val userLng = -9.1423

        val farGem = ScoutSpotEntity(
            id = "far",
            title = "Far Gem",
            description = "Far away",
            category = "sights",
            distance = "5km",
            walkMinutes = 60,
            rating = 4.5,
            reviewCount = 10,
            imageUrl = "",
            badgeLabel = "SIGHTS",
            statusBadge = "Verified",
            tagsRaw = "",
            latitude = 38.7500,
            longitude = -9.1000,
            isSaved = true
        )

        val nearGem = ScoutSpotEntity(
            id = "near",
            title = "Near Gem",
            description = "Close by",
            category = "eats",
            distance = "200m",
            walkMinutes = 3,
            rating = 4.8,
            reviewCount = 50,
            imageUrl = "",
            badgeLabel = "EATS",
            statusBadge = "Verified",
            tagsRaw = "",
            latitude = 38.7120,
            longitude = -9.1420,
            isSaved = true
        )

        val nearest = CompassMathUtils.findNearestSavedGem(userLat, userLng, listOf(farGem, nearGem))
        assertNotNull(nearest)
        assertEquals("near", nearest?.id)
    }

    @Test
    fun testCompassSensorManagerLifecycleAndTrueNorthToggle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = CompassSensorManager(context)

        val initialState = manager.compassState.value
        assertTrue(initialState.isTrueNorthMode)

        manager.toggleTrueNorthMode()
        assertFalse(manager.compassState.value.isTrueNorthMode)

        manager.updateLocation(38.7112, -9.1423)
        // Verify declination computed without throwing exception
        val updatedState = manager.compassState.value
        assertNotNull(updatedState.declinationDegrees)
    }
}

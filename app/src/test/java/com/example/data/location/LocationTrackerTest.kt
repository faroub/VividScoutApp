package com.example.data.location

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceMeters
import com.example.data.local.computedDistanceString
import com.example.data.local.computedWalkMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocationTrackerTest {

    @Test
    fun testDistanceBetweenCalculation() {
        // Distance from Chiado (38.7112, -9.1423) to Miradouro de Santa Luzia (38.7118, -9.1306)
        val distance = LocationTracker.distanceBetween(
            startLat = 38.7112,
            startLng = -9.1423,
            endLat = 38.7118,
            endLng = -9.1306
        )
        // Expected approx ~1000 meters
        assertTrue("Distance should be between 800m and 1200m", distance in 800f..1200f)
    }

    @Test
    fun testFormatDistanceMetersAndKilometers() {
        val formattedShort = LocationTracker.formatDistance(180f)
        assertEquals("180m walk", formattedShort)

        val formattedLong = LocationTracker.formatDistance(1450f)
        assertEquals("1.5 km away", formattedLong)
    }

    @Test
    fun testWalkMinutesCalculation() {
        // 80 meters ~ 1 minute walk
        val minsShort = LocationTracker.calculateWalkMinutes(160f)
        assertEquals(2, minsShort)

        val minsLong = LocationTracker.calculateWalkMinutes(800f)
        assertEquals(10, minsLong)
    }

    @Test
    fun testSpotEntityDistanceExtensions() {
        val spot = ScoutSpotEntity(
            id = "test_spot",
            title = "Test Spot",
            description = "Test description",
            category = "sights",
            distance = "350m walk",
            walkMinutes = 5,
            rating = 4.8,
            reviewCount = "100",
            imageUrl = "https://example.com/image.jpg",
            badgeLabel = "SIGHTS",
            statusBadge = "Verified",
            tagsRaw = "Tag1|||Tag2",
            latitude = 38.7118,
            longitude = -9.1306
        )

        val userLat = 38.7112
        val userLng = -9.1423

        val distanceMeters = spot.computedDistanceMeters(userLat, userLng)
        assertTrue(distanceMeters > 0)

        val distanceStr = spot.computedDistanceString(userLat, userLng)
        assertTrue(distanceStr.contains("m walk") || distanceStr.contains("km away"))

        val walkMins = spot.computedWalkMinutes(userLat, userLng)
        assertTrue(walkMins > 0)
    }

    @Test
    fun testLocationTrackerDefaultFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tracker = LocationTracker(context)

        assertNotNull(tracker.defaultLocation)
        assertEquals(38.7112, tracker.defaultLocation.latitude, 0.0001)
        assertEquals(-9.1423, tracker.defaultLocation.longitude, 0.0001)
    }
}

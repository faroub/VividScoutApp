package com.example.engine

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRouteEngineTest {

    @Test
    fun calculateTrail_emptySpots_returnsNull() {
        val result = OfflineRouteEngine.calculateTrail(38.7112, -9.1423, emptyList())
        assertNull(result)
    }

    @Test
    fun calculateTrail_ordersWaypointsAndComputesMetrics() {
        val spot1 = ScoutSpotEntity(
            id = "spot_1",
            title = "Chiado Viewpoint",
            description = "Great vista",
            category = "sights",
            distance = "200m",
            walkMinutes = 3,
            rating = 4.8,
            reviewCount = "120",
            imageUrl = "https://example.com/photo1.jpg",
            badgeLabel = "Must See",
            statusBadge = "Verified",
            tagsRaw = "sights,viewpoint",
            latitude = 38.7115,
            longitude = -9.1420,
            locationName = "Chiado"
        )
        val spot2 = ScoutSpotEntity(
            id = "spot_2",
            title = "Alfama Alleyway",
            description = "Historic cobblestones",
            category = "history",
            distance = "800m",
            walkMinutes = 10,
            rating = 4.9,
            reviewCount = "250",
            imageUrl = "https://example.com/photo2.jpg",
            badgeLabel = "Secret",
            statusBadge = "Local Favorite",
            tagsRaw = "history,alleyway",
            latitude = 38.7130,
            longitude = -9.1350,
            locationName = "Alfama"
        )

        val trail = OfflineRouteEngine.calculateTrail(38.7112, -9.1423, listOf(spot1, spot2))

        assertNotNull(trail)
        assertEquals(2, trail!!.waypoints.size)
        assertTrue("Path points should include interpolated steps", trail.pathPoints.size >= 5)
        assertTrue("Total distance should be greater than 0", trail.totalDistanceMeters > 0)
        assertTrue("Estimated duration should be at least 1 min", trail.estimatedWalkingMinutes >= 1)
        assertNotNull(trail.formattedDistance)
        assertNotNull(trail.formattedDuration)
    }
}

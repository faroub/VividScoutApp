package com.example.data.offline

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedWalkPlannerEngineTest {

    private fun createSpot(
        id: String,
        title: String,
        category: String,
        lat: Double,
        lng: Double
    ): ScoutSpotEntity {
        return ScoutSpotEntity(
            id = id,
            title = title,
            description = "Description for $title",
            category = category,
            distance = "Nearby",
            walkMinutes = 5,
            imageUrl = "",
            badgeLabel = "Spot",
            statusBadge = "Scout Spot",
            tagsRaw = "$category|||test",
            latitude = lat,
            longitude = lng
        )
    }

    @Test
    fun planGuidedWalk_returnsCircularLoopWithReturnLegAndDuration() {
        val originLat = 38.7112
        val originLng = -9.1423

        val spots = listOf(
            createSpot("s1", "Miradouro 500m", "sights", 38.7150, -9.1423),
            createSpot("s2", "Miradouro 1000m", "sights", 38.7200, -9.1423)
        )

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio Square",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = spots
        )

        assertTrue("Generated plan must be marked as circular loop", plan.isCircularLoop)
        assertTrue("Return leg distance must be > 0.0 meters", plan.returnLegDistanceMeters > 0.0)
        assertTrue("Total walking time must include return leg to origin", plan.totalWalkingMinutes > 0)
    }

    @Test
    fun planGuidedWalk_durationLimitRespected_doesNotExceedSelectedDuration() {
        val originLat = 38.7112
        val originLng = -9.1423
        val targetDurationMinutes = 30 // 30 minute limit

        // Create spots spaced at 500m, 1200m, 2500m, 5000m away
        val spots = listOf(
            createSpot("s1", "Spot 500m", "sights", 38.7150, -9.1423),
            createSpot("s2", "Spot 1200m", "sights", 38.7220, -9.1423),
            createSpot("s3", "Spot 2500m", "sights", 38.7330, -9.1423),
            createSpot("s4", "Spot 5000m", "sights", 38.7560, -9.1423)
        )

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio Square",
            targetDurationMinutes = targetDurationMinutes,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = spots
        )

        val maxAllowedWalkTime = (targetDurationMinutes * 1.10).toInt()
        assertTrue(
            "Total walking time (${plan.totalWalkingMinutes} min) must respect 30-min duration limit including return leg",
            plan.totalWalkingMinutes <= maxAllowedWalkTime
        )
    }

    @Test
    fun planGuidedWalk_moodFilter_returnsOnlyMatchingCategories() {
        val originLat = 38.7112
        val originLng = -9.1423

        val spots = listOf(
            createSpot("s1", "Bakery Central", "eats", 38.7120, -9.1423),
            createSpot("s2", "Carmo Convent Ruins", "history", 38.7125, -9.1423),
            createSpot("s3", "Sao Jorge Castle", "history", 38.7135, -9.1423),
            createSpot("s4", "Scenic Miradouro", "sights", 38.7140, -9.1423)
        )

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Current Location",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.HISTORIC,
            availableSpots = spots
        )

        assertTrue("Historic walk plan must contain waypoints", plan.waypoints.isNotEmpty())
        for (spot in plan.waypoints) {
            val catLower = spot.category.lowercase()
            assertTrue("Waypoint '${spot.title}' category '$catLower' must match historic mood", catLower.contains("history"))
        }
    }

    @Test
    fun planGuidedWalk_noMatchingSpots_returnsEmptyStatePlanWithoutFakeLoop() {
        val originLat = 38.7112
        val originLng = -9.1423

        val spots = listOf(
            createSpot("s1", "Pastelaria", "eats", 38.7120, -9.1423)
        )

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Current Location",
            targetDurationMinutes = 30,
            mood = GuidedWalkMood.SCENIC, // Scenic requested, but only eats spot available
            availableSpots = spots
        )

        assertTrue("Plan with no matching spots must return empty waypoints", plan.waypoints.isEmpty())
        assertFalse("Empty plan cannot be marked as circular loop", plan.isCircularLoop)
        assertEquals(0, plan.totalWalkingMinutes)
        assertEquals(0.0, plan.totalDistanceMeters, 0.01)
        assertNull("Empty plan must not produce fake elevation chart", plan.elevationProfile)
    }
}

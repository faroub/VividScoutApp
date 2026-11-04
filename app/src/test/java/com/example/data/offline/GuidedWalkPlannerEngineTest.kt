package com.example.data.offline

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun planGuidedWalk_lisbon_receivesLisbonElevationModel() {
        val lisbonSpot = createSpot("s1", "Miradouro 500m", "sights", 38.7150, -9.1423)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 38.7112,
            originLng = -9.1423,
            startTitle = "Rossio Square",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(lisbonSpot),
            cityName = "Lisbon, Portugal"
        )

        assertNotNull("Lisbon routes MUST receive Lisbon elevation profile", plan.elevationProfile)
        assertTrue(plan.elevationProfile!!.points.size >= 2)
    }

    @Test
    fun planGuidedWalk_unsupportedCity_doesNotReceiveLisbonElevationData() {
        // Dresden, Germany coordinates and city name
        val dresdenSpot = createSpot("d1", "Frauenkirche 500m", "sights", 51.0530, 13.7373)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 51.0504,
            originLng = 13.7373,
            startTitle = "Neumarkt",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(dresdenSpot),
            cityName = "Dresden, Germany"
        )

        assertNull("Unsupported cities MUST NOT receive Lisbon elevation data", plan.elevationProfile)
    }

    @Test
    fun planGuidedWalk_validElevationProfile_includesCompleteRouteWithReturnLeg() {
        val lisbonSpot = createSpot("s1", "Miradouro 500m", "sights", 38.7150, -9.1423)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 38.7112,
            originLng = -9.1423,
            startTitle = "Rossio Square",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(lisbonSpot),
            cityName = "Lisbon, Portugal"
        )

        assertNotNull(plan.elevationProfile)
        val lastPoint = plan.elevationProfile!!.points.last()
        assertEquals(plan.totalDistanceMeters, lastPoint.distanceMeters, 50.0)
    }

    @Test
    fun planGuidedWalk_oneDistantWaypointAndShortDuration_skipsDistantWaypoint() {
        val originLat = 38.7112
        val originLng = -9.1423
        val shortDurationMinutes = 15 // 15 minute walk budget

        // Spot 10 km away -> round trip > 120 minutes (exceeds 15 min budget)
        val distantSpot = createSpot("distant_1", "Distant Castle", "sights", 38.8000, -9.1423)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio",
            targetDurationMinutes = shortDurationMinutes,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(distantSpot)
        )

        assertTrue("First waypoint exceeding duration budget MUST be skipped, returning empty plan", plan.waypoints.isEmpty())
        assertFalse(plan.isCircularLoop)
        assertEquals(0, plan.totalWalkingMinutes)
    }

    @Test
    fun planGuidedWalk_firstWaypointWithinDurationBudget_isAccepted() {
        val originLat = 38.7112
        val originLng = -9.1423

        // Spot 300m away -> round trip ~600m (~7 min walk, fits inside 30 min budget)
        val closeSpot = createSpot("close_1", "Nearby Miradouro", "sights", 38.7130, -9.1423)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio",
            targetDurationMinutes = 30,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(closeSpot)
        )

        assertEquals(1, plan.waypoints.size)
        assertEquals("close_1", plan.waypoints.first().id)
        assertTrue(plan.isCircularLoop)
        assertTrue(plan.totalWalkingMinutes <= 30)
    }

    @Test
    fun planGuidedWalk_infeasibleFirstCandidateSkipped_laterFeasibleCandidateAccepted() {
        val originLat = 38.7112
        val originLng = -9.1423

        // Candidate 1 (infeasible): 8 km away
        val infeasibleSpot = createSpot("infeasible_1", "Far Monument", "sights", 38.7800, -9.1423)
        // Candidate 2 (feasible): 400m away
        val feasibleSpot = createSpot("feasible_2", "Nearby Viewpoint", "sights", 38.7135, -9.1423)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio",
            targetDurationMinutes = 20,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(infeasibleSpot, feasibleSpot)
        )

        assertEquals("Engine must skip infeasible candidate and select feasible candidate", 1, plan.waypoints.size)
        assertEquals("feasible_2", plan.waypoints.first().id)
        assertTrue(plan.isCircularLoop)
    }

    @Test
    fun planGuidedWalk_returnLegDistance_isIncludedInTotalDistanceAndDuration() {
        val originLat = 38.7112
        val originLng = -9.1423

        val spots = listOf(
            createSpot("s1", "Miradouro 500m", "sights", 38.7150, -9.1423)
        )

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio Square",
            targetDurationMinutes = 30,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = spots
        )

        assertTrue(plan.isCircularLoop)
        assertTrue("Return leg distance must be > 0.0 meters", plan.returnLegDistanceMeters > 0.0)
        assertTrue("Total loop distance must equal outbound + return leg distance", plan.totalDistanceMeters > plan.returnLegDistanceMeters)
        assertTrue("Total walking time must include return leg to origin", plan.totalWalkingMinutes > 0)
    }

    @Test
    fun planGuidedWalk_invalidCoordinates_returnsEmptyPlan() {
        val spots = listOf(createSpot("s1", "Spot", "sights", 38.7120, -9.1423))

        // NaN origin latitude
        val nanPlan = GuidedWalkPlannerEngine.planGuidedWalk(Double.NaN, -9.1423, "Start", 30, GuidedWalkMood.SCENIC, spots)
        assertTrue("NaN origin coordinates must return empty plan", nanPlan.waypoints.isEmpty())

        // Out of bounds origin latitude (> 90)
        val outOfBoundsPlan = GuidedWalkPlannerEngine.planGuidedWalk(100.0, -9.1423, "Start", 30, GuidedWalkMood.SCENIC, spots)
        assertTrue("Out of bounds origin coordinates must return empty plan", outOfBoundsPlan.waypoints.isEmpty())

        // Null Island origin (0, 0)
        val nullIslandPlan = GuidedWalkPlannerEngine.planGuidedWalk(0.0, 0.0, "Start", 30, GuidedWalkMood.SCENIC, spots)
        assertTrue("Null Island origin coordinates must return empty plan", nullIslandPlan.waypoints.isEmpty())
    }

    @Test
    fun planGuidedWalk_zeroOrNegativeDuration_returnsEmptyPlan() {
        val spots = listOf(createSpot("s1", "Spot 500m", "sights", 38.7130, -9.1423))

        val zeroPlan = GuidedWalkPlannerEngine.planGuidedWalk(38.7112, -9.1423, "Start", 0, GuidedWalkMood.SCENIC, spots)
        assertTrue("Zero duration budget must return empty plan", zeroPlan.waypoints.isEmpty())

        val negativePlan = GuidedWalkPlannerEngine.planGuidedWalk(38.7112, -9.1423, "Start", -15, GuidedWalkMood.SCENIC, spots)
        assertTrue("Negative duration budget must return empty plan", negativePlan.waypoints.isEmpty())
    }

    @Test
    fun planGuidedWalk_emptyCandidateList_returnsEmptyPlan() {
        val plan = GuidedWalkPlannerEngine.planGuidedWalk(38.7112, -9.1423, "Start", 30, GuidedWalkMood.SCENIC, emptyList())
        assertTrue("Empty candidate list must return empty plan", plan.waypoints.isEmpty())
        assertFalse(plan.isCircularLoop)
        assertEquals(0, plan.totalWalkingMinutes)
        assertNull(plan.elevationProfile)
    }
}

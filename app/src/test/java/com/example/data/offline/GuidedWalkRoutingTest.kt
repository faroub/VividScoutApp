package com.example.data.offline

import com.example.data.local.ScoutSpotEntity
import com.example.engine.AStarPathfinder
import com.example.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that guided-walk distances and durations follow the offline pedestrian
 * network (A*) rather than straight-line segments, that the return leg is included,
 * and that lapses in routing coverage are honestly labeled approximate instead of
 * being presented as routed walking distances.
 */
class GuidedWalkRoutingTest {

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
    fun lisbonLoop_isFullyRoutedAlongPedestrianNetwork() {
        // Praça do Comércio area origin; waypoint on a graph-covered street.
        val originLat = 38.7075
        val originLng = -9.1364
        val waypoint = createSpot("r1", "Largo do Carmo", "sights", 38.7122, -9.1404)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Praça do Comércio",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(waypoint)
        )

        assertTrue("Loop must be circular", plan.isCircularLoop)
        assertTrue("Lisbon legs on the pedestrian network must be routed", plan.isFullyRouted)
        assertEquals(
            "Routed label must state the network provenance",
            "Pedestrian route · offline walking network",
            plan.routingLabel
        )
        assertTrue("Routed loop must have a positive distance", plan.totalDistanceMeters > 0)
    }

    @Test
    fun routedLoopDistance_isAtLeastStraightLineDistance() {
        val originLat = 38.7075
        val originLng = -9.1364
        val waypoint = createSpot("r1", "Rossio", "sights", 38.7138, -9.1393)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Terreiro do Paço",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(waypoint)
        )

        val straightLine = Geo.calculateDistanceMeters(originLat, originLng, waypoint.latitude, waypoint.longitude) * 2
        assertTrue(
            "Routed distance (${'$'}{plan.totalDistanceMeters} m) must be at least the straight-line total (${'$'}{straightLine} m)",
            plan.totalDistanceMeters >= straightLine - 50.0
        )
    }

    @Test
    fun returnLeg_isIncludedInRoutedTotal() {
        val originLat = 38.7075
        val originLng = -9.1364
        val waypoint = createSpot("r1", "Castle", "sights", 38.7139, -9.1334)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Terreiro do Paço",
            targetDurationMinutes = 90,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(waypoint)
        )

        assertTrue(plan.isCircularLoop)
        assertTrue("Return leg must carry a routed distance back to the origin", plan.returnLegDistanceMeters > 0)
        assertTrue(
            "Total must exceed the return leg by the outbound portion",
            plan.totalDistanceMeters > plan.returnLegDistanceMeters
        )
    }

    @Test
    fun routingUnavailable_outsideCoverage_marksPlanApproximate() {
        // Dresden has no offline pedestrian graph coverage.
        val dresdenSpot = createSpot("d1", "Frauenkirche", "sights", 51.0530, 13.7373)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 51.0504,
            originLng = 13.7373,
            startTitle = "Neumarkt",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(dresdenSpot)
        )

        assertTrue("Walk must still be planned when routing is unavailable", plan.waypoints.isNotEmpty())
        assertFalse("Unrouted legs must mark the plan approximate", plan.isFullyRouted)
        assertEquals(
            "Approximate label must state that routing is unavailable",
            "Approximate · offline routing unavailable for part of this loop",
            plan.routingLabel
        )
        // The fallback distance is the straight-line sum of the legs.
        val straightTotal = Geo.calculateDistanceMeters(51.0504, 13.7373, 51.0530, 13.7373) * 2
        assertEquals(straightTotal, plan.totalDistanceMeters, 200.0)
    }

    @Test
    fun waypointBeyondSnapRadius_marksPlanApproximate() {
        // Santos district, ~1.5 km from Rossio: within a 60-minute walking budget
        // but ~1.1 km from the nearest covered graph node (Cais do Sodré), i.e.
        // beyond AStarPathfinder.MAX_SNAP_METERS. The leg must fall back to a
        // straight-line estimate and the plan must be labeled approximate.
        val santos = createSpot("f1", "Santos District", "sights", 38.6990, -9.1500)
        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 38.7112,
            originLng = -9.1423,
            startTitle = "Rossio",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(santos)
        )

        assertTrue("The waypoint must be kept (round trip ~40 min fits the budget)", plan.waypoints.isNotEmpty())
        assertTrue("Total distance must be the straight-line fallback sum", plan.totalDistanceMeters > 2000.0)
        assertFalse("A leg beyond the graph snap radius must mark the plan approximate", plan.isFullyRouted)
    }

    @Test
    fun durationBudget_isRespectedWithRoutedLegs() {
        val originLat = 38.7112
        val originLng = -9.1423
        val spots = (1..6).map { i ->
            createSpot("b$i", "Nearby Spot $i", "sights", originLat + i * 0.0004, originLng + i * 0.0002)
        }

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio",
            targetDurationMinutes = 30,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = spots
        )

        assertTrue("Total walking minutes must respect the duration budget", plan.totalWalkingMinutes <= 30 * 1.1)
    }

    @Test
    fun firstWaypointWithinBudget_isAcceptedAndRouted() {
        val originLat = 38.7112
        val originLng = -9.1423
        val closeSpot = createSpot("c1", "Praça da Figueira", "sights", 38.7132, -9.1373)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = originLat,
            originLng = originLng,
            startTitle = "Rossio",
            targetDurationMinutes = 30,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(closeSpot)
        )

        assertEquals("c1", plan.waypoints.first().id)
        assertTrue(plan.isCircularLoop)
        assertTrue("First waypoint within budget must be routed when covered", plan.isFullyRouted)
    }

    @Test
    fun invalidWaypointCoordinates_areSkipped() {
        val valid = createSpot("v1", "Valid Spot", "sights", 38.7132, -9.1373)
        val invalid = createSpot("i1", "Invalid Spot", "sights", Double.NaN, -9.1373)
        val nullIsland = createSpot("i2", "Null Island", "sights", 0.0, 0.0)

        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 38.7112,
            originLng = -9.1423,
            startTitle = "Rossio",
            targetDurationMinutes = 30,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(invalid, nullIsland, valid)
        )

        assertEquals("Only the coordinate-valid waypoint may be selected", listOf("v1"), plan.waypoints.map { it.id })
    }

    @Test
    fun aStarPathfinder_unsupportedRegion_returnsDirectFallback() {
        // Dresden-to-Dresden: no graph coverage, so the engine must return the
        // clearly-flagged straight-line estimate instead of a route snapped to a
        // distant Lisbon street (which the old unbounded snapping produced).
        val result = AStarPathfinder.findShortestPath(
            startLat = 51.0504,
            startLng = 13.7373,
            destLat = 51.0530,
            destLng = 13.7373,
            startName = "Neumarkt",
            destName = "Frauenkirche"
        )

        assertTrue("Uncovered regions must flag the direct fallback", result.isDirectFallback)
        val expected = Geo.calculateDistanceMeters(51.0504, 13.7373, 51.0530, 13.7373)
        assertEquals(expected, result.totalDistanceMeters, 50.0)
    }

    @Test
    fun aStarPathfinder_coveredRegion_isNotAFallback() {
        val result = AStarPathfinder.findShortestPath(
            startLat = 38.7075,
            startLng = -9.1364,
            destLat = 38.7138,
            destLng = -9.1393,
            startName = "Terreiro do Paço",
            destName = "Rossio"
        )

        assertFalse("Covered Lisbon routes must be real graph paths, not fallbacks", result.isDirectFallback)
        assertTrue("Routed distance must exceed the straight-line snap", result.totalDistanceMeters > 400.0)
    }
}

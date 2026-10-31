package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AStarPathfinderTest {

    @Test
    fun findShortestPath_calculatesRouteBetweenHistoricLandmarks() {
        // Praça do Comércio to Largo do Carmo (Baixa to Chiado)
        val pracaComercioLat = 38.7075
        val pracaComercioLng = -9.1364
        val largoCarmoLat = 38.7122
        val largoCarmoLng = -9.1404

        val result = AStarPathfinder.findShortestPath(
            startLat = pracaComercioLat,
            startLng = pracaComercioLng,
            destLat = largoCarmoLat,
            destLng = largoCarmoLng,
            startName = "Praça do Comércio",
            destName = "Largo do Carmo"
        )

        assertNotNull(result)
        assertEquals("Praça do Comércio", result.originName)
        assertEquals("Largo do Carmo", result.destinationName)
        assertTrue("Distance should be roughly 500-1200 meters", result.totalDistanceMeters in 400.0..1500.0)
        assertTrue("Walking time should be 5 to 20 minutes", result.estimatedWalkingMinutes in 5..20)
        assertTrue("Path points should be interpolated", result.pathPoints.size >= 10)
        assertTrue("Steps should be generated", result.steps.isNotEmpty())

        val arrivalStep = result.steps.last()
        assertTrue("Last step should be arrival", arrivalStep.instruction.contains("Arrive"))
    }

    @Test
    fun findShortestPath_snapsArbitraryGpsCoordinatesToRoadNetwork() {
        // Off-network GPS point in Alfama alleyway to Chiado bookshop
        val arbitraryLat = 38.71182
        val arbitraryLng = -9.13025
        val destLat = 38.7107
        val destLng = -9.1419

        val result = AStarPathfinder.findShortestPath(
            startLat = arbitraryLat,
            startLng = arbitraryLng,
            destLat = destLat,
            destLng = destLng,
            startName = "User GPS Beacon",
            destName = "Livraria Bertrand"
        )

        assertNotNull(result)
        assertTrue("Total distance should be greater than zero", result.totalDistanceMeters > 0)
        assertTrue("Should have multiple walking steps", result.steps.size >= 2)
        assertTrue("Formatted distance should end in m or km", result.formattedDistance.endsWith("m") || result.formattedDistance.endsWith("km"))
    }

    @Test
    fun findShortestPath_sameCoordinates_returnsInstantArrival() {
        val lat = 38.7112
        val lng = -9.1423

        val result = AStarPathfinder.findShortestPath(
            startLat = lat,
            startLng = lng,
            destLat = lat,
            destLng = lng,
            startName = "Current Spot",
            destName = "Current Spot"
        )

        assertNotNull(result)
        assertTrue("Distance should be minimal (< 10m)", result.totalDistanceMeters < 10.0)
        assertEquals(1, result.estimatedWalkingMinutes)
        assertEquals(1, result.steps.size)
        assertTrue(result.steps.first().instruction.contains("arrived"))
    }

    @Test
    fun calculateShortestAStarRoute_integratesWithOfflineRouteEngine() {
        val trail = OfflineRouteEngine.calculateShortestAStarRoute(
            originLat = 38.7075,
            originLng = -9.1364,
            destLat = 38.7138,
            destLng = -9.1393,
            originName = "Terreiro do Paço",
            destName = "Rossio Square"
        )

        assertNotNull(trail)
        assertEquals("Terreiro do Paço", trail.originTitle)
        assertEquals("Rossio Square", trail.destinationTitle)
        assertTrue("Distance should be around 700-1100m", trail.totalDistanceMeters in 500.0..1400.0)
        assertTrue("Steps should be populated", trail.steps.isNotEmpty())
        assertTrue("Path points should be generated", trail.pathPoints.isNotEmpty())
    }
}

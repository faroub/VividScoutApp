package com.example.engine

import com.example.data.offline.DownloadedRoadSegment
import com.example.data.offline.DownloadedRegionPack
import com.example.data.offline.GeoBoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionDownloaderTest {

    @Test
    fun geoBoundingBox_calculatesDimensionsAndContainment() {
        val bounds = GeoBoundingBox(
            minLat = 38.7000,
            minLng = -9.1500,
            maxLat = 38.7200,
            maxLng = -9.1300
        )

        assertEquals(38.7100, bounds.centerLat, 0.0001)
        assertEquals(-9.1400, bounds.centerLng, 0.0001)

        assertTrue("Width should be roughly 1.5 - 2.5 km", bounds.widthKm in 1.5..2.5)
        assertTrue("Height should be roughly 2.0 - 2.5 km", bounds.heightKm in 2.0..2.5)
        assertTrue("Formatted dimensions should contain 'km ×'", bounds.formattedDimensions.contains("km ×"))

        assertTrue("Bounds should contain center coordinate", bounds.contains(38.7100, -9.1400))
        assertFalse("Bounds should exclude far-away coordinate", bounds.contains(40.0000, -8.0000))
    }

    @Test
    fun vectorWalkGraph_loadsCustomRegionSegmentsAndEnablesAStarRouting() {
        // Create custom road network in remote Sintra area (outside Lisbon base network)
        val sintraMinLat = 38.7900
        val sintraMinLng = -9.3900

        val seg1 = DownloadedRoadSegment(
            id = "sintra_seg_1",
            fromId = "sintra_palao",
            toId = "sintra_town_center",
            fromLat = sintraMinLat,
            fromLng = sintraMinLng,
            toLat = sintraMinLat + 0.005,
            toLng = sintraMinLng + 0.005,
            distanceMeters = 650.0,
            streetName = "Rua das Pedras Negras Sintra",
            isPedestrian = true,
            isStairs = false
        )

        val seg2 = DownloadedRoadSegment(
            id = "sintra_seg_2",
            fromId = "sintra_town_center",
            toId = "sintra_castle_trail",
            fromLat = sintraMinLat + 0.005,
            fromLng = sintraMinLng + 0.005,
            toLat = sintraMinLat + 0.010,
            toLng = sintraMinLng + 0.010,
            distanceMeters = 800.0,
            streetName = "Calçada do Castelo dos Mouros",
            isPedestrian = true,
            isStairs = true
        )

        VectorWalkGraph.loadCustomRegionSegments(listOf(seg1, seg2))

        // Perform A* pathfinding across the custom loaded region
        val route = AStarPathfinder.findShortestPath(
            startLat = sintraMinLat,
            startLng = sintraMinLng,
            destLat = sintraMinLat + 0.010,
            destLng = sintraMinLng + 0.010,
            startName = "Sintra Palace",
            destName = "Moorish Castle"
        )

        assertNotNull(route)
        assertEquals("Sintra Palace", route.originName)
        assertEquals("Moorish Castle", route.destinationName)
        assertTrue("Total distance should be ~1450m", route.totalDistanceMeters in 1200.0..1800.0)
        assertTrue("Should contain path points", route.pathPoints.isNotEmpty())
    }

    @Test
    fun downloadedRegionPack_formatsSize() {
        val pack = DownloadedRegionPack(
            id = "custom_test_1",
            name = "Sintra Forest Pass",
            bounds = GeoBoundingBox(38.7900, -9.3900, 38.8000, -9.3800),
            roadSegments = emptyList(),
            elevationAnchors = emptyList(),
            spotsCount = 5,
            totalSizeBytes = 2 * 1024 * 1024 // 2 MB
        )

        assertEquals("2.0 MB", pack.formattedSize)
    }
}

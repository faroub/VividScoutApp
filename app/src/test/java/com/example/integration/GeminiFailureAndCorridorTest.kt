package com.example.integration

import com.example.data.network.GeminiQueryService
import com.example.data.network.GeminiSpotService
import com.example.engine.OfflineRouteEngine
import com.example.engine.OfflineWalkingTrail
import com.example.util.LocationBreadcrumb
import com.example.util.MovementVectorUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeminiFailureAndCorridorTest {

    @Test
    fun geminiSpotService_rejectsInvalidCoordinates() {
        val service = GeminiSpotService()
        val userLat = 38.7112
        val userLng = -9.1423
        val radiusMeters = 2000

        // JSON with NaN, Null Island (0,0), out of bounds (95, -200), and 1 valid coordinate
        val json = """
            {
              "spots": [
                {
                  "title": "Valid Nearby Spot",
                  "locationName": "Chiado",
                  "category": "sights",
                  "description": "Valid historic square.",
                  "latitude": 38.7120,
                  "longitude": -9.1410,
                  "distanceText": "120m away"
                },
                {
                  "title": "Null Island Spot",
                  "locationName": "Ocean",
                  "category": "gems",
                  "description": "Zero coordinate spot.",
                  "latitude": 0.0,
                  "longitude": 0.0,
                  "distanceText": "Far away"
                },
                {
                  "title": "Out of Bounds Lat",
                  "locationName": "North Pole Extreme",
                  "category": "sights",
                  "description": "Latitude above 90.",
                  "latitude": 95.0,
                  "longitude": -9.1423,
                  "distanceText": "5000km away"
                },
                {
                  "title": "Out of Bounds Lng",
                  "locationName": "Date Line Beyond",
                  "category": "sights",
                  "description": "Longitude above 180.",
                  "latitude": 38.7112,
                  "longitude": 195.0,
                  "distanceText": "Far away"
                }
              ]
            }
        """.trimIndent()

        val parsed = service.parseSpotsJson(json, userLat, userLng, radiusMeters)

        assertEquals("Only the valid spot must be accepted", 1, parsed.size)
        assertEquals("Valid Nearby Spot", parsed.first().title)
        assertNull("Ratings must be null for AI spots", parsed.first().rating)
    }

    @Test
    fun geminiSpotService_fallsBackToCuratedOnMalformedOrEmptyJson() = runBlocking {
        val service = GeminiSpotService()
        // If JSON has 0 valid spots or is empty, parseSpotsJson returns emptyList
        val emptyJson = """{ "spots": [] }"""
        val emptyList = service.parseSpotsJson(emptyJson, 38.7112, -9.1423, 2000)
        assertTrue("Empty spots JSON produces empty list", emptyList.isEmpty())

        // Curated fallback produces verified fallback spots with null ratings
        val curated = service.generateCuratedVicinitySpots(38.7112, -9.1423, "all")
        assertTrue("Curated fallback must return spots", curated.isNotEmpty())
        for (spot in curated) {
            assertNull("Curated spots must never present artificial ratings as real", spot.rating)
            assertNull("Curated reviewCount must be null", spot.reviewCount)
        }
    }

    @Test
    fun movementVectorUtils_validatesRouteCorridorAccurately() {
        val originLat = 38.7112
        val originLng = -9.1423
        val breadcrumbs = listOf(
            LocationBreadcrumb(latitude = 38.7110, longitude = -9.1425, bearingDegrees = 45f),
            LocationBreadcrumb(latitude = originLat, longitude = originLng, bearingDegrees = 45f)
        )

        val vector = MovementVectorUtils.calculateMovementVector(breadcrumbs, fallbackBearing = 45f, distanceMeters = 5000.0)

        // 1. Point right along the 45 degree path (e.g. ~1km ahead)
        val (alongPathLat, alongPathLng) = MovementVectorUtils.extrapolateTargetCoordinate(
            originLat, originLng, 45f, distanceMeters = 1500.0
        )
        val isAlongPathInCorridor = MovementVectorUtils.isPointInCorridor(alongPathLat, alongPathLng, vector)
        assertTrue("Point 1.5km directly along travel heading must be within corridor", isAlongPathInCorridor)

        // 2. Point 5km perpendicular (lateral) to the travel path (bearing 135 degrees)
        val (farPerpendicularLat, farPerpendicularLng) = MovementVectorUtils.extrapolateTargetCoordinate(
            alongPathLat, alongPathLng, 135f, distanceMeters = 4000.0 // 4km cross-track
        )
        val isFarPointInCorridor = MovementVectorUtils.isPointInCorridor(farPerpendicularLat, farPerpendicularLng, vector, maxLateralMeters = 1500.0)
        assertFalse("Point 4km perpendicular to route must be rejected", isFarPointInCorridor)

        // 3. Point behind origin in the opposite direction (225 degrees)
        val (behindLat, behindLng) = MovementVectorUtils.extrapolateTargetCoordinate(
            originLat, originLng, 225f, distanceMeters = 2000.0
        )
        val isBehindInCorridor = MovementVectorUtils.isPointInCorridor(behindLat, behindLng, vector)
        assertFalse("Point 2km behind origin must be rejected", isBehindInCorridor)

        // 4. Invalid coordinates (NaN, out of range, null island)
        assertFalse(MovementVectorUtils.isPointInCorridor(Double.NaN, -9.14, vector))
        assertFalse(MovementVectorUtils.isPointInCorridor(0.0, 0.0, vector))
        assertFalse(MovementVectorUtils.isPointInCorridor(91.0, 0.0, vector))
    }

    @Test
    fun geminiQueryService_filtersCandidatesOutsideCorridor() {
        val queryService = GeminiQueryService()
        val originLat = 38.7112
        val originLng = -9.1423
        val breadcrumbs = listOf(
            LocationBreadcrumb(latitude = originLat, longitude = originLng, bearingDegrees = 90f) // Eastbound
        )
        val vector = MovementVectorUtils.calculateMovementVector(breadcrumbs, fallbackBearing = 90f, distanceMeters = 5000.0)

        // Coordinate 1km east (in corridor)
        val (onCorridorLat, onCorridorLng) = MovementVectorUtils.extrapolateTargetCoordinate(originLat, originLng, 90f, 1000.0)
        // Coordinate 5km north (way outside eastbound corridor)
        val (offCorridorLat, offCorridorLng) = MovementVectorUtils.extrapolateTargetCoordinate(originLat, originLng, 0f, 5000.0)

        val json = """
            {
              "spots": [
                {
                  "title": "Eastbound Corridor Spot",
                  "locationName": "East District",
                  "category": "sights",
                  "description": "On track.",
                  "latitude": $onCorridorLat,
                  "longitude": $onCorridorLng,
                  "distanceText": "1km ahead"
                },
                {
                  "title": "North Out of Corridor Spot",
                  "locationName": "North District",
                  "category": "sights",
                  "description": "Off track.",
                  "latitude": $offCorridorLat,
                  "longitude": $offCorridorLng,
                  "distanceText": "5km north"
                }
              ]
            }
        """.trimIndent()

        val parsed = queryService.parseSpots(json, vector)

        assertEquals("Only on-corridor spot should be kept", 1, parsed.size)
        assertEquals("Eastbound Corridor Spot", parsed.first().title)
        assertNull("Ratings must be null for AI spots", parsed.first().rating)
    }

    @Test
    fun offlineRouteEngine_validatesPointWithinRouteCorridor() {
        val trail = OfflineWalkingTrail(
            waypoints = emptyList(),
            pathPoints = listOf(
                Pair(38.7100, -9.1400),
                Pair(38.7150, -9.1400),
                Pair(38.7200, -9.1400)
            ),
            totalDistanceMeters = 1110.0,
            estimatedWalkingMinutes = 15
        )

        // Point directly near the middle point (within 50 meters)
        assertTrue(
            "Point near trail segment must be accepted",
            OfflineRouteEngine.isPointWithinRouteCorridor(38.7151, -9.1402, trail, maxDeviationMeters = 300.0)
        )

        // Point 5km away
        assertFalse(
            "Point far away from trail must be rejected",
            OfflineRouteEngine.isPointWithinRouteCorridor(38.7500, -9.1400, trail, maxDeviationMeters = 300.0)
        )
    }
}

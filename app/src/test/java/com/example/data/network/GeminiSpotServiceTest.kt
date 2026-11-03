package com.example.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeminiSpotServiceTest {

    @Test
    fun geminiModelConstants_providesDefaultModelIdsAndDisplayNames() {
        assertEquals("gemini-3.8-flash", GeminiModelConstants.DEFAULT_PRIMARY_MODEL_ID)
        assertEquals("gemini-3.5-flash", GeminiModelConstants.DEFAULT_SECONDARY_MODEL_ID)
        assertEquals("Gemini 3.8 Flash", GeminiModelConstants.PRIMARY_DISPLAY_NAME)
        assertEquals("Gemini 3.5 Flash", GeminiModelConstants.SECONDARY_DISPLAY_NAME)

        // When RemoteConfig is uninitialized in test, getPrimaryModelId & getSecondaryModelId return defaults
        assertEquals("gemini-3.8-flash", GeminiModelConstants.getPrimaryModelId())
        assertEquals("gemini-3.5-flash", GeminiModelConstants.getSecondaryModelId())
    }

    @Test
    fun geminiSpotService_fallbackToCuratedVicinitySpotsOnException() = runBlocking {
        val service = GeminiSpotService()
        // Calling discoverGems in offline unit test environment triggers fallback safely without crashing
        val result = service.discoverGems(
            userLat = 38.7112,
            userLng = -9.1423,
            category = "all",
            radiusMeters = 2000
        )

        assertTrue("discoverGems must succeed using fallback curated spots when network unavailable", result.isSuccess)
        val spots = result.getOrNull()
        assertNotNull(spots)
        assertTrue("Fallback spots must be returned", spots!!.isNotEmpty())
    }

    @Test
    fun parseSpotsJson_rejectsCoordinatesExceedingRequestedRadius() {
        val service = GeminiSpotService()
        val userLat = 38.7112
        val userLng = -9.1423
        val radiusMeters = 2000 // 2 km

        // Synthetic JSON with 1 nearby spot (~200m) and 1 far spot (~70km away in Setúbal/Sintra)
        val json = """
            {
              "spots": [
                {
                  "title": "Nearby Pastry Shop",
                  "locationName": "Chiado",
                  "category": "restaurant",
                  "description": "Authentic custard tarts.",
                  "latitude": 38.7125,
                  "longitude": -9.1415,
                  "distanceText": "200m away"
                },
                {
                  "title": "Far Away Landmark",
                  "locationName": "Distant Region",
                  "category": "touristic",
                  "description": "Spot located 70km away.",
                  "latitude": 39.3000,
                  "longitude": -9.1423,
                  "distanceText": "70km away"
                }
              ]
            }
        """.trimIndent()

        val spots = service.parseSpotsJson(json, userLat, userLng, radiusMeters)

        assertEquals("Should reject spot that is 70km away when 2km radius requested", 1, spots.size)
        assertEquals("Nearby Pastry Shop", spots.first().title)
    }

    @Test
    fun parseSpotsJson_acceptsSpotsWithinBoundaryMargin() {
        val service = GeminiSpotService()
        val userLat = 51.0504
        val userLng = 13.7373
        val radiusMeters = 2000

        // Spot located 1.2 km away in Dresden center
        val json = """
            {
              "spots": [
                {
                  "title": "Dresden Frauenkirche",
                  "locationName": "Neumarkt",
                  "category": "historical",
                  "description": "Reconstructed Baroque landmark.",
                  "latitude": 51.0519,
                  "longitude": 13.7415,
                  "distanceText": "350m away"
                }
              ]
            }
        """.trimIndent()

        val spots = service.parseSpotsJson(json, userLat, userLng, radiusMeters)

        assertEquals(1, spots.size)
        assertEquals("Dresden Frauenkirche", spots.first().title)
        assertTrue(spots.first().latitude in 51.0..51.1)
    }

    @Test
    fun parseSpotsJson_strictlyRejectsSpotOutsideSmallRadiusWithout3kmInflation() {
        val service = GeminiSpotService()
        val userLat = 38.7112
        val userLng = -9.1423
        val radiusMeters = 500 // 500m radius (tolerance threshold = 575m)

        // Spot located ~1.4km away (lat diff ~0.0128)
        val json = """
            {
              "spots": [
                {
                  "title": "Within Radius Cafe",
                  "locationName": "Baixa",
                  "category": "restaurant",
                  "description": "Close cafe 200m away.",
                  "latitude": 38.7125,
                  "longitude": -9.1420,
                  "distanceText": "200m away"
                },
                {
                  "title": "Outside 500m Radius Park",
                  "locationName": "Parque Eduardo VII",
                  "category": "touristic",
                  "description": "Park located ~1.4km away.",
                  "latitude": 38.7240,
                  "longitude": -9.1423,
                  "distanceText": "1.4km away"
                }
              ]
            }
        """.trimIndent()

        val spots = service.parseSpotsJson(json, userLat, userLng, radiusMeters)

        assertEquals("Should strictly reject 1.4km spot under 500m requested radius", 1, spots.size)
        assertEquals("Within Radius Cafe", spots.first().title)
    }
}

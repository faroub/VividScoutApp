package com.example.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeminiSpotServiceTest {

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
}

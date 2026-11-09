package com.example.data

import com.example.data.local.ScoutSpotEntity
import com.example.data.local.isDemoFallbackSpot
import com.example.data.network.OverpassRetrofitClient
import com.example.data.network.model.OverpassElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassRetrofitClientTest {

    private val client = OverpassRetrofitClient()

    @Test
    fun buildQuery_containsRequiredOsmNodeFilters() {
        val lat = 38.7112
        val lng = -9.1423
        val radius = 1000

        val query = client.buildQuery(lat, lng, radius)

        assertTrue("Query should contain viewpoint node filter", query.contains("""node["tourism"="viewpoint"]"""))
        assertTrue("Query should contain bakery node filter", query.contains("""node["shop"="bakery"]"""))
        assertTrue("Query should contain shortcut/steps node filter", query.contains("""node["highway"="steps"]"""))
        assertTrue("Query should contain latitude", query.contains("38.7112"))
        assertTrue("Query should contain longitude", query.contains("-9.1423"))
    }

    @Test
    fun buildQuery_generatesCategorySpecificFilters() {
        val lat = 38.7112
        val lng = -9.1423
        val radius = 1500

        val diningQuery = client.buildQuery(lat, lng, radius, "dining")
        assertTrue("Dining query should target restaurant amenity", diningQuery.contains("""node["amenity"="restaurant"]"""))

        val eventsQuery = client.buildQuery(lat, lng, radius, "events")
        assertTrue("Events query should target museum tourism", eventsQuery.contains("""node["tourism"="museum"]"""))
    }

    @Test
    fun mapElementsToEntities_correctlyCategorizesViewpointBakeryAndShortcut() {
        val elements = listOf(
            OverpassElement(
                type = "node",
                id = 101L,
                lat = 38.7120,
                lon = -9.1400,
                tags = mapOf("tourism" to "viewpoint", "name" to "Miradouro Panoramico")
            ),
            OverpassElement(
                type = "node",
                id = 102L,
                lat = 38.7115,
                lon = -9.1410,
                tags = mapOf("shop" to "bakery", "name" to "Padaria Central")
            ),
            OverpassElement(
                type = "node",
                id = 103L,
                lat = 38.7100,
                lon = -9.1430,
                tags = mapOf("highway" to "steps", "name" to "Escadinhas da Bica")
            )
        )

        val spots = client.mapElementsToEntities(elements, 38.7112, -9.1423)

        assertEquals(3, spots.size)

        // Viewpoint
        assertEquals("osm_node_101", spots[0].id)
        assertEquals("Miradouro Panoramico", spots[0].title)
        assertEquals("sights", spots[0].category)

        // Bakery
        assertEquals("osm_node_102", spots[1].id)
        assertEquals("Padaria Central", spots[1].title)
        assertEquals("eats", spots[1].category)

        // Shortcut
        assertEquals("osm_node_103", spots[2].id)
        assertEquals("Escadinhas da Bica", spots[2].title)
        assertEquals("shortcut", spots[2].category)
    }

    @Test
    fun mapElementsWithDiagnostics_capturesDroppedNodesAndReasons() {
        val elements = listOf(
            OverpassElement(
                type = "node",
                id = 201L,
                lat = 38.7120,
                lon = -9.1400,
                tags = mapOf("tourism" to "viewpoint", "name" to "Valid Spot")
            ),
            OverpassElement(
                type = "node",
                id = 202L,
                lat = null,
                lon = null,
                tags = mapOf("tourism" to "viewpoint", "name" to "Missing Coords Spot")
            )
        )

        val (mapped, dropReasons) = client.mapElementsWithDiagnostics(elements, 38.7112, -9.1423)

        assertEquals(1, mapped.size)
        assertEquals("Valid Spot", mapped[0].title)
        assertEquals(1, dropReasons.size)
        assertTrue("Drop reason should indicate missing coordinates", dropReasons[0].contains("Missing latitude/longitude"))
    }

    @Test
    fun mapElementsWithDiagnostics_discardsNodesExceeding50kmRadius() {
        val userLat = 38.7112
        val userLng = -9.1423

        val elements = listOf(
            // Nearby spot (~100m away)
            OverpassElement(
                type = "node",
                id = 301L,
                lat = 38.7120,
                lon = -9.1420,
                tags = mapOf("tourism" to "viewpoint", "name" to "Nearby Viewpoint")
            ),
            // Distant spot (>4,000 km away - e.g. New York 40.7128, -74.0060)
            OverpassElement(
                type = "node",
                id = 302L,
                lat = 40.7128,
                lon = -74.0060,
                tags = mapOf("tourism" to "viewpoint", "name" to "Distant NYC Spot")
            )
        )

        val (mapped, dropReasons) = client.mapElementsWithDiagnostics(elements, userLat, userLng)

        assertEquals(1, mapped.size)
        assertEquals("Nearby Viewpoint", mapped[0].title)
        assertEquals(1, dropReasons.size)
        assertTrue("Drop reason should log 50 km safety radius limit", dropReasons[0].contains("exceeds 50 km safety radius limit"))
    }

    @Test
    fun mapElementsWithDiagnostics_dropsUnnamedElements() {
        val elements = listOf(
            OverpassElement(
                type = "node",
                id = 401L,
                lat = 38.7120,
                lon = -9.1420,
                tags = mapOf("tourism" to "viewpoint") // No name tag
            )
        )

        val (mapped, dropReasons) = client.mapElementsWithDiagnostics(elements, 38.7112, -9.1423)

        assertEquals(0, mapped.size)
        assertEquals(1, dropReasons.size)
        assertTrue("Drop reason should indicate missing official name tag", dropReasons[0].contains("Missing official name tag"))
    }

    @Test
    fun generateNearbyFallbackSpots_marksEverySpotAsIllustrativeDemo() {
        val spots = client.generateNearbyFallbackSpots(38.7112, -9.1423, 6)

        assertEquals(6, spots.size)
        spots.forEach { spot ->
            assertTrue(
                "Every generated fallback spot must be classifiable as a demo record",
                spot.isDemoFallbackSpot
            )
            assertTrue("Demo ids must carry the fallback_ machine prefix", spot.id.startsWith("fallback_"))
            assertEquals(
                "The visible badge must say it is an offline demo, not a verified place",
                "Offline Demo Spot",
                spot.statusBadge
            )
            assertTrue("tagsRaw must tag the record as an offline demo", spot.tagsRaw.contains("Offline Demo"))
            assertTrue(
                "The description must disclose the illustrative nature",
                spot.description.contains("Illustrative demo spot", ignoreCase = true)
            )
            assertEquals(
                "Demo records are attributed to the offline fallback generator, not a person",
                "@offline_fallback",
                spot.contributor
            )
            assertFalse(
                "Demo records must never enter the offline-draft upload queue",
                spot.isOfflineDraft
            )
            assertTrue(
                "Demo coordinates must be placed near the requested center",
                com.example.data.location.LocationTracker.distanceBetween(
                    38.7112, -9.1423, spot.latitude, spot.longitude
                ) < 2000f
            )
        }
    }

    @Test
    fun mapElementsToEntities_realOsmSpotsAreNotClassifiedAsDemo() {
        val elements = listOf(
            OverpassElement(
                type = "node",
                id = 501L,
                lat = 38.7120,
                lon = -9.1400,
                tags = mapOf("tourism" to "viewpoint", "name" to "Miradouro Real")
            )
        )

        val spots = client.mapElementsToEntities(elements, 38.7112, -9.1423)

        assertEquals(1, spots.size)
        assertFalse("A real mapped OSM node must not be classified as a demo spot", spots[0].isDemoFallbackSpot)
        assertEquals("Live OSM Scout", spots[0].statusBadge)
    }

    @Test
    fun generateNearbyFallbackSpots_differentNamesAndCoordinatesGetDistinctIds() {
        val spots = client.generateNearbyFallbackSpots(38.7112, -9.1423, 6)

        val ids = spots.map { it.id }
        assertEquals("Every spot in a batch names and places differently — ids must be unique", 6, ids.toSet().size)

        // The same spot titles generated around a center 5 m away: coordinates differ,
        // so ids must differ as well (the old (lat * 10000).toInt() quantization could collide here).
        val nearby = client.generateNearbyFallbackSpots(38.71125, -9.14235, 6)
        assertEquals(
            "Nearby generation must not share ids with the first batch",
            0,
            (ids.toSet() intersect nearby.map { it.id }.toSet()).size
        )
    }

    @Test
    fun generateNearbyFallbackSpots_sameInputProducesSameIdsAcrossRuns() {
        val first = client.generateNearbyFallbackSpots(38.7112, -9.1423, 6).map { it.id }
        val second = client.generateNearbyFallbackSpots(38.7112, -9.1423, 6).map { it.id }

        assertEquals(
            "Re-generating the same fallback batch must reproduce identical ids (stable, dedup-safe)",
            first,
            second
        )
    }

    @Test
    fun generateNearbyFallbackSpots_negativeCoordinatesProduceValidIds() {
        val spots = client.generateNearbyFallbackSpots(-33.8688, 151.2093, 3)

        assertEquals(3, spots.size)
        spots.forEach { spot ->
            assertTrue("Southern-hemisphere ids follow the same scheme", spot.id.startsWith("fallback_"))
            assertEquals("fallback_".length + 16, spot.id.length)
            assertTrue("The 16-char id suffix is lowercase hex", spot.id.removePrefix("fallback_").matches(Regex("^[0-9a-f]{16}$")))
            assertTrue("Coordinates stay in the requested hemisphere", spot.latitude < 0)
        }
    }

    @Test
    fun overpassDiagnosticLog_calculatesMetricsCorrectly() {
        val log = com.example.data.network.OverpassDiagnosticLog(
            mirrorUrl = "https://overpass-api.de/",
            httpStatusCode = 200,
            durationMs = 245,
            rawJson = """{"elements":[{"id":1}]}""",
            rawElementsCount = 1,
            mappedEntitiesCount = 1,
            droppedElementsCount = 0,
            isFallbackTriggered = false
        )

        assertTrue(log.isSuccess)
        assertEquals(200, log.httpStatusCode)
        assertEquals(245L, log.durationMs)
        assertEquals(1, log.mappedEntitiesCount)
    }

    @Test
    fun egressEndpoints_onlyTheOfficialOverpassServerIsUsed() {
        // User query areas must never reach third-party mirrors (maps.mail.ru,
        // overpass.kumi.systems, ...): each recipient would see the queried location
        // without any backend App Check, rate limiting, or privacy disclosure.
        assertEquals(
            "Only the official Overpass endpoint may receive outgoing queries",
            listOf("https://overpass-api.de/"),
            client.mirrorBaseUrls
        )
    }

    @Test
    fun scraperEgressEndpoints_onlyTheOfficialOverpassServerIsUsed() {
        assertEquals(
            "The scraper must only call the official Overpass interpreter endpoint",
            listOf("https://overpass-api.de/api/interpreter"),
            com.example.data.network.OverpassScraperService().endpoints
        )
    }

    @Test
    fun buildQuery_carriesTheRoundedEgressAreaNeverTheExactFix() {
        // What actually leaves the device: the query embeds the coarse grid cell, not
        // the caller's coordinates, while the local mapping keeps exact values.
        val query = client.buildQuery(38.71, -9.15, 1500 + 1574)

        assertTrue("Rounded latitude is part of the sent query", query.contains("38.71"))
        assertTrue("Rounded longitude is part of the sent query", query.contains("-9.15"))
        assertTrue("Expanded radius is part of the sent query", query.contains("3074"))
        assertFalse(
            "The over-precise original coordinates must not appear in the sent query",
            query.contains("38.7112") || query.contains("-9.1423")
        )
    }
}

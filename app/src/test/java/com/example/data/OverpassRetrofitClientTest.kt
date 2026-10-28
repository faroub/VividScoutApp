package com.example.data

import com.example.data.network.OverpassRetrofitClient
import com.example.data.network.model.OverpassElement
import org.junit.Assert.assertEquals
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
}

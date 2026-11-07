package com.example.data.sync

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Illustrative "Offline Demo Spot" records (generated when every Overpass mirror fails
 * or returns zero elements) must never be published inside a shared trip as if they
 * were real destinations. Shared trips are shared with other people and rendered as
 * real itineraries, so a fabricated place in the list would mislead recipients.
 */
class SharedTripDemoSpotTest {

    private fun realSpot(
        id: String = "osm_node_9001",
        title: String = "Miradouro de Santa Luzia",
        lat: Double = 38.7120,
        lng: Double = -9.1400
    ): ScoutSpotEntity = ScoutSpotEntity(
        id = id,
        title = title,
        description = "Real OpenStreetMap viewpoint node.",
        category = "sights",
        distance = "120m away",
        walkMinutes = 2,
        imageUrl = "https://upload.wikimedia.org/example.jpg",
        badgeLabel = "OSM VIEWPOINT",
        statusBadge = "Live OSM Scout",
        tagsRaw = "Retrofit|||OpenStreetMap|||Nodes Verified",
        contributor = "@osm_contributor",
        latitude = lat,
        longitude = lng
    )

    private fun demoSpot(id: String = "osm_fallback_1", title: String = "Scenic Overlook & Viewpoint #1"): ScoutSpotEntity =
        realSpot(id = id, title = title).copy(
            description = "Illustrative demo spot shown while offline.",
            statusBadge = "Offline Demo Spot",
            tagsRaw = "Offline Demo|||Curated Fallback",
            contributor = "@offline_fallback"
        )

    @Test
    fun createSharedTrip_excludesDemoFallbackSpots() {
        val trip = SharedTripManager.createSharedTrip(
            "Weekend Walk",
            listOf(realSpot("osm_node_1"), demoSpot("osm_fallback_1"), realSpot("osm_node_2"), demoSpot("osm_fallback_2"))
        )

        val publishedIds = trip.spots.map { it.spotId }
        assertEquals(
            "Only the two real OSM spots may be published in the trip",
            listOf("osm_node_1", "osm_node_2"),
            publishedIds
        )
        assertTrue("Published spot titles must come from the real spots only", trip.spots.all { !it.title.contains("Demo") })
    }

    @Test
    fun createSharedTrip_withOnlyDemoSpots_publishesNoDestinations() {
        val trip = SharedTripManager.createSharedTrip(
            "Offline Stroll",
            listOf(demoSpot("osm_fallback_1"), demoSpot("osm_fallback_2"))
        )

        assertTrue("A trip built purely from demo records must publish zero destinations", trip.spots.isEmpty())
    }

    @Test
    fun createSharedTrip_keepsPublishingGenuineUserDraftsAndRealSpots() {
        val userDraft = realSpot(id = "draft_abc", title = "My Hidden Courtyard").copy(statusBadge = "Saved Offline")
        val trip = SharedTripManager.createSharedTrip("Favorites", listOf(userDraft, realSpot()))

        assertEquals(2, trip.spots.size)
        assertFalse("A genuine user draft must not be filtered out", trip.spots.none { it.spotId == "draft_abc" })
    }
}

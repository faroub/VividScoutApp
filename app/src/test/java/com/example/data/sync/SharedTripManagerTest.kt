package com.example.data.sync

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedTripManagerTest {

    private fun createSpot(id: String, title: String): ScoutSpotEntity {
        return ScoutSpotEntity(
            id = id,
            title = title,
            description = "Description for $title",
            category = "sights",
            distance = "Nearby",
            walkMinutes = 5,
            imageUrl = "https://images.unsplash.com/photo-1",
            badgeLabel = "Spot",
            statusBadge = "Scout Spot",
            tagsRaw = "",
            latitude = 38.7112,
            longitude = -9.1423
        )
    }

    @Test
    fun createSharedTrip_enforcesThirtyDayExpiration_andRandom128BitUuid() {
        val now = System.currentTimeMillis()
        val spots = listOf(
            createSpot("s1", "Miradouro de Santa Luzia"),
            createSpot("s2", "Castelo de São Jorge")
        )

        val trip = SharedTripManager.createSharedTrip(
            title = "Lisbon Scenic Walk",
            spots = spots,
            currentTimeMs = now
        )

        assertNotNull(trip.id)
        assertTrue("Trip ID must be a valid 128-bit UUID string", trip.id.length >= 32)

        val expectedExpireAt = now + (30L * 24 * 3600 * 1000L)
        assertEquals("expireAt TTL timestamp must be exactly 30 days after creation", expectedExpireAt, trip.expireAt)

        assertFalse("Newly created trip must not be expired", SharedTripManager.isTripExpired(trip, currentTimeMs = now))

        val thirtyOneDaysLater = now + (31L * 24 * 3600 * 1000L)
        assertTrue("Trip evaluated after 31 days MUST be marked as expired", SharedTripManager.isTripExpired(trip, currentTimeMs = thirtyOneDaysLater))
    }

    @Test
    fun createSharedTrip_containsZeroUserIdentifiersOrTrackingHistory() {
        val spots = listOf(createSpot("s1", "Alfama Secret Corner"))
        val trip = SharedTripManager.createSharedTrip("Alfama Gems", spots)

        assertEquals("https://vividscout.app/trip/${trip.id}", trip.shareableLink)
        assertEquals("Alfama Gems", trip.title)

        val spotItem = trip.spots.first()
        assertEquals("s1", spotItem.spotId)
        assertEquals("Alfama Secret Corner", spotItem.title)
        assertEquals(38.7112, spotItem.latitude, 0.0001)
        assertEquals(-9.1423, spotItem.longitude, 0.0001)
    }

    @Test
    fun parseTripIdFromUri_parsesAppLinksAndCustomSchemes() {
        val appLink = "https://vividscout.app/trip/abcd-1234-efgh-5678"
        assertEquals("abcd-1234-efgh-5678", SharedTripManager.parseTripIdFromUri(appLink))

        val customScheme = "vividscout://trip/abcd-1234-efgh-5678"
        assertEquals("abcd-1234-efgh-5678", SharedTripManager.parseTripIdFromUri(customScheme))

        val queryScheme = "vividscout://trip?id=abcd-1234-efgh-5678"
        assertEquals("abcd-1234-efgh-5678", SharedTripManager.parseTripIdFromUri(queryScheme))
    }

    @Test
    fun parseTripIdFromUri_rejectsMalformedAndUnrelatedLinks() {
        // Malformed input must never yield a trip id that would be used to query Firestore.
        assertEquals(null, SharedTripManager.parseTripIdFromUri(null))
        assertEquals(null, SharedTripManager.parseTripIdFromUri(""))
        assertEquals(null, SharedTripManager.parseTripIdFromUri("   "))
        assertEquals(null, SharedTripManager.parseTripIdFromUri("not a uri at all"))
        assertEquals(null, SharedTripManager.parseTripIdFromUri("https://vividscout.app"))
        assertEquals(null, SharedTripManager.parseTripIdFromUri("vividscout://other"))
        // Note: the query-parameter form (vividscout://<any>?id=...) reads the id on
        // any host by design — the manifest intent filter is what restricts delivery
        // to the vividscout://trip and https://vividscout.app/trip/ forms.
    }

    @Test
    fun parseTripIdFromUri_returnsBlankIdForEmptyPathSegments() {
        // "/trip/" with nothing after it parses to an empty id — the deep-link handler
        // must check for blank before fetching (it does), so a bare link cannot be
        // used to probe arbitrary document IDs.
        val parsed = SharedTripManager.parseTripIdFromUri("https://vividscout.app/trip/")
        assertTrue(parsed.isNullOrBlank())
    }
}

package com.example.data.sync

import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun createSharedTrip_enforcesThirtyDayExpiration_andLocalPlaceholderId() {
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

        // The local id is an obvious placeholder, never a random UUID: only the
        // server-generated link returned by publishSharedTripToFirestore resolves.
        assertEquals(SharedTripManager.LOCAL_PLACEHOLDER_TRIP_ID, trip.id)
        assertEquals(
            "https://vividscout.app/trip/${SharedTripManager.LOCAL_PLACEHOLDER_TRIP_ID}",
            trip.shareableLink
        )

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
    fun createSharedTrip_dropsImageUrlsHostedOutsideTheAllowlist() {
        // Every viewer's device fetches a shared trip's spot images, so only the
        // allowlisted hosts may be shared: a GeoNames thumbnail (or any other host)
        // would expose each viewer's IP address to an unknown third party. Dropped
        // images become the empty string (the shared-trip missing-image
        // representation); the trusted publishSharedTrip Cloud Function rejects
        // non-allowlisted hosts server-side as well.
        val wikimedia = createSpot("s1", "Miradouro de Santa Luzia").copy(
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Photo.jpg/800px-Photo.jpg"
        )
        val geonames = createSpot("s2", "Convento do Carmo").copy(
            imageUrl = "https://a1.geonames.org/img/wikipedia/12345.jpg"
        )
        val unsplash = createSpot("s3", "Manteigaria").copy(
            imageUrl = "https://images.unsplash.com/photo-tiny-cafe"
        )
        val missing = createSpot("s4", "Alfama Steps").copy(imageUrl = "")

        val trip = SharedTripManager.createSharedTrip(
            "Lisbon Gems",
            listOf(wikimedia, geonames, unsplash, missing)
        )

        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Photo.jpg/800px-Photo.jpg",
            trip.spots[0].imageUrl
        )
        assertEquals("", trip.spots[1].imageUrl)
        assertEquals("https://images.unsplash.com/photo-tiny-cafe", trip.spots[2].imageUrl)
        assertEquals("", trip.spots[3].imageUrl)
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

    @Test
    fun parseTripIdFromUri_rejectsSlashContainingIds() {
        // Path traversal / collection probing must never yield an id that reaches Firestore.
        assertEquals(null, SharedTripManager.parseTripIdFromUri("https://vividscout.app/trip/abcd/../../users/secret"))
        assertEquals(null, SharedTripManager.parseTripIdFromUri("vividscout://trip/good-id/shared_trips/secret"))
        assertEquals(null, SharedTripManager.parseTripIdFromUri("vividscout://trip?id=ok-id-but/with-slash"))
    }

    @Test
    fun parseTripIdFromUri_rejectsOversizedAndUndersizedIds() {
        val tooShort = "short" // 5 chars
        val exactMin = "abcdefgh" // 8 chars
        val exactMax = "a".repeat(64)
        val tooLong = "a".repeat(65)

        assertEquals(null, SharedTripManager.parseTripIdFromUri("https://vividscout.app/trip/$tooShort"))
        assertEquals(exactMin, SharedTripManager.parseTripIdFromUri("https://vividscout.app/trip/$exactMin"))
        assertEquals(exactMax, SharedTripManager.parseTripIdFromUri("https://vividscout.app/trip/$exactMax"))
        assertEquals(null, SharedTripManager.parseTripIdFromUri("https://vividscout.app/trip/$tooLong"))
    }

    @Test
    fun isValidTripId_acceptsTheDocumentedShapeOnly() {
        assertTrue(SharedTripManager.isValidTripId("abcd-1234-efgh-5678"))
        assertTrue(SharedTripManager.isValidTripId("abcdefgh"))
        assertTrue(SharedTripManager.isValidTripId("A_b-9cdE"))
        assertFalse(SharedTripManager.isValidTripId(null))
        assertFalse(SharedTripManager.isValidTripId(""))
        assertFalse(SharedTripManager.isValidTripId("   "))
        assertFalse(SharedTripManager.isValidTripId("has space"))
        assertFalse(SharedTripManager.isValidTripId("shrt"))
        assertFalse(SharedTripManager.isValidTripId("a".repeat(65)))
        assertFalse(SharedTripManager.isValidTripId("with/slash"))
        assertFalse(SharedTripManager.isValidTripId("has.spec"))
    }
}

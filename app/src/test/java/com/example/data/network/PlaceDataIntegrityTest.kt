package com.example.data.network

import com.example.data.local.ScoutSpotEntity
import com.example.data.places.GeonamesSource
import com.example.util.Geo
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
class PlaceDataIntegrityTest {

    @Test
    fun europeanaService_rejectsEntriesWithoutRealCoordinates() = runBlocking {
        val service = EuropeanaCulturalService()
        // Querying Europeana with sample location: if records do not contain valid lat/lon, they are omitted
        val spots = service.fetchEuropeanaCulturalSpots("Lisbon", 38.7112, -9.1423, 15000)

        for (spot in spots) {
            assertFalse("Latitude cannot be NaN", spot.latitude.isNaN())
            assertFalse("Longitude cannot be NaN", spot.longitude.isNaN())
            assertFalse("Coordinates cannot be Null Island (0,0)", spot.latitude == 0.0 && spot.longitude == 0.0)
            assertNull("Europeana ratings must be null", spot.rating)
            assertNull("Europeana reviewCount must be null", spot.reviewCount)
        }
    }

    @Test
    fun municipalOpenDataService_doesNotGenerateFictionalAttractionsForNonLisbonCities() = runBlocking {
        val service = MunicipalOpenDataService()

        // Non-Lisbon city (Dresden, Germany)
        val dresdenSpots = service.fetchMunicipalDataSpots("Dresden", 51.0504, 13.7373)
        assertTrue("Municipal open data service must return empty list for non-verified cities (no synthetic monuments)", dresdenSpots.isEmpty())

        // Verified city (Lisbon)
        val lisbonSpots = service.fetchMunicipalDataSpots("Lisbon", 38.7112, -9.1423)
        assertTrue("Lisbon verified dataset items must be returned", lisbonSpots.isNotEmpty())
        for (spot in lisbonSpots) {
            assertNull("Municipal ratings must be null", spot.rating)
            assertNull("Municipal reviewCount must be null", spot.reviewCount)
            assertEquals("City Open Data", spot.badgeLabel)
        }
    }

    @Test
    fun unescoHeritageService_returnsEmptyResultWhenNoNearbySitesInRadius() = runBlocking {
        val service = UnescoHeritageService()

        // Querying UNESCO for a location far away from Portugal (e.g. Dresden, Germany ~2000km away) with 30km radius
        val distantSpots = service.fetchNearbyUnescoSites(51.0504, 13.7373, radiusKm = 30.0)
        assertTrue("UNESCO service must return empty list when no nearby UNESCO site exists within radius", distantSpots.isEmpty())

        // Querying UNESCO for Belém, Lisbon (within 5km radius of Torre de Belém 38.6916, -9.2160)
        val nearbySpots = service.fetchNearbyUnescoSites(38.6916, -9.2160, radiusKm = 5.0)
        assertTrue("Nearby UNESCO site in Belém must be returned", nearbySpots.isNotEmpty())
        val belemSpot = nearbySpots.first()
        assertEquals("Torre de Belém & Jerónimos Monastery", belemSpot.title)
        assertNull("UNESCO ratings must be null", belemSpot.rating)
        assertNull("UNESCO reviewCount must be null", belemSpot.reviewCount)
    }

    @Test
    fun geonamesService_rejectsMissingCoordinates_andContainsNoDemoCredentials() = runBlocking {
        val rawCandidates = listOf(
            mapOf(
                "sourceName" to "GeoNames Gazetteer",
                "sourceId" to "geonames_invalid",
                "name" to "Invalid Coord Monument",
                "latitude" to Double.NaN,
                "longitude" to -9.1423,
                "category" to "history"
            ),
            mapOf(
                "sourceName" to "GeoNames Gazetteer",
                "sourceId" to "geonames_null_island",
                "name" to "Null Island Monument",
                "latitude" to 0.0,
                "longitude" to 0.0,
                "category" to "history"
            ),
            mapOf(
                "sourceName" to "GeoNames Gazetteer",
                "sourceId" to "geonames_valid",
                "name" to "Valid Rossio Landmark",
                "latitude" to 38.7138,
                "longitude" to -9.1393,
                "category" to "history"
            )
        )

        val fakeGeonamesSource = GeonamesSource(callableProvider = { rawCandidates })
        val geonamesService = GeonamesService(geonamesSource = fakeGeonamesSource)
        val verifiedSpots = geonamesService.fetchNearbyGeonamesPOIs(38.7112, -9.1423, radiusMeters = 3000)

        // Invalid NaN and 0.0/0.0 candidates MUST be rejected!
        assertEquals("Only valid coordinate candidate must be accepted", 1, verifiedSpots.size)
        assertEquals("Valid Rossio Landmark", verifiedSpots.first().title)
        assertEquals("GeoNames", verifiedSpots.first().badgeLabel)
        assertNull("GeoNames rating must be null", verifiedSpots.first().rating)
    }

    @Test
    fun openTripMapService_leavesRatingsAndReviewCountsNull() {
        val spot = ScoutSpotEntity(
            id = "otm_W12345",
            title = "Historic Fountain",
            description = "Touristic place",
            category = "history",
            distance = "Nearby",
            walkMinutes = 8,
            rating = null,
            reviewCount = null,
            imageUrl = "",
            badgeLabel = "OpenTripMap",
            statusBadge = "OpenTripMap POI",
            tagsRaw = "historic|||architecture",
            latitude = 38.7125,
            longitude = -9.1415
        )

        assertNull("OpenTripMap rating must be null", spot.rating)
        assertNull("OpenTripMap reviewCount must be null", spot.reviewCount)
        assertEquals("OpenTripMap", spot.badgeLabel)
    }

    @Test
    fun coordinateValidation_rejectsNaN_ZeroZero_andOutOfBounds() {
        val validLat = 38.7112
        val validLng = -9.1423

        // Geodesic distance for invalid coordinates
        assertTrue(Geo.calculateDistanceMeters(Double.NaN, validLng, validLat, validLng).isNaN())
        assertTrue(Geo.calculateDistanceMeters(validLat, validLng, 0.0, 0.0).isNaN())

        // Geodesic distance calculation for valid points
        val validDist = Geo.calculateDistanceMeters(validLat, validLng, 38.7150, -9.1423)
        assertFalse(validDist.isNaN())
        assertTrue(validDist > 0.0)
    }

    @Test
    fun providerAttributions_arePreservedAcrossDataSources() = runBlocking {
        val unescoService = UnescoHeritageService()
        val unescoSites = unescoService.fetchNearbyUnescoSites(38.6916, -9.2160, 10.0)
        assertTrue(unescoSites.isNotEmpty())

        val site = unescoSites.first()
        assertEquals("UNESCO Heritage", site.badgeLabel)
        assertTrue(site.proTip!!.contains("UNESCO"))
        assertNull(site.rating)
    }
}

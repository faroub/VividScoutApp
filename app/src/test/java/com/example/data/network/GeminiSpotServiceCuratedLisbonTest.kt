package com.example.data.network

import com.example.data.location.LocationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the Lisbon curated fallback catalog: landmarks keep their verified
 * real-world coordinates regardless of the user's location, distances are real
 * geodesics, results are restricted to the requested radius (coverage limited to
 * central Lisbon), and invalid request coordinates yield an empty result instead
 * of invented spots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeminiSpotServiceCuratedLisbonTest {

    private val service = GeminiSpotService()

    // OSM-verified landmark coordinates (Nominatim).
    private val santaLuzia = 38.7117382 to -9.1301295
    private val manteigaria = 38.7107638 to -9.1440621
    private val castelo = 38.7139258 to -9.1334830
    private val bica = 38.7097584 to -9.1462053
    private val carmo = 38.7120060 to -9.1405862
    private val taberna = 38.7101395 to -9.1435869
    private val catalog = listOf(santaLuzia, manteigaria, castelo, bica, carmo, taberna)

    @Test
    fun centralLisbonUser_returnsAllVerifiedLandmarksAtTheirRealCoordinates() {
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "all")

        assertEquals(6, spots.size)
        val returnedCoords = spots.map { it.latitude to it.longitude }
        catalog.forEach { verified ->
            assertTrue(
                "Landmark at $verified must appear at its verified real-world coordinate",
                returnedCoords.contains(verified)
            )
        }
    }

    @Test
    fun landmarkCoordinatesNeverMoveWithTheUser() {
        val fromChiado = service.generateCuratedVicinitySpots(38.7112, -9.1423, "all")
        // Same landmarks requested from a different location inside the coverage area.
        val fromAlfama = service.generateCuratedVicinitySpots(38.7120, -9.1300, "all")

        val castleFromChiado = fromChiado.first { it.title.contains("Castelo") }
        val castleFromAlfama = fromAlfama.first { it.title.contains("Castelo") }

        assertEquals(castelo.first, castleFromChiado.latitude, 0.0)
        assertEquals(castelo.second, castleFromChiado.longitude, 0.0)
        assertEquals(castelo.first, castleFromAlfama.latitude, 0.0)
        assertEquals(castelo.second, castleFromAlfama.longitude, 0.0)
    }

    @Test
    fun userStandingAtLandmarkSeesZeroDistanceToIt() {
        val spots = service.generateCuratedVicinitySpots(santaLuzia.first, santaLuzia.second, "all")

        val viewpoint = spots.first { it.title.contains("Santa Luzia") }
        assertEquals("0m away", viewpoint.distance)
        assertEquals(1, viewpoint.walkMinutes)
    }

    @Test
    fun userAtCasteloSaoJorge_returnsCastleAtItsOwnLocation() {
        val spots = service.generateCuratedVicinitySpots(castelo.first, castelo.second, "all")

        val castle = spots.first { it.title.contains("Castelo") }
        assertEquals(castelo.first, castle.latitude, 0.0)
        assertEquals(castelo.second, castle.longitude, 0.0)
        assertEquals("0m away", castle.distance)
    }

    @Test
    fun distancesMatchRealGeodesicCalculation() {
        val userLat = 38.7112
        val userLng = -9.1423
        val spots = service.generateCuratedVicinitySpots(userLat, userLng, "all")

        val castle = spots.first { it.title.contains("Castelo") }
        val expectedMeters = LocationTracker.distanceBetween(userLat, userLng, castelo.first, castelo.second).toInt()
        assertEquals("${expectedMeters}m away", castle.distance)
        assertEquals((expectedMeters / 80f).toInt().coerceAtLeast(1), castle.walkMinutes)
    }

    @Test
    fun resultsAreSortedByRealDistance() {
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "all")
        val distances = spots.map { it.distance.filter { c -> c.isDigit() }.toInt() }

        assertEquals(distances, distances.sorted())
    }

    @Test
    fun userOutsideLisbon_receivesNoCuratedLandmarks() {
        // Sintra (~26 km from central Lisbon).
        val sintra = service.generateCuratedVicinitySpots(38.7979, -9.3905, "all")
        assertTrue("Sintra is outside the catalog coverage", sintra.isEmpty())

        // ~100 km east of Lisbon (Alentejo).
        val farAway = service.generateCuratedVicinitySpots(38.7112, -8.0, "all")
        assertTrue("A user ~100 km from Lisbon must not see Lisbon landmarks", farAway.isEmpty())
    }

    @Test
    fun landmarksOutsideTheRequestedRadiusAreExcluded() {
        // Standing at Praça Luís de Camões, asking for only 120 m: the nearest curated
        // landmark (Manteigaria) is ~370 m away, so nothing qualifies.
        val spots = service.generateCuratedVicinitySpots(38.7138, -9.1423, "all", radiusMeters = 120)
        assertTrue("No curated landmark within 120m must be returned", spots.isEmpty())

        // A sufficiently large radius still returns the catalog for a central user.
        val wide = service.generateCuratedVicinitySpots(38.7112, -9.1423, "all", radiusMeters = 2000)
        assertEquals(6, wide.size)
    }

    @Test
    fun invalidUserCoordinates_returnEmptyResult() {
        assertTrue(service.generateCuratedVicinitySpots(Double.NaN, -9.1423, "all").isEmpty())
        assertTrue(service.generateCuratedVicinitySpots(38.7112, Double.NaN, "all").isEmpty())
        assertTrue(service.generateCuratedVicinitySpots(0.0, 0.0, "all").isEmpty())
        assertTrue(service.generateCuratedVicinitySpots(95.0, -9.1423, "all").isEmpty())
        assertTrue(service.generateCuratedVicinitySpots(38.7112, 195.0, "all").isEmpty())
    }

    @Test
    fun curatedSpotsNeverPresentFabricatedSocialProof() {
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "all")
        assertTrue(spots.isNotEmpty())
        spots.forEach { spot ->
            assertEquals("CURATED FALLBACK", spot.statusBadge)
            assertNull("Curated spots must never present artificial ratings as real", spot.rating)
            assertNull("Curated reviewCount must be null", spot.reviewCount)
            assertTrue(
                "Distance must be a real geodesic value",
                spot.distance.matches(Regex("\\d+m away"))
            )
        }
    }
}

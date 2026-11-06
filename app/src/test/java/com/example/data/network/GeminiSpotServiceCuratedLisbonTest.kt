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

    // ------------------------------------------------------------------
    // Canonical category filtering (P6)
    // ------------------------------------------------------------------

    @Test
    fun eatsFilter_returnsOnlyDiningRecordsNormalizedToEats() {
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "eats", radiusMeters = 5000)
        assertTrue("eats filter must not return a restaurant-free catalog mix", spots.isNotEmpty())
        spots.forEach { spot ->
            assertEquals("eats", spot.category)
            assertTrue(spot.tagsRaw.split(",").any { it in listOf("bakery", "pastry", "dining", "seafood", "wine") })
        }
    }

    @Test
    fun historyFilter_returnsOnlyHistoricalRecords() {
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "history", radiusMeters = 5000)
        assertTrue("history filter must return the historical landmarks", spots.isNotEmpty())
        spots.forEach { spot -> assertEquals("history", spot.category) }
        assertTrue(spots.map { it.title }.contains("Castelo de São Jorge Ramparts"))
        assertTrue(spots.map { it.title }.contains("Convento do Carmo Ruins"))
    }

    @Test
    fun sightsFilter_returnsOnlySightseeingRecords() {
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "sights", radiusMeters = 5000)
        assertTrue("sights filter must return the touristic landmarks", spots.isNotEmpty())
        spots.forEach { spot -> assertEquals("sights", spot.category) }
        assertTrue(spots.map { it.title }.contains("Miradouro de Santa Luzia"))
    }

    @Test
    fun shortcutFilter_returnsNoCuratedResults() {
        // No curated catalog entry classifies as a shortcut; an empty list — never a
        // fabricated shortcut or the full catalog.
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "shortcut", radiusMeters = 5000)
        assertTrue("No curated shortcut entries exist", spots.isEmpty())
    }

    @Test
    fun categoryAliasesNormalizeToCanonicalIds() {
        // Raw aliases used by providers and the AI must map to the canonical ids.
        val restaurantAlias = service.generateCuratedVicinitySpots(38.7112, -9.1423, "restaurant", radiusMeters = 5000)
        assertTrue(restaurantAlias.isNotEmpty())
        assertTrue(restaurantAlias.all { it.category == "eats" })

        val historicAlias = service.generateCuratedVicinitySpots(38.7112, -9.1423, "historical", radiusMeters = 5000)
        assertTrue(historicAlias.isNotEmpty())
        assertTrue(historicAlias.all { it.category == "history" })

        val touristicAlias = service.generateCuratedVicinitySpots(38.7112, -9.1423, "touristic", radiusMeters = 5000)
        assertTrue(touristicAlias.isNotEmpty())
        assertTrue(touristicAlias.all { it.category == "sights" })
    }

    @Test
    fun allFilter_returnsTheFullInRadiusCatalog() {
        val spots = service.generateCuratedVicinitySpots(38.7112, -9.1423, "all", radiusMeters = 5000)
        assertEquals(6, spots.size)
    }

    @Test
    fun unmatchedCategoryDoesNotReturnTheEntireCatalog() {
        // An unknown category normalizes to "sights" (the app-wide convention), NOT to
        // the full catalog — and any requested category outside the radius is empty.
        val unknown = service.generateCuratedVicinitySpots(38.7112, -9.1423, "zebra-safari", radiusMeters = 5000)
        assertTrue("Unknown categories must never return the full catalog", unknown.size <= 2)
        assertTrue(unknown.all { it.category == "sights" })
    }
}

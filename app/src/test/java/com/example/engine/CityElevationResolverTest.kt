package com.example.engine

import com.example.data.offline.GuidedWalkMood
import com.example.data.offline.GuidedWalkPlannerEngine
import com.example.data.local.ScoutSpotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that LisbonElevationModel is served exclusively for central Lisbon —
 * the area actually covered by its surveyed topo anchors — and that every other
 * geographic region or city name resolves to no model at all.
 */
class CityElevationResolverTest {

    private fun makeSpot(id: String, lat: Double, lng: Double): ScoutSpotEntity = ScoutSpotEntity(
        id = id,
        title = "Test spot $id",
        description = "Description",
        category = "sights",
        distance = "Nearby",
        walkMinutes = 5,
        imageUrl = "",
        badgeLabel = "Spot",
        statusBadge = "Scout Spot",
        tagsRaw = "sights|||test",
        latitude = lat,
        longitude = lng
    )

    @Test
    fun centralLisbon_resolvesLisbonModel() {
        val model = CityElevationResolver.resolveModelForCity("Lisbon, Portugal", 38.7112, -9.1423)
        assertEquals(LisbonElevationModel, model)
        assertTrue(model!!.isSupported)
    }

    @Test
    fun alfamaDistrict_resolvesLisbonModel() {
        val model = CityElevationResolver.resolveModelForCity("Alfama", 38.7116, -9.1306)
        assertEquals(LisbonElevationModel, model)
    }

    @Test
    fun chiadoDistrict_resolvesLisbonModel() {
        val model = CityElevationResolver.resolveModelForCity("Chiado", 38.7107, -9.1402)
        assertEquals(LisbonElevationModel, model)
    }

    @Test
    fun belem_isOutsideAnchorCoverage_returnsNull() {
        // Belém is a Lisbon district ~5 km west of the westernmost surveyed anchor.
        val model = CityElevationResolver.resolveModelForCity("Belém", 38.6979, -9.2060)
        assertNull("Belém must not receive the central-Lisbon elevation model", model)
    }

    @Test
    fun belem_alternateSpelling_returnsNull() {
        val model = CityElevationResolver.resolveModelForCity("Belem", 38.6979, -9.2060)
        assertNull(model)
    }

    @Test
    fun sintra_returnsNull() {
        val model = CityElevationResolver.resolveModelForCity("Sintra", 38.7976, -9.3905)
        assertNull("Sintra must not receive the Lisbon elevation model", model)
    }

    @Test
    fun sintra_withinCoordinateBoxOfOldBehavior_stillReturnsNull() {
        // Regression: a broad 22 km x 28 km rectangle around Lisbon used to match
        // Sintra's coordinates. The anchor envelope is far smaller.
        val model = CityElevationResolver.resolveModelForCity(null, 38.7976, -9.3905)
        assertNull(model)
    }

    @Test
    fun dresden_returnsNull() {
        val model = CityElevationResolver.resolveModelForCity("Dresden, Germany", 51.0504, 13.7373)
        assertNull(model)
    }

    @Test
    fun unsupportedCity_returnsNull() {
        val model = CityElevationResolver.resolveModelForCity("Porto, Portugal", 41.1495, -8.6108)
        assertNull(model)
    }

    @Test
    fun missingCityName_lisbonCoordinates_resolvesLisbonModel() {
        // RoutePlannerSheet supplies no city name for "My Current GPS Location".
        val model = CityElevationResolver.resolveModelForCity(null, 38.7112, -9.1423)
        assertEquals(LisbonElevationModel, model)
    }

    @Test
    fun missingCityName_outsideLisbon_returnsNull() {
        val model = CityElevationResolver.resolveModelForCity("", 38.7976, -9.3905)
        assertNull(model)
    }

    @Test
    fun coordinateBoundary_justInsideCoverage_resolvesModel() {
        // Southern edge of the surveyed envelope plus the GPS tolerance is still covered.
        val model = CityElevationResolver.resolveModelForCity("Lisbon", LisbonElevationModel.minCoveredLat + 0.0001, -9.1368)
        assertNotNull(model)
    }

    @Test
    fun coordinateBoundary_justOutsideCoverage_returnsNull() {
        val model = CityElevationResolver.resolveModelForCity("Lisbon", LisbonElevationModel.minCoveredLat - 0.0001, -9.1368)
        assertNull("Coordinates just outside the anchor envelope must resolve to no model", model)
    }

    @Test
    fun invalidCoordinates_returnNull() {
        assertNull(CityElevationResolver.resolveModelForCity("Lisbon", Double.NaN, -9.1423))
        assertNull(CityElevationResolver.resolveModelForCity("Lisbon", 38.7112, Double.NaN))
        assertNull(CityElevationResolver.resolveModelForCity("Lisbon", 91.0, -9.1423))
        assertNull(CityElevationResolver.resolveModelForCity("Lisbon", 38.7112, -181.0))
        assertNull(CityElevationResolver.resolveModelForCity("Lisbon", 0.0, 0.0))
    }

    @Test
    fun missingElevationData_outsideCoverage_returnsNull() {
        // Direct queries about coordinates with no surveyed basis must not invent values.
        assertNull(LisbonElevationModel.getElevationForCoordinate(38.7976, -9.3905)) // Sintra
        assertNull(LisbonElevationModel.getElevationForCoordinate(38.6979, -9.2060)) // Belém
        assertNull(LisbonElevationModel.getElevationForCoordinate(51.0504, 13.7373)) // Dresden
    }

    @Test
    fun elevationProfile_outsideCoverageWaypoints_returnsNull() {
        // A loop whose waypoints leave the surveyed area has no verified terrain.
        val profile = LisbonElevationModel.buildElevationProfile(
            listOf(Pair(38.7112, -9.1423), Pair(38.6979, -9.2060))
        )
        assertNull(profile)
    }

    @Test
    fun guidedWalk_inSintraCityName_neverShowsLisbonElevationChart() {
        val sintraSpot = makeSpot("sintra_1", 38.7990, -9.3900)
        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 38.7976,
            originLng = -9.3905,
            startTitle = "Sintra National Palace",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(sintraSpot),
            cityName = "Sintra"
        )
        assertFalse(plan.waypoints.isEmpty())
        assertNull("Guided walks in Sintra must not carry a Lisbon elevation profile", plan.elevationProfile)
    }

    @Test
    fun guidedWalk_lisbonNameWithSintraCoordinates_showsNoChart() {
        // City identity AND geographic coverage must both validate.
        val plan = GuidedWalkPlannerEngine.planGuidedWalk(
            originLat = 38.7976,
            originLng = -9.3905,
            startTitle = "Start",
            targetDurationMinutes = 60,
            mood = GuidedWalkMood.SCENIC,
            availableSpots = listOf(makeSpot("s1", 38.7990, -9.3900)),
            cityName = "Lisbon"
        )
        assertFalse(plan.waypoints.isEmpty())
        assertNull(plan.elevationProfile)
    }
}

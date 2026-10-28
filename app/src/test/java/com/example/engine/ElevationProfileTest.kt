package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationProfileTest {

    @Test
    fun getElevationForCoordinate_reflectsLisbonTopography() {
        // Tagus riverfront should be low elevation (~2m - 6m)
        val riverElev = LisbonElevationModel.getElevationForCoordinate(38.7063, -9.1368)
        assertTrue("Riverfront elevation should be near sea level (< 10m), was $riverElev", riverElev < 10.0)

        // Bairro Alto / São Pedro de Alcântara should be elevated (> 60m)
        val alcantaraElev = LisbonElevationModel.getElevationForCoordinate(38.7150, -9.1443)
        assertTrue("São Pedro de Alcântara elevation should be elevated (> 60m), was $alcantaraElev", alcantaraElev > 60.0)

        // Castelo de São Jorge summit should be near top (> 85m)
        val castleElev = LisbonElevationModel.getElevationForCoordinate(38.7139, -9.1334)
        assertTrue("Castelo elevation should be high (> 85m), was $castleElev", castleElev > 85.0)
    }

    @Test
    fun buildElevationProfile_climbingRoute_computesAscentAndIntensity() {
        // Route from Praça do Comércio (4m) up to Largo do Carmo (50m)
        val trail = OfflineRouteEngine.calculateShortestAStarRoute(
            originLat = 38.7075,
            originLng = -9.1364,
            destLat = 38.7122,
            destLng = -9.1404,
            originName = "Praça do Comércio",
            destName = "Largo do Carmo"
        )

        val profile = trail.elevationProfile
        assertNotNull(profile)
        assertTrue("Should contain multiple sampled points", profile!!.points.size >= 5)
        assertTrue("Total ascent should be significant (> 25m)", profile.totalAscentMeters >= 25.0)
        assertTrue("Max elevation should be higher than min elevation", profile.maxElevationMeters > profile.minElevationMeters)
        assertTrue(
            "Intensity label should reflect incline",
            profile.intensityLabel.contains("Incline") || profile.intensityLabel.contains("Climb")
        )
    }

    @Test
    fun buildElevationProfile_flatRiverfrontRoute_labeledGentle() {
        // Flat promenade from Cais das Colunas to Ribeira das Naus along the river
        val flatPath = listOf(
            Pair(38.7063, -9.1368),
            Pair(38.7062, -9.1390),
            Pair(38.7061, -9.1415),
            Pair(38.7060, -9.1448)
        )

        val profile = LisbonElevationModel.buildElevationProfile(flatPath)
        assertNotNull(profile)
        assertTrue("Total ascent along riverbank should be very low (< 10m)", profile.totalAscentMeters < 10.0)
        assertEquals("Gentle Walk", profile.intensityLabel)
    }

    @Test
    fun buildElevationProfile_calculatesSlopeGradePercentage() {
        // Steep hill climb test points
        val steepPath = listOf(
            Pair(38.7075, -9.1364), // Riverfront
            Pair(38.7107, -9.1419), // Chiado
            Pair(38.7150, -9.1443)  // São Pedro de Alcântara
        )

        val profile = LisbonElevationModel.buildElevationProfile(steepPath)
        val hasPositiveGrade = profile.points.any { it.slopeGradePct > 2.0 }
        assertTrue("Climbing path should contain positive slope grades", hasPositiveGrade)
    }
}

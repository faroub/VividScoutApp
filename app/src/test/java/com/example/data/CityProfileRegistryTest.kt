package com.example.data

import com.example.data.model.CityProfileRegistry
import com.example.engine.NullElevationModel
import com.example.util.EmptyLandmarkCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityProfileRegistryTest {

    @Test
    fun `lisbon profile returns full elevation and landmark support`() {
        val lisbon = CityProfileRegistry.getProfileForCity("Lisbon, Portugal")

        assertTrue(lisbon.isCuratedSupported)
        assertTrue(lisbon.elevationModel.isSupported)
        assertTrue(lisbon.landmarkCatalog.isSupported)

        val samplePath = listOf(Pair(38.7112, -9.1423), Pair(38.7122, -9.1404))
        val elevationProfile = lisbon.elevationModel.buildElevationProfile(samplePath)
        assertNotNull(elevationProfile)

        val landmarks = lisbon.landmarkCatalog.landmarks
        assertTrue(landmarks.isNotEmpty())
    }

    @Test
    fun `unknown city returns honest unavailable state for elevation and landmarks`() {
        val berlin = CityProfileRegistry.getProfileForCity("Berlin, Germany")

        assertFalse(berlin.isCuratedSupported)
        assertFalse(berlin.elevationModel.isSupported)
        assertFalse(berlin.landmarkCatalog.isSupported)
        assertEquals(NullElevationModel, berlin.elevationModel)
        assertEquals(EmptyLandmarkCatalog, berlin.landmarkCatalog)

        val samplePath = listOf(Pair(52.5200, 13.4050), Pair(52.5210, 13.4060))
        val elevationProfile = berlin.elevationModel.buildElevationProfile(samplePath)
        assertNull("Elevation profile must be null for unknown city", elevationProfile)

        val landmarks = berlin.landmarkCatalog.landmarks
        assertTrue("Landmark catalog must be empty for unknown city", landmarks.isEmpty())
        assertNull("Landmark lookup must return null for unknown city", berlin.landmarkCatalog.findLandmark("Brandenburg Gate"))
    }
}

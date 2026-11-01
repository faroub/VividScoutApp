package com.example.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DefaultLocationConfigTest {

    @Test
    fun defaultLocationConfig_providesCentralizedFallbackCoordinates() {
        assertEquals(38.7112, DefaultLocationConfig.DEFAULT_LAT, 0.0001)
        assertEquals(-9.1423, DefaultLocationConfig.DEFAULT_LNG, 0.0001)
        assertEquals("Select City", DefaultLocationConfig.DEFAULT_CITY_NAME)

        DefaultLocationConfig.setActiveFallbackCity(38.7112, -9.1423, "Select City")
        val location = DefaultLocationConfig.defaultLocation
        assertNotNull(location)
        assertEquals(38.7112, location.latitude, 0.0001)
        assertEquals(-9.1423, location.longitude, 0.0001)
        assertEquals("DefaultFallbackProvider", location.provider)
    }

    @Test
    fun defaultLocationConfig_updatesActiveCityFallback() {
        DefaultLocationConfig.setActiveFallbackCity(51.0504, 13.7373, "Dresden, Germany")
        assertEquals(51.0504, DefaultLocationConfig.activeFallbackLat, 0.0001)
        assertEquals(13.7373, DefaultLocationConfig.activeFallbackLng, 0.0001)
        assertEquals("Dresden, Germany", DefaultLocationConfig.activeFallbackCityName)

        val location = DefaultLocationConfig.defaultLocation
        assertEquals(51.0504, location.latitude, 0.0001)
        assertEquals(13.7373, location.longitude, 0.0001)

        // Reset back to default
        DefaultLocationConfig.setActiveFallbackCity(
            DefaultLocationConfig.DEFAULT_LAT,
            DefaultLocationConfig.DEFAULT_LNG,
            DefaultLocationConfig.DEFAULT_CITY_NAME
        )
    }
}

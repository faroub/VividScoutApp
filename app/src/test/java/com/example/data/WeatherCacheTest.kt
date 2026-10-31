package com.example.data

import com.example.data.local.WeatherCacheEntity
import com.example.data.model.SuitabilityLevel
import com.example.data.model.WeatherReport
import com.example.util.WeatherSuitabilityEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherCacheTest {

    @Test
    fun testSuitabilityEvaluator_OutdoorRainRisk_returnsCautionOrUnfavorable() {
        val (level, reason) = WeatherSuitabilityEvaluator.evaluate(
            category = "sights",
            tempC = 20.0,
            feelsLikeC = 20.0,
            precipChance = 70,
            condition = "Rain Showers"
        )

        assertEquals(SuitabilityLevel.UNFAVORABLE, level)
        assertTrue(reason.contains("High rain chance"))
    }

    @Test
    fun testSuitabilityEvaluator_IndoorRainRisk_returnsIdeal() {
        val (level, reason) = WeatherSuitabilityEvaluator.evaluate(
            category = "eats",
            tempC = 18.0,
            feelsLikeC = 18.0,
            precipChance = 80,
            condition = "Heavy Rain"
        )

        assertEquals(SuitabilityLevel.IDEAL, level)
        assertTrue(reason.contains("Perfect time for this covered/indoor gem"))
    }

    @Test
    fun testSuitabilityEvaluator_OutdoorMildWeather_returnsIdeal() {
        val (level, reason) = WeatherSuitabilityEvaluator.evaluate(
            category = "shortcut",
            tempC = 22.0,
            feelsLikeC = 23.0,
            precipChance = 5,
            condition = "Clear Sunny"
        )

        assertEquals(SuitabilityLevel.IDEAL, level)
        assertTrue(reason.contains("Ideal outdoor conditions"))
    }

    @Test
    fun testWeatherReport_CacheAgeFormatting() {
        val now = System.currentTimeMillis()
        val reportJustUpdated = WeatherReport(
            gemId = "test_gem_1",
            temperatureC = 22.0,
            feelsLikeC = 23.0,
            precipitationChance = 10,
            condition = "Sunny",
            suitability = SuitabilityLevel.IDEAL,
            suitabilityReason = "Good weather",
            cachedAtMillis = now
        )

        assertEquals("Just updated", reportJustUpdated.cacheAgeText)

        val report15MinOld = reportJustUpdated.copy(cachedAtMillis = now - 15 * 60 * 1000L)
        assertEquals("Cached 15m ago", report15MinOld.cacheAgeText)

        val report2HourOld = reportJustUpdated.copy(cachedAtMillis = now - 2 * 3600 * 1000L)
        assertEquals("Cached 2h ago", report2HourOld.cacheAgeText)
    }

    @Test
    fun testWeatherCacheEntity_CreationAndMapping() {
        val entity = WeatherCacheEntity(
            gemId = "spot_1",
            temperatureC = 24.5,
            feelsLikeC = 26.0,
            precipitationChance = 15,
            condition = "Mainly Clear",
            suitability = "IDEAL",
            suitabilityReason = "Pleasant warmth",
            cachedAtMillis = System.currentTimeMillis()
        )

        assertEquals("spot_1", entity.gemId)
        assertEquals(24.5, entity.temperatureC, 0.01)
        assertEquals(26.0, entity.feelsLikeC, 0.01)
        assertEquals(15, entity.precipitationChance)
        assertEquals("IDEAL", entity.suitability)
    }
}

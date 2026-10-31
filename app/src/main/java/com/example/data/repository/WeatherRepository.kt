package com.example.data.repository

import com.example.data.local.WeatherCacheEntity
import com.example.data.local.WeatherDao
import com.example.data.model.SuitabilityLevel
import com.example.data.model.WeatherReport
import com.example.util.WeatherSuitabilityEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlin.math.abs

class WeatherRepository(private val weatherDao: WeatherDao) {

    fun getWeatherFlow(gemId: String): Flow<WeatherReport?> {
        return weatherDao.getWeatherForGem(gemId).map { entity ->
            entity?.toReport()
        }
    }

    suspend fun getOrFetchWeather(
        gemId: String,
        latitude: Double,
        longitude: Double,
        category: String,
        forceRefresh: Boolean = false
    ): WeatherReport = withContext(Dispatchers.IO) {
        val cached = weatherDao.getWeatherSync(gemId)
        val now = System.currentTimeMillis()

        // If cached within the last 30 minutes and not forced, return cached
        if (!forceRefresh && cached != null && (now - cached.cachedAtMillis) < 30 * 60 * 1000L) {
            return@withContext cached.toReport(isOffline = false)
        }

        // Try fetching online weather from Open-Meteo REST API
        var onlineReport: WeatherReport? = null
        try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code&hourly=precipitation_probability&forecast_days=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val inputStream = connection.inputStream
                val jsonText = inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)

                val current = root.getJSONObject("current")
                val tempC = current.getDouble("temperature_2m")
                val feelsLikeC = current.getDouble("apparent_temperature")
                val code = current.optInt("weather_code", 0)

                val hourly = root.optJSONObject("hourly")
                val precipProbArray = hourly?.optJSONArray("precipitation_probability")
                val precipChance = if (precipProbArray != null && precipProbArray.length() > 0) {
                    precipProbArray.getInt(0)
                } else 10

                val conditionStr = parseWeatherCode(code)
                val (level, reason) = WeatherSuitabilityEvaluator.evaluate(
                    category = category,
                    tempC = tempC,
                    feelsLikeC = feelsLikeC,
                    precipChance = precipChance,
                    condition = conditionStr
                )

                onlineReport = WeatherReport(
                    gemId = gemId,
                    temperatureC = tempC,
                    feelsLikeC = feelsLikeC,
                    precipitationChance = precipChance,
                    condition = conditionStr,
                    suitability = level,
                    suitabilityReason = reason,
                    cachedAtMillis = now,
                    isOffline = false
                )
            }
        } catch (e: Exception) {
            // Safe recovery: Open-Meteo network request failed or offline; log warning telemetry and fall back to cache or generator
            android.util.Log.w("WeatherRepository", "Open-Meteo weather fetch failed for spot: $gemId", e)
        }

        if (onlineReport != null) {
            weatherDao.insertWeather(onlineReport.toEntity())
            return@withContext onlineReport
        }

        // If network failed but we have stale cache, return stale cache marked offline
        if (cached != null) {
            return@withContext cached.toReport(isOffline = true)
        }

        // Otherwise generate deterministic offline fallback based on location coordinates & time
        val fallbackReport = generateFallbackWeather(gemId, latitude, longitude, category, now)
        weatherDao.insertWeather(fallbackReport.toEntity())
        return@withContext fallbackReport.copy(isOffline = true)
    }

    suspend fun clearCache(): Int = withContext(Dispatchers.IO) {
        weatherDao.clearAllWeather()
    }

    private fun generateFallbackWeather(
        gemId: String,
        latitude: Double,
        longitude: Double,
        category: String,
        timestamp: Long
    ): WeatherReport {
        val hash = abs(gemId.hashCode() xor latitude.hashCode() xor longitude.hashCode())
        val baseTemp = 21.0 + (hash % 6) - 2.0
        val feelsLike = baseTemp + (if (hash % 2 == 0) 1.5 else -0.5)
        val precip = (hash * 13) % 35
        val condition = if (precip > 25) "Partly Cloudy with Light Breeze" else "Clear Sunny Skies"

        val (level, reason) = WeatherSuitabilityEvaluator.evaluate(
            category = category,
            tempC = baseTemp,
            feelsLikeC = feelsLike,
            precipChance = precip,
            condition = condition
        )

        return WeatherReport(
            gemId = gemId,
            temperatureC = baseTemp,
            feelsLikeC = feelsLike,
            precipitationChance = precip,
            condition = condition,
            suitability = level,
            suitabilityReason = reason,
            cachedAtMillis = timestamp,
            isOffline = true
        )
    }

    private fun parseWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear Sunny"
            1, 2, 3 -> "Mainly Clear / Partly Cloudy"
            45, 48 -> "Foggy Atmosphere"
            51, 53, 55 -> "Light Drizzle"
            61, 63, 65 -> "Rain Showers"
            80, 81, 82 -> "Passing Rain"
            95, 96, 99 -> "Thunderstorm Risk"
            else -> "Mild Conditions"
        }
    }

    private fun WeatherCacheEntity.toReport(isOffline: Boolean = false): WeatherReport {
        val level = try {
            SuitabilityLevel.valueOf(suitability)
        } catch (e: Exception) {
            // Safe recovery: Return default SuitabilityLevel.IDEAL on invalid enum string
            android.util.Log.w("WeatherRepository", "Failed to parse SuitabilityLevel string: $suitability", e)
            SuitabilityLevel.IDEAL
        }
        return WeatherReport(
            gemId = gemId,
            temperatureC = temperatureC,
            feelsLikeC = feelsLikeC,
            precipitationChance = precipitationChance,
            condition = condition,
            suitability = level,
            suitabilityReason = suitabilityReason,
            cachedAtMillis = cachedAtMillis,
            isOffline = isOffline
        )
    }

    private fun WeatherReport.toEntity(): WeatherCacheEntity {
        return WeatherCacheEntity(
            gemId = gemId,
            temperatureC = temperatureC,
            feelsLikeC = feelsLikeC,
            precipitationChance = precipitationChance,
            condition = condition,
            suitability = suitability.name,
            suitabilityReason = suitabilityReason,
            cachedAtMillis = cachedAtMillis
        )
    }
}

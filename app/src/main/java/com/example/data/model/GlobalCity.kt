package com.example.data.model

import android.content.Context
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Data model representing a global city or region destination anywhere in the world.
 */
data class GlobalCity(
    val id: String,
    val name: String,
    val country: String,
    val flagEmoji: String,
    val latitude: Double,
    val longitude: Double,
    val description: String = "",
    val photoUrl: String = ""
)

object GlobalCityCatalog {

    private var cachedCities: List<GlobalCity>? = null

    val POPULAR_WORLD_CITIES: List<GlobalCity>
        get() = cachedCities ?: fallbackCities

    fun loadCitiesFromAssets(context: Context): List<GlobalCity> {
        cachedCities?.let { return it }
        return try {
            val jsonString = context.resources.openRawResource(R.raw.global_cities)
                .bufferedReader().use { it.readText() }
            val parsed = parseCitiesJson(jsonString)
            if (parsed.isNotEmpty()) {
                cachedCities = parsed
                parsed
            } else {
                fallbackCities
            }
        } catch (e: Exception) {
            // Safe recovery: Asset reading/parsing failed, log warning telemetry and return static fallback cities list
            android.util.Log.w("GlobalCityCatalog", "Failed to load global cities from raw asset", e)
            fallbackCities
        }
    }

    private fun parseCitiesJson(jsonString: String): List<GlobalCity> {
        val list = mutableListOf<GlobalCity>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    GlobalCity(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        country = obj.optString("country"),
                        flagEmoji = obj.optString("flagEmoji"),
                        latitude = obj.optDouble("latitude"),
                        longitude = obj.optDouble("longitude"),
                        description = obj.optString("description")
                    )
                )
            }
        } catch (e: Exception) {
            // Safe recovery: Log warning and return whatever cities were successfully parsed
            android.util.Log.w("GlobalCityCatalog", "Error parsing cities JSON array", e)
        }
        return list
    }

    private val fallbackCities = listOf(
        GlobalCity(
            id = "gps_current",
            name = "Live GPS Location",
            country = "Current Region",
            flagEmoji = "🛰️",
            latitude = 0.0,
            longitude = 0.0,
            description = "Auto-detect points of interest near your active coordinates anywhere in the world."
        ),
        GlobalCity(
            id = "city_lisbon",
            name = "Lisbon",
            country = "Portugal",
            flagEmoji = "🇵🇹",
            latitude = 38.7112,
            longitude = -9.1423,
            description = "Capital of seven hills, tile-clad alleyways, historic funiculars, and scenic miradouros."
        ),
        GlobalCity(
            id = "city_berlin",
            name = "Berlin",
            country = "Germany",
            flagEmoji = "🇩🇪",
            latitude = 52.5200,
            longitude = 13.4050,
            description = "Capital of creative arts, historic gates, riverside parks, and vibrant neighborhood cafes."
        )
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Dynamically geocodes an arbitrary city or region name anywhere in the world using OpenStreetMap Nominatim.
     */
    suspend fun geocodeGlobalCity(query: String): List<GlobalCity> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=6&addressdetails=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VividScoutApp/1.0 (Android; Global Geocoder)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseNominatimResults(body)
            }
        } catch (e: Exception) {
            // Safe recovery: Geocoding network call failed, log warning telemetry and return empty list
            android.util.Log.w("GlobalCityCatalog", "Nominatim geocoding network error for query: $query", e)
            emptyList()
        }
    }

    private fun parseNominatimResults(jsonString: String): List<GlobalCity> {
        val results = mutableListOf<GlobalCity>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val lat = obj.optDouble("lat", 0.0)
                val lon = obj.optDouble("lon", 0.0)
                val displayName = obj.optString("display_name", "")
                val parts = displayName.split(",").map { it.trim() }

                val name = parts.firstOrNull() ?: "Location"
                val country = parts.lastOrNull() ?: "Global"
                val flag = getFlagEmojiForCountry(country)

                if (lat != 0.0 && lon != 0.0) {
                    results.add(
                        GlobalCity(
                            id = "geo_${obj.optLong("place_id", i.toLong())}",
                            name = name,
                            country = country,
                            flagEmoji = flag,
                            latitude = lat,
                            longitude = lon,
                            description = displayName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Safe recovery: Return parsed results on malformed item
            android.util.Log.w("GlobalCityCatalog", "Error parsing Nominatim JSON response", e)
        }
        return results
    }

    private fun getFlagEmojiForCountry(countryName: String): String {
        val lower = countryName.lowercase()
        return when {
            lower.contains("portugal") -> "🇵🇹"
            lower.contains("france") -> "🇫🇷"
            lower.contains("japan") -> "🇯🇵"
            lower.contains("states") || lower.contains("usa") -> "🇺🇸"
            lower.contains("kingdom") || lower.contains("uk") -> "🇬🇧"
            lower.contains("italy") || lower.contains("italia") -> "🇮🇹"
            lower.contains("australia") -> "🇦🇺"
            lower.contains("brazil") || lower.contains("brasil") -> "🇧🇷"
            lower.contains("egypt") -> "🇪🇬"
            lower.contains("emirates") || lower.contains("uae") -> "🇦🇪"
            lower.contains("singapore") -> "🇸🇬"
            lower.contains("spain") || lower.contains("españa") -> "🇪🇸"
            lower.contains("germany") || lower.contains("deutschland") -> "🇩🇪"
            lower.contains("turkey") || lower.contains("türkiye") -> "🇹🇷"
            lower.contains("canada") -> "🇨🇦"
            lower.contains("mexico") -> "🇲🇽"
            lower.contains("india") -> "🇮🇳"
            lower.contains("china") -> "🇨🇳"
            lower.contains("greece") -> "🇬🇷"
            lower.contains("netherlands") -> "🇳🇱"
            else -> "🌍"
        }
    }
}

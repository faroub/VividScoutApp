package com.example.data.model

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

    val POPULAR_WORLD_CITIES = listOf(
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
            id = "city_paris",
            name = "Paris",
            country = "France",
            flagEmoji = "🇫🇷",
            latitude = 48.8566,
            longitude = 2.3522,
            description = "City of light, iconic iron towers, Seine riverbanks, historic bistros, and grand boulevards."
        ),
        GlobalCity(
            id = "city_tokyo",
            name = "Tokyo",
            country = "Japan",
            flagEmoji = "🇯🇵",
            latitude = 35.6762,
            longitude = 139.6503,
            description = "Futuristic metropolis, ancient shrines, hidden ramen alleys, and vibrant neon districts."
        ),
        GlobalCity(
            id = "city_nyc",
            name = "New York",
            country = "United States",
            flagEmoji = "🇺🇸",
            latitude = 40.7128,
            longitude = -74.0060,
            description = "The Big Apple, Central Park trails, iconic skyscrapers, and historic neighborhood eateries."
        ),
        GlobalCity(
            id = "city_london",
            name = "London",
            country = "United Kingdom",
            flagEmoji = "🇬🇧",
            latitude = 51.5074,
            longitude = -0.1278,
            description = "Thames riverbank walks, royal parks, historic pubs, and centuries-old architecture."
        ),
        GlobalCity(
            id = "city_rome",
            name = "Rome",
            country = "Italy",
            flagEmoji = "🇮🇹",
            latitude = 41.9028,
            longitude = 12.4964,
            description = "Eternal City of ancient arenas, marble fountains, cobblestone piazzas, and gelato spots."
        ),
        GlobalCity(
            id = "city_sydney",
            name = "Sydney",
            country = "Australia",
            flagEmoji = "🇦🇺",
            latitude = -33.8688,
            longitude = 151.2093,
            description = "Harbor vistas, iconic sails, coastal cliff walks, and sunny ocean coves."
        ),
        GlobalCity(
            id = "city_rio",
            name = "Rio de Janeiro",
            country = "Brazil",
            flagEmoji = "🇧🇷",
            latitude = -22.9068,
            longitude = -43.1729,
            description = "Marvelous city of dramatic granite peaks, golden beaches, and lively samba plazas."
        ),
        GlobalCity(
            id = "city_cairo",
            name = "Cairo",
            country = "Egypt",
            flagEmoji = "🇪🇬",
            latitude = 30.0444,
            longitude = 31.2357,
            description = "City of a thousand minarets, Nile river banks, historic bazaars, and ancient wonders."
        ),
        GlobalCity(
            id = "city_dubai",
            name = "Dubai",
            country = "UAE",
            flagEmoji = "🇦🇪",
            latitude = 25.2048,
            longitude = 55.2708,
            description = "Modern desert metropolis of towering spires, coastal promenades, and historic souks."
        ),
        GlobalCity(
            id = "city_singapore",
            name = "Singapore",
            country = "Singapore",
            flagEmoji = "🇸🇬",
            latitude = 1.3521,
            longitude = 103.8198,
            description = "Garden city of supertrees, vibrant hawker centers, and waterfront promenades."
        ),
        GlobalCity(
            id = "city_kyoto",
            name = "Kyoto",
            country = "Japan",
            flagEmoji = "🇯🇵",
            latitude = 35.0116,
            longitude = 135.7681,
            description = "Cultural heart of Japan, bamboo groves, wooden teahouses, and serene zen gardens."
        ),
        GlobalCity(
            id = "city_barcelona",
            name = "Barcelona",
            country = "Spain",
            flagEmoji = "🇪🇸",
            latitude = 41.3851,
            longitude = 2.1734,
            description = "Mediterranean gem of Modernist spires, Gothic alleyways, and sunny seaside promenades."
        ),
        GlobalCity(
            id = "city_berlin",
            name = "Berlin",
            country = "Germany",
            flagEmoji = "🇩🇪",
            latitude = 52.5200,
            longitude = 13.4050,
            description = "Capital of creative arts, historic gates, riverside parks, and vibrant neighborhood cafes."
        ),
        GlobalCity(
            id = "city_istanbul",
            name = "Istanbul",
            country = "Turkey",
            flagEmoji = "🇹🇷",
            latitude = 41.0082,
            longitude = 28.9784,
            description = "Crossroads of continents, Bosphorus views, domed mosques, and grand historic markets."
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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

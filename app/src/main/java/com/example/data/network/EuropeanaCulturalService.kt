package com.example.data.network

import android.util.Log
import com.example.data.local.ScoutSpotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service to query digital cultural heritage items from thousands of European museums,
 * libraries, and archives via the Europeana Cultural API.
 */
class EuropeanaCulturalService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "EuropeanaCulturalService"
        private const val USER_AGENT = "VividScoutApp/1.0 (Android; Europeana Cultural Heritage Resolver; contact: scout@example.com)"
        private const val PUBLIC_API_KEY = "api2demo"
    }

    /**
     * Queries Europeana Cultural Heritage repository for localized heritage objects, public art,
     * and historical archives near a location query.
     */
    suspend fun fetchEuropeanaCulturalSpots(
        locationName: String = "Lisbon",
        lat: Double = 38.7112,
        lon: Double = -9.1423
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        val spots = mutableListOf<ScoutSpotEntity>()
        try {
            val encodedQuery = java.net.URLEncoder.encode(locationName, "UTF-8")
            val url = "https://api.europeana.eu/record/v2/search.json?wskey=$PUBLIC_API_KEY&query=$encodedQuery&rows=8&profile=standard"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val root = JSONObject(body)
                val itemsArr = root.optJSONArray("items") ?: return@use

                for (i in 0 until itemsArr.length()) {
                    val item = itemsArr.getJSONObject(i)
                    val titleArr = item.optJSONArray("title")
                    val title = titleArr?.optString(0)?.ifEmpty { null } ?: continue
                    val edmIsShownBy = item.optJSONArray("edmIsShownBy")?.optString(0) ?: ""
                    val edmConceptLabel = item.optJSONArray("edmConceptLabel")?.optJSONArray(0)?.optString(0) ?: "Cultural Heritage"
                    val dataProvider = item.optJSONArray("dataProvider")?.optString(0) ?: "European Cultural Institution"
                    val europeanaId = item.optString("id", "europeana_$i")

                    // Extract actual coordinates provided by Europeana record
                    val spotLat = item.optDouble("latitude", item.optDouble("edmPlaceLatitude", Double.NaN))
                    val spotLon = item.optDouble("longitude", item.optDouble("edmPlaceLongitude", Double.NaN))

                    // Require actual coordinates from the provider — do not invent offset coordinates
                    if (spotLat.isNaN() || spotLon.isNaN() || (spotLat == 0.0 && spotLon == 0.0)) {
                        continue
                    }

                    spots.add(
                        ScoutSpotEntity(
                            id = "europeana_${europeanaId.hashCode()}",
                            title = title,
                            description = "Digital cultural heritage record archived by $dataProvider. Category: $edmConceptLabel.",
                            category = "history",
                            distance = "Heritage District",
                            walkMinutes = 8,
                            rating = null,
                            reviewCount = null,
                            imageUrl = edmIsShownBy,
                            badgeLabel = "Europeana",
                            statusBadge = "Europeana Cultural Item",
                            tagsRaw = "europeana|||archive|||$edmConceptLabel",
                            proTip = "Archived by $dataProvider.",
                            latitude = spotLat,
                            longitude = spotLon,
                            locationName = locationName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Europeana API query handled: ${e.message}")
        }
        spots
    }
}

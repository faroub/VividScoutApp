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
 * Service to retrieve geographical gazetteer POIs, hilltops, historical monuments,
 * and geographical features from the GeoNames Geographical Database (25M+ global places).
 */
class GeonamesService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "GeonamesService"
        private const val USER_AGENT = "VividScoutApp/1.0 (Android; GeoNames Gazetteer Resolver; contact: scout@example.com)"
        private const val DEMO_USERNAME = "demo"
    }

    /**
     * Fetches nearby geographical POIs and gazetteer landmarks from GeoNames.
     */
    suspend fun fetchNearbyGeonamesPOIs(
        lat: Double,
        lon: Double,
        radiusKm: Double = 3.0
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        val spots = mutableListOf<ScoutSpotEntity>()
        try {
            // GeoNames findNearbyWikipedia API endpoint
            val url = "https://secure.geonames.org/findNearbyWikipediaJSON?lat=$lat&lng=$lon&radius=$radiusKm&maxRows=10&username=$DEMO_USERNAME"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "GeoNames request returned code ${response.code}")
                    return@use
                }
                val body = response.body?.string() ?: return@use
                val root = JSONObject(body)
                val geonamesArr = root.optJSONArray("geonames") ?: return@use

                for (i in 0 until geonamesArr.length()) {
                    val item = geonamesArr.getJSONObject(i)
                    val title = item.optString("title").ifEmpty { null } ?: continue
                    val summary = item.optString("summary", "Gazetteer landmark cataloged on GeoNames database.")
                    val spotLat = item.optDouble("lat", lat)
                    val spotLng = item.optDouble("lng", lon)
                    val feature = item.optString("feature", "landmark")
                    val thumbnail = item.optString("thumbnailImg", "")
                    val geoNameId = item.optLong("geoNameId", i.toLong())

                    spots.add(
                        ScoutSpotEntity(
                            id = "geonames_$geoNameId",
                            title = title,
                            description = summary,
                            category = if (feature.contains("mountain") || feature.contains("hill")) "sights" else "history",
                            distance = "Nearby",
                            walkMinutes = 6,
                            rating = null,
                            reviewCount = null,
                            imageUrl = thumbnail,
                            badgeLabel = "GeoNames",
                            statusBadge = "GeoNames Gazetteer",
                            tagsRaw = "geonames|||gazetteer|||$feature",
                            proTip = "GeoNames gazetteer entry #$geoNameId ($feature).",
                            latitude = spotLat,
                            longitude = spotLng,
                            locationName = "GeoNames POI"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GeoNames fetch handled: ${e.message}")
        }
        spots
    }
}

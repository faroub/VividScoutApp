package com.example.data.network

import android.util.Log
import com.example.data.local.ScoutSpotEntity
import com.example.data.places.OpenTripMapSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service to retrieve touristic places and points of interest (POIs)
 * from OpenTripMap and Wikidata open tourist databases.
 */
class OpenTripMapService(
    private val openTripMapSource: OpenTripMapSource = OpenTripMapSource(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "OpenTripMapService"
        private const val USER_AGENT = "VividScoutApp/1.0 (Android; Touristic Source Aggregator; contact: scout@example.com)"
    }

    /**
     * Fetches touristic POIs near given coordinates from OpenTripMap via secure authenticated backend function.
     */
    suspend fun fetchTouristicPlaces(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 3000
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        val candidatesResult = openTripMapSource.fetchNear(lat, lon, radiusMeters)
        val candidates = candidatesResult.getOrNull().orEmpty()

        candidates.map { candidate ->
            ScoutSpotEntity(
                id = candidate.sourceId,
                title = candidate.name,
                description = candidate.description.ifBlank { "Touristic place documented via OpenTripMap API." },
                category = candidate.category,
                distance = "Nearby",
                walkMinutes = 8,
                rating = null,
                reviewCount = null,
                imageUrl = candidate.imageUrl.orEmpty(),
                badgeLabel = "OpenTripMap",
                statusBadge = "OpenTripMap POI",
                tagsRaw = "opentripmap|||${candidate.category}",
                proTip = "Discovered via OpenTripMap global POI database.",
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                locationName = "OpenTripMap Landmark"
            )
        }
    }

    /**
     * Queries Wikidata SPARQL Endpoint for UNESCO World Heritage Sites & Monuments near given location.
     */
    suspend fun fetchWikidataHeritageSites(
        lat: Double,
        lon: Double
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        val spots = mutableListOf<ScoutSpotEntity>()
        try {
            // SPARQL query fetching UNESCO / Heritage monuments within ~10km radius
            val sparql = """
                SELECT ?item ?itemLabel ?coord ?image WHERE {
                  SERVICE wikibase:around {
                    ?item wdt:P625 ?coord .
                    bd:serviceParam wikibase:center "Point($lon $lat)"^^geo:wktLiteral .
                    bd:serviceParam wikibase:radius "10" .
                  }
                  ?item wdt:P31/wdt:P279* ?type .
                  FILTER(?type IN (wd:Q9259, wd:Q83440, wd:Q570116, wd:Q4989906))
                  OPTIONAL { ?item wdt:P18 ?image . }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "en,pt,es,fr". }
                } LIMIT 8
            """.trimIndent()

            val encodedSparql = URLEncoder.encode(sparql, "UTF-8")
            val url = "https://query.wikidata.org/sparql?query=$encodedSparql&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/sparql-results+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string() ?: return@use
                val root = JSONObject(body)
                val bindings = root.optJSONObject("results")?.optJSONArray("bindings") ?: return@use

                for (i in 0 until bindings.length()) {
                    val binding = bindings.getJSONObject(i)
                    val label = binding.optJSONObject("itemLabel")?.optString("value") ?: continue
                    if (label.startsWith("Q") && label.substring(1).all { it.isDigit() }) continue

                    val image = binding.optJSONObject("image")?.optString("value") ?: ""
                    val coordStr = binding.optJSONObject("coord")?.optString("value") ?: ""

                    var spotLat = lat
                    var spotLon = lon
                    if (coordStr.contains("Point(")) {
                        val parts = coordStr.substringAfter("Point(").substringBefore(")").split(" ")
                        if (parts.size == 2) {
                            spotLon = parts[0].toDoubleOrNull() ?: lon
                            spotLat = parts[1].toDoubleOrNull() ?: lat
                        }
                    }

                    spots.add(
                        ScoutSpotEntity(
                            id = "wikidata_${label.hashCode()}",
                            title = label,
                            description = "Official Cultural Heritage Landmark cataloged on Wikidata Knowledge Graph & UNESCO registries.",
                            category = "history",
                            distance = "Nearby",
                            walkMinutes = 10,
                            rating = null,
                            reviewCount = null,
                            imageUrl = image,
                            badgeLabel = "Wikidata",
                            statusBadge = "UNESCO / Wikidata",
                            tagsRaw = "wikidata|||heritage|||history",
                            proTip = "Official World Heritage landmark registry item.",
                            latitude = spotLat,
                            longitude = spotLon,
                            locationName = "World Heritage Site"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wikidata SPARQL query handled: ${e.message}")
        }
        spots
    }
}

package com.example.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Service to retrieve authentic location photos from the free Wikipedia & Wikimedia Commons REST APIs.
 * Supports:
 * - Querying multi-language Wikipedia page summaries for thumbnail/original image
 * - Worldwide Wikipedia Geosearch API by latitude & longitude
 * - Direct Wikimedia Commons Special:FilePath conversion for File: OSM tags
 * - Extracting and transforming OSM wikipedia tags
 */
class WikipediaPhotoService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "WikipediaPhotoService"
        private const val USER_AGENT = "VividScoutApp/1.0 (Android; Location Photo Resolver; contact: scout@example.com)"
    }

    /**
     * Resolves an authentic Wikimedia Commons image URL from an OpenStreetMap `wikimedia_commons` tag
     * (e.g., "File:Miradouro de Santa Luzia.jpg" or "Miradouro_de_Santa_Luzia.jpg").
     */
    fun resolveWikimediaCommonsTag(commonsTag: String?, targetWidth: Int = 800): String? {
        if (commonsTag.isNullOrBlank()) return null
        val cleaned = commonsTag.trim().removePrefix("File:").removePrefix("Image:").trim()
        if (cleaned.isBlank()) return null
        val encoded = try {
            URLEncoder.encode(cleaned.replace(" ", "_"), StandardCharsets.UTF_8.toString())
                .replace("+", "%20")
        } catch (e: Exception) {
            // Safe recovery: Encoding failed, fall back to simple string underscore replacement
            android.util.Log.w("WikipediaPhotoService", "URL encoding failed for tag: $commonsTag", e)
            cleaned.replace(" ", "_")
        }
        return "https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=$targetWidth"
    }

    /**
     * Resolves a photo by querying Wikipedia REST Summary API across languages (English, local, etc.).
     */
    suspend fun fetchWikipediaPhotoByTitle(title: String): WikipediaPhotoResult? = withContext(Dispatchers.IO) {
        val cleaned = cleanTitleForWiki(title)
        if (cleaned.isBlank()) return@withContext null

        // 1. English Wikipedia first (broadest coverage globally)
        fetchPageSummary("en", cleaned)?.let { return@withContext it }

        // 2. Portuguese Wikipedia
        fetchPageSummary("pt", cleaned)?.let { return@withContext it }

        // 3. Spanish / French / German / Japanese Wikipedia
        fetchPageSummary("es", cleaned)?.let { return@withContext it }
        fetchPageSummary("fr", cleaned)?.let { return@withContext it }
        fetchPageSummary("de", cleaned)?.let { return@withContext it }
        fetchPageSummary("ja", cleaned)?.let { return@withContext it }

        null
    }

    /**
     * Resolves a photo from an OSM `wikipedia` tag (e.g., "en:Eiffel Tower", "pt:Miradouro de Santa Luzia").
     */
    suspend fun fetchWikipediaPhotoFromTag(tagValue: String): WikipediaPhotoResult? = withContext(Dispatchers.IO) {
        val trimmed = tagValue.trim()
        val parts = trimmed.split(":", limit = 2)
        val (lang, title) = if (parts.size == 2 && parts[0].length <= 5) {
            Pair(parts[0], parts[1])
        } else {
            Pair("en", trimmed)
        }
        fetchPageSummary(lang, title)
    }

    /**
     * Worldwide Geosearch: queries Wikipedia REST API for articles near latitude & longitude anywhere on Earth,
     * returning authentic photos and summaries of real nearby landmarks.
     */
    suspend fun fetchWikipediaPhotosByLocation(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 5000
    ): List<WikipediaPhotoResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WikipediaPhotoResult>()
        try {
            val url = "https://en.wikipedia.org/w/api.php?action=query&list=geosearch&gscoord=$lat|$lon&gsradius=$radiusMeters&gslimit=12&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val queryObj = root.optJSONObject("query") ?: return@withContext emptyList()
                val geoArray = queryObj.optJSONArray("geosearch") ?: return@withContext emptyList()

                for (i in 0 until geoArray.length()) {
                    val geoItem = geoArray.optJSONObject(i) ?: continue
                    val title = geoItem.optString("title")
                    if (title.isNotBlank()) {
                        fetchPageSummary("en", title)?.let { summary ->
                            if (summary.photoUrl.isNotBlank()) {
                                results.add(summary)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed Wikipedia geosearch for coordinates ($lat, $lon): ${e.message}")
        }
        results
    }

    private fun cleanTitleForWiki(rawTitle: String): String {
        return rawTitle
            .replace(Regex("—.*"), "") // Remove subtitle after em-dash
            .replace(Regex("-.*"), "")  // Remove subtitle after hyphen
            .replace(Regex("\\(.*\\)"), "") // Remove parenthetical notes
            .trim()
    }

    private fun fetchPageSummary(lang: String, title: String): WikipediaPhotoResult? {
        return try {
            val encodedTitle = URLEncoder.encode(title.replace(" ", "_"), StandardCharsets.UTF_8.toString())
                .replace("+", "%20")
            val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val body = response.body?.string() ?: return null
                parseSummaryJson(body, title, lang)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch summary for $title ($lang): ${e.message}")
            null
        }
    }

    private fun parseSummaryJson(jsonString: String, title: String, lang: String): WikipediaPhotoResult? {
        return try {
            val root = JSONObject(jsonString)
            val thumbnailObj = root.optJSONObject("thumbnail")
            val originalObj = root.optJSONObject("originalimage")

            var photoUrl = thumbnailObj?.optString("source")
            if (photoUrl.isNullOrBlank()) {
                photoUrl = originalObj?.optString("source")
            }

            if (photoUrl.isNullOrBlank()) return null

            // Enhance thumbnail resolution to 800px if it's a standard Wikimedia thumb
            if (photoUrl.contains("/thumb/")) {
                val regex = Regex("/\\d+px-")
                photoUrl = photoUrl.replace(regex, "/800px-")
            }

            val pageTitle = root.optString("title", title)
            val attribution = "Wikipedia ($lang) / $pageTitle"

            WikipediaPhotoResult(
                photoUrl = photoUrl,
                attribution = attribution,
                pageTitle = pageTitle,
                description = root.optString("description", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Wikipedia summary JSON", e)
            null
        }
    }
}

data class WikipediaPhotoResult(
    val photoUrl: String,
    val attribution: String,
    val pageTitle: String,
    val description: String = ""
)

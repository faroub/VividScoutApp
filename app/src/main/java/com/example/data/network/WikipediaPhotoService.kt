package com.example.data.network

import android.util.Log
import com.example.util.SafeLog
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
    fun resolveWikimediaCommonsTag(commonsTag: String?, targetWidth: Int = 1200): String? {
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
     * Queries Wikimedia Commons API for 1200px HD resolution image metadata directly using gem names.
     */
    suspend fun fetchWikimediaCommonsHighResUrl(query: String, targetWidth: Int = 1200): WikipediaPhotoResult? = withContext(Dispatchers.IO) {
        val cleaned = cleanTitleForWiki(query)
        if (cleaned.length < 3) return@withContext null
        try {
            val encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=1&prop=imageinfo&iiprop=url&iiurlwidth=$targetWidth&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val pagesObj = root.optJSONObject("query")?.optJSONObject("pages") ?: return@withContext null
                val keys = pagesObj.keys()
                if (keys.hasNext()) {
                    val key = keys.next()
                    val pageObj = pagesObj.optJSONObject(key) ?: return@withContext null
                    val imageInfoArr = pageObj.optJSONArray("imageinfo") ?: return@withContext null
                    if (imageInfoArr.length() > 0) {
                        val infoObj = imageInfoArr.getJSONObject(0)
                        val photoUrl = infoObj.optString("thumburl").ifEmpty { infoObj.optString("url") }
                        val fileTitle = pageObj.optString("title", cleaned)
                        if (photoUrl.isNotBlank()) {
                            Log.i(TAG, "Wikimedia Commons API returned 1200px HD photo for '$query': $photoUrl")
                            return@withContext WikipediaPhotoResult(
                                photoUrl = photoUrl,
                                attribution = "Wikimedia Commons / $fileTitle",
                                pageTitle = fileTitle,
                                description = "Wikimedia Commons HD image metadata"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wikimedia Commons high-res lookup failed for '$query': ${e.message}")
        }
        null
    }

    /**
     * Resolves a photo by querying Wikipedia REST Summary API across languages (English, local, etc.).
     */
    suspend fun fetchWikipediaPhotoByTitle(title: String, targetWidth: Int = 1200): WikipediaPhotoResult? = withContext(Dispatchers.IO) {
        val cleaned = cleanTitleForWiki(title)
        if (cleaned.isBlank()) return@withContext null

        // 1. English Wikipedia first (broadest coverage globally)
        fetchPageSummary("en", cleaned, targetWidth)?.let { return@withContext it }

        // 2. Portuguese Wikipedia
        fetchPageSummary("pt", cleaned, targetWidth)?.let { return@withContext it }

        // 3. Spanish / French / German / Japanese Wikipedia
        fetchPageSummary("es", cleaned, targetWidth)?.let { return@withContext it }
        fetchPageSummary("fr", cleaned, targetWidth)?.let { return@withContext it }
        fetchPageSummary("de", cleaned, targetWidth)?.let { return@withContext it }
        fetchPageSummary("ja", cleaned, targetWidth)?.let { return@withContext it }

        null
    }

    /**
     * Resolves a photo from an OSM `wikipedia` tag (e.g., "en:Eiffel Tower", "pt:Miradouro de Santa Luzia").
     */
    suspend fun fetchWikipediaPhotoFromTag(tagValue: String, targetWidth: Int = 1200): WikipediaPhotoResult? = withContext(Dispatchers.IO) {
        val trimmed = tagValue.trim()
        val parts = trimmed.split(":", limit = 2)
        val (lang, title) = if (parts.size == 2 && parts[0].length <= 5) {
            Pair(parts[0], parts[1])
        } else {
            Pair("en", trimmed)
        }
        fetchPageSummary(lang, title, targetWidth)
    }

    /**
     * Worldwide Geosearch: queries Wikipedia API using generator=geosearch to retrieve
     * real landmark articles with photos within radiusMeters in a single fast network call.
     */
    suspend fun fetchWikipediaPhotosByLocation(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 2000,
        primaryLang: String = "en",
        targetWidth: Int = 1200
    ): List<WikipediaPhotoResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WikipediaPhotoResult>()
        val languages = if (primaryLang == "pt") listOf("pt", "en") else listOf("en", "pt")

        for (lang in languages) {
            try {
                val url = "https://$lang.wikipedia.org/w/api.php?action=query&generator=geosearch&ggscoord=$lat|$lon&ggsradius=$radiusMeters&ggslimit=8&prop=pageimages|description&piprop=thumbnail|original&pithumbsize=$targetWidth&format=json"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val root = JSONObject(body)
                    val queryObj = root.optJSONObject("query") ?: return@use
                    val pagesObj = queryObj.optJSONObject("pages") ?: return@use

                    val keys = pagesObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val pageObj = pagesObj.optJSONObject(key) ?: continue
                        val title = pageObj.optString("title")
                        val thumbObj = pageObj.optJSONObject("thumbnail")
                        val origObj = pageObj.optJSONObject("original")

                        var photoUrl = thumbObj?.optString("source") ?: origObj?.optString("source")
                        if (!photoUrl.isNullOrBlank()) {
                            if (photoUrl.contains("/thumb/")) {
                                val regex = Regex("/\\d+px-")
                                photoUrl = photoUrl.replace(regex, "/${targetWidth}px-")
                            }
                            results.add(
                                WikipediaPhotoResult(
                                    photoUrl = photoUrl,
                                    attribution = "Wikimedia Commons / $title ($lang)",
                                    pageTitle = title,
                                    description = pageObj.optString("description", "")
                                )
                            )
                        }
                    }
                }
                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                Log.d(TAG, "Failed Wikipedia geosearch for coordinates (${SafeLog.sanitizeCoordinate(lat)}, ${SafeLog.sanitizeCoordinate(lon)}) on $lang: ${e.message}")
            }
        }
        results
    }

    /**
     * Searches Wikipedia via OpenSearch / Search API and fetches the thumbnail of the top result.
     */
    suspend fun searchWikipediaPhoto(query: String, lang: String = "en", targetWidth: Int = 1200): WikipediaPhotoResult? = withContext(Dispatchers.IO) {
        val cleaned = cleanTitleForWiki(query)
        if (cleaned.length < 3) return@withContext null
        try {
            val encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=1&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val searchArr = root.optJSONObject("query")?.optJSONArray("search") ?: return@withContext null
                if (searchArr.length() > 0) {
                    val firstResult = searchArr.getJSONObject(0)
                    val title = firstResult.optString("title")
                    if (title.isNotBlank()) {
                        return@withContext fetchPageSummary(lang, title, targetWidth)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wikipedia search query failed for '$query': ${e.message}")
        }
        null
    }

    fun cleanTitleForWiki(rawTitle: String): String {
        return rawTitle
            .replace(Regex("#\\d+"), "") // Remove numbered suffixes like #1, #2
            .replace(Regex("—.*"), "") // Remove subtitle after em-dash
            .replace(Regex("-.*"), "")  // Remove subtitle after hyphen
            .replace(Regex("\\(.*\\)"), "") // Remove parenthetical notes
            .replace("Secret Spot", "", ignoreCase = true)
            .replace("Hidden Passage", "", ignoreCase = true)
            .replace("Scenic Viewpoint", "", ignoreCase = true)
            .replace("Historic Tavern", "", ignoreCase = true)
            .replace("Artisan Bakery", "", ignoreCase = true)
            .replace("Local Gem", "", ignoreCase = true)
            .trim()
    }

    private fun fetchPageSummary(lang: String, title: String, targetWidth: Int = 1200): WikipediaPhotoResult? {
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
                parseSummaryJson(body, title, lang, targetWidth)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch summary for $title ($lang): ${e.message}")
            null
        }
    }

    private fun parseSummaryJson(jsonString: String, title: String, lang: String, targetWidth: Int = 1200): WikipediaPhotoResult? {
        return try {
            val root = JSONObject(jsonString)
            val thumbnailObj = root.optJSONObject("thumbnail")
            val originalObj = root.optJSONObject("originalimage")

            var photoUrl = thumbnailObj?.optString("source")
            if (photoUrl.isNullOrBlank()) {
                photoUrl = originalObj?.optString("source")
            }

            if (photoUrl.isNullOrBlank()) return null

            // Enhance thumbnail resolution to 1200px HD if it's a standard Wikimedia thumb
            if (photoUrl.contains("/thumb/")) {
                val regex = Regex("/\\d+px-")
                photoUrl = photoUrl.replace(regex, "/${targetWidth}px-")
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

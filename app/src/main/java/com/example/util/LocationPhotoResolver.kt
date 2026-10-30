package com.example.util

import android.util.Log
import com.example.data.network.WikipediaPhotoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified photo resolution engine that bridges:
 * 1. OpenStreetMap node tags (image, wikimedia_commons, wikipedia)
 * 2. Curated Lisbon & Global Landmark Catalogs
 * 3. Asynchronous Wikipedia/Wikimedia REST API queries across languages
 * 4. Contextual keyword & regional ambient photo fallbacks
 */
object LocationPhotoResolver {

    private const val TAG = "LocationPhotoResolver"
    private val wikiService = WikipediaPhotoService()

    data class EnrichedPhoto(
        val url: String,
        val attribution: String,
        val isVerifiedLandmark: Boolean
    )

    /**
     * Checks if a URL is empty, an icon placeholder (e.g. ic_cat_sights),
     * or one of the generic stock photos previously hardcoded.
     */
    fun isGenericOrStockPhoto(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val trimmed = url.trim()
        if (trimmed.startsWith("ic_cat_") || trimmed.startsWith("img_lisbon_")) return true

        // Known repeated generic stock photos
        val genericUnsplashIds = listOf(
            "1509440159596", // bakery
            "1548707309",    // tram/convent
            "1513635269",    // alley/stairs
            "1513688275",    // generic viewpoint
            "1588668214",    // castle stock
            "1555881400",    // arch
            "1517248135"     // dining table
        )
        for (stockId in genericUnsplashIds) {
            if (trimmed.contains(stockId)) return true
        }

        return false
    }

    /**
     * Synchronous immediate photo resolution for fast UI rendering or offline discovery.
     */
    fun resolvePhotoSynchronous(
        title: String?,
        category: String?,
        districtOrLocation: String? = null,
        osmTags: Map<String, String>? = null
    ): EnrichedPhoto {
        // 1. Check OSM image tag
        val osmImage = osmTags?.get("image")
        if (!osmImage.isNullOrBlank() && (osmImage.startsWith("http://") || osmImage.startsWith("https://"))) {
            return EnrichedPhoto(osmImage, "OpenStreetMap / Node Contributor", isVerifiedLandmark = true)
        }

        // 2. Check OSM wikimedia_commons tag
        val commonsTag = osmTags?.get("wikimedia_commons")
        if (!commonsTag.isNullOrBlank()) {
            val commonsUrl = wikiService.resolveWikimediaCommonsTag(commonsTag)
            if (commonsUrl != null) {
                return EnrichedPhoto(commonsUrl, "Wikimedia Commons / $commonsTag", isVerifiedLandmark = true)
            }
        }

        // 3. Check Global & Lisbon Landmark Catalogs
        val globalLandmark = GlobalLandmarkCatalog.findLandmark(title)
        if (globalLandmark != null) {
            return EnrichedPhoto(globalLandmark.imageUrl, globalLandmark.attribution, isVerifiedLandmark = true)
        }

        val lisbonLandmark = LisbonLandmarkCatalog.findLandmark(title)
        if (lisbonLandmark != null) {
            return EnrichedPhoto(lisbonLandmark.imageUrl, lisbonLandmark.attribution, isVerifiedLandmark = true)
        }

        // 4. Fallback to contextual keyword / regional photo
        val (fallbackUrl, fallbackAttr) = GlobalLandmarkCatalog.resolveGlobalContextualPhoto(title, category, districtOrLocation)
        return EnrichedPhoto(fallbackUrl, fallbackAttr, isVerifiedLandmark = false)
    }

    /**
     * Full asynchronous photo enrichment.
     * Queries Wikipedia REST API if no direct verified photo exists yet.
     */
    suspend fun resolveEnrichedPhoto(
        title: String,
        category: String?,
        districtOrLocation: String? = null,
        osmTags: Map<String, String>? = null
    ): EnrichedPhoto = withContext(Dispatchers.IO) {
        // 1. Direct OSM image tag
        val osmImage = osmTags?.get("image")
        if (!osmImage.isNullOrBlank() && (osmImage.startsWith("http://") || osmImage.startsWith("https://"))) {
            return@withContext EnrichedPhoto(osmImage, "OpenStreetMap Image", true)
        }

        // 2. Direct OSM wikimedia_commons tag
        val commonsTag = osmTags?.get("wikimedia_commons")
        if (!commonsTag.isNullOrBlank()) {
            val commonsUrl = wikiService.resolveWikimediaCommonsTag(commonsTag)
            if (commonsUrl != null) {
                return@withContext EnrichedPhoto(commonsUrl, "Wikimedia Commons ($commonsTag)", true)
            }
        }

        // 3. Global & Lisbon Landmark Catalogs
        val globalLandmark = GlobalLandmarkCatalog.findLandmark(title)
        if (globalLandmark != null) {
            return@withContext EnrichedPhoto(globalLandmark.imageUrl, globalLandmark.attribution, true)
        }

        val lisbonLandmark = LisbonLandmarkCatalog.findLandmark(title)
        if (lisbonLandmark != null) {
            return@withContext EnrichedPhoto(lisbonLandmark.imageUrl, lisbonLandmark.attribution, true)
        }

        // 4. OSM wikipedia tag (e.g. "en:Eiffel Tower" or "pt:Miradouro de Santa Luzia")
        val wikiTag = osmTags?.get("wikipedia")
        if (!wikiTag.isNullOrBlank()) {
            val wikiResult = wikiService.fetchWikipediaPhotoFromTag(wikiTag)
            if (wikiResult != null && wikiResult.photoUrl.isNotBlank()) {
                Log.d(TAG, "Resolved photo via OSM wikipedia tag: ${wikiResult.photoUrl}")
                return@withContext EnrichedPhoto(wikiResult.photoUrl, wikiResult.attribution, true)
            }
        }

        // 5. Query Wikipedia REST API by title
        val titleWikiResult = wikiService.fetchWikipediaPhotoByTitle(title)
        if (titleWikiResult != null && titleWikiResult.photoUrl.isNotBlank()) {
            Log.d(TAG, "Resolved photo via Wikipedia title lookup for '$title': ${titleWikiResult.photoUrl}")
            return@withContext EnrichedPhoto(titleWikiResult.photoUrl, titleWikiResult.attribution, true)
        }

        // 6. Contextual Keyword & Category fallback
        val (contextualUrl, attribution) = GlobalLandmarkCatalog.resolveGlobalContextualPhoto(
            title = title,
            category = category,
            districtOrLocation = districtOrLocation
        )
        EnrichedPhoto(contextualUrl, attribution, false)
    }
}

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
        val isVerifiedLandmark: Boolean,
        val isOsmVerified: Boolean = false,
        val isCuratedLandmark: Boolean = false
    )

    /**
     * Checks if a URL is empty, an icon placeholder, or one of the generic stock/fallback photos.
     */
    fun isGenericOrStockPhoto(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val trimmed = url.trim()
        if (trimmed.startsWith("ic_cat_") || trimmed.startsWith("img_lisbon_")) return true

        // Known repeated generic stock photos and old hardcoded Eiffel Tower fallback
        val genericUnsplashIds = listOf(
            "1502602898657", // Old Eiffel Tower fallback
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
        osmTags: Map<String, String>? = null,
        spotId: String? = null
    ): EnrichedPhoto {
        // 1. Check OSM image tag
        val osmImage = osmTags?.get("image")
        if (!osmImage.isNullOrBlank() && (osmImage.startsWith("http://") || osmImage.startsWith("https://"))) {
            return EnrichedPhoto(osmImage, "OpenStreetMap / Node Contributor", isVerifiedLandmark = true, isOsmVerified = true)
        }

        // 2. Check OSM wikimedia_commons tag
        val commonsTag = osmTags?.get("wikimedia_commons")
        if (!commonsTag.isNullOrBlank()) {
            val commonsUrl = wikiService.resolveWikimediaCommonsTag(commonsTag)
            if (commonsUrl != null) {
                return EnrichedPhoto(commonsUrl, "Wikimedia Commons / $commonsTag", isVerifiedLandmark = true, isOsmVerified = true)
            }
        }

        // 3. Check Global & Lisbon Landmark Catalogs
        val globalLandmark = GlobalLandmarkCatalog.findLandmark(title)
        if (globalLandmark != null) {
            return EnrichedPhoto(globalLandmark.imageUrl, globalLandmark.attribution, isVerifiedLandmark = true, isCuratedLandmark = true)
        }

        val lisbonLandmark = LisbonLandmarkCatalog.findLandmark(title)
        if (lisbonLandmark != null) {
            return EnrichedPhoto(lisbonLandmark.imageUrl, lisbonLandmark.attribution, isVerifiedLandmark = true, isCuratedLandmark = true)
        }

        // 4. Contextual keyword & district matching from Lisbon catalog
        val (lisbonUrl, lisbonAttr) = LisbonLandmarkCatalog.resolveContextualPhoto(
            title = title,
            category = category,
            districtOrLocation = districtOrLocation
        )
        if (!lisbonAttr.endsWith("Lisbon Street Exploration")) {
            return EnrichedPhoto(lisbonUrl, lisbonAttr, isVerifiedLandmark = false)
        }

        // 5. Fallback to contextual keyword / regional photo pool
        val (fallbackUrl, fallbackAttr) = GlobalLandmarkCatalog.resolveGlobalContextualPhoto(
            title = title,
            category = category,
            districtOrLocation = districtOrLocation,
            seedKey = spotId ?: title
        )
        return EnrichedPhoto(fallbackUrl, fallbackAttr, isVerifiedLandmark = false)
    }

    /**
     * Full asynchronous photo enrichment.
     * Queries Wikipedia REST API, search API, and Geosearch if no direct verified photo exists yet.
     */
    suspend fun resolveEnrichedPhoto(
        title: String,
        category: String?,
        districtOrLocation: String? = null,
        osmTags: Map<String, String>? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        spotId: String? = null
    ): EnrichedPhoto = withContext(Dispatchers.IO) {
        // 1. Direct OSM image tag
        val osmImage = osmTags?.get("image")
        if (!osmImage.isNullOrBlank() && (osmImage.startsWith("http://") || osmImage.startsWith("https://"))) {
            return@withContext EnrichedPhoto(osmImage, "OpenStreetMap Image", true, isOsmVerified = true)
        }

        // 2. Direct OSM wikimedia_commons tag
        val commonsTag = osmTags?.get("wikimedia_commons")
        if (!commonsTag.isNullOrBlank()) {
            val commonsUrl = wikiService.resolveWikimediaCommonsTag(commonsTag, targetWidth = 1200)
            if (commonsUrl != null) {
                return@withContext EnrichedPhoto(commonsUrl, "Wikimedia Commons ($commonsTag)", true, isOsmVerified = true)
            }
        }

        // 3. Query Wikimedia Commons API directly using gem name for 1200px HD resolution metadata
        val commonsDirect = wikiService.fetchWikimediaCommonsHighResUrl(title, targetWidth = 1200)
        if (commonsDirect != null && commonsDirect.photoUrl.isNotBlank()) {
            Log.i(TAG, "Resolved 1200px HD photo from Wikimedia Commons API for '$title': ${commonsDirect.photoUrl}")
            return@withContext EnrichedPhoto(commonsDirect.photoUrl, commonsDirect.attribution, true)
        }

        // 4. Global & Lisbon Landmark Catalogs
        val globalLandmark = GlobalLandmarkCatalog.findLandmark(title)
        if (globalLandmark != null) {
            return@withContext EnrichedPhoto(globalLandmark.imageUrl, globalLandmark.attribution, true, isCuratedLandmark = true)
        }

        val lisbonLandmark = LisbonLandmarkCatalog.findLandmark(title)
        if (lisbonLandmark != null) {
            return@withContext EnrichedPhoto(lisbonLandmark.imageUrl, lisbonLandmark.attribution, true, isCuratedLandmark = true)
        }

        // 5. OSM wikipedia tag (e.g. "en:Eiffel Tower" or "pt:Miradouro de Santa Luzia")
        val wikiTag = osmTags?.get("wikipedia")
        if (!wikiTag.isNullOrBlank()) {
            val wikiResult = wikiService.fetchWikipediaPhotoFromTag(wikiTag, targetWidth = 1200)
            if (wikiResult != null && wikiResult.photoUrl.isNotBlank()) {
                Log.d(TAG, "Resolved photo via OSM wikipedia tag: ${wikiResult.photoUrl}")
                return@withContext EnrichedPhoto(wikiResult.photoUrl, wikiResult.attribution, true)
            }
        }

        // 6. Query Wikipedia REST API by title
        val titleWikiResult = wikiService.fetchWikipediaPhotoByTitle(title, targetWidth = 1200)
        if (titleWikiResult != null && titleWikiResult.photoUrl.isNotBlank()) {
            Log.d(TAG, "Resolved photo via Wikipedia title lookup for '$title': ${titleWikiResult.photoUrl}")
            return@withContext EnrichedPhoto(titleWikiResult.photoUrl, titleWikiResult.attribution, true)
        }

        // 6b. Query Wikipedia Search API if direct title didn't resolve
        val searchResult = wikiService.searchWikipediaPhoto(title, "pt", targetWidth = 1200)
            ?: wikiService.searchWikipediaPhoto(title, "en", targetWidth = 1200)
        if (searchResult != null && searchResult.photoUrl.isNotBlank()) {
            Log.d(TAG, "Resolved photo via Wikipedia search API for '$title': ${searchResult.photoUrl}")
            return@withContext EnrichedPhoto(searchResult.photoUrl, searchResult.attribution, true)
        }

        // 6c. Query Wikipedia Geosearch around coordinates
        if (latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)) {
            val geoResults = wikiService.fetchWikipediaPhotosByLocation(latitude, longitude, radiusMeters = 1500, primaryLang = "pt", targetWidth = 1200)
            if (geoResults.isNotEmpty()) {
                val bestGeo = geoResults.first()
                Log.d(TAG, "Resolved photo via Wikipedia Geosearch at ($latitude, $longitude): ${bestGeo.photoUrl}")
                return@withContext EnrichedPhoto(bestGeo.photoUrl, bestGeo.attribution, true)
            }
        }

        // 7. Contextual keyword & district matching from Lisbon catalog
        val (lisbonUrl, lisbonAttr) = LisbonLandmarkCatalog.resolveContextualPhoto(
            title = title,
            category = category,
            districtOrLocation = districtOrLocation
        )
        if (!lisbonAttr.endsWith("Lisbon Street Exploration")) {
            Log.i(TAG, "Wikimedia Commons resolution status: Retaining curated Lisbon photo for '$title' ($lisbonUrl)")
            return@withContext EnrichedPhoto(lisbonUrl, lisbonAttr, isVerifiedLandmark = false)
        }

        // 8. Contextual Keyword & Category fallback pool
        val (contextualUrl, attribution) = GlobalLandmarkCatalog.resolveGlobalContextualPhoto(
            title = title,
            category = category,
            districtOrLocation = districtOrLocation,
            seedKey = spotId ?: title
        )
        Log.i(TAG, "Wikimedia Commons resolution status: No direct match found for '$title', retaining fallback photo ($contextualUrl)")
        EnrichedPhoto(contextualUrl, attribution, false)
    }
}

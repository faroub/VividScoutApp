package com.example.data.repository

import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.location.DefaultLocationConfig
import com.example.data.network.EuropeanaCulturalService
import com.example.data.network.GeonamesService
import com.example.data.network.GeminiSpotService
import com.example.data.network.MunicipalOpenDataService
import com.example.data.network.OpenTripMapService
import com.example.data.network.OverpassRetrofitClient
import com.example.data.network.UnescoHeritageService
import com.example.data.network.WikidataSearchService
import com.example.data.places.MergedPlace
import com.example.data.places.PlaceRepository
import com.example.util.Geo
import com.example.util.SafeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remote discovery extraction from [ScoutGemRepository]: the single- and multi-source
 * spot syncs that pull live provider results into Room, plus the shared retry helper.
 * Each provider is invoked at most once per request; failures are logged and never
 * discard another provider's results.
 *
 * [recordRecentSearch] and [enrichmentTrigger] are supplied by the repository so the
 * repository's open method overrides (test fakes) still intercept these calls.
 */
internal class GemDiscoverySync(
    private val scoutDao: ScoutDao,
    private val placeRepository: PlaceRepository?,
    private val overpassClient: OverpassRetrofitClient,
    private val geminiSpotService: GeminiSpotService,
    private val openTripMapService: OpenTripMapService,
    private val geonamesService: GeonamesService,
    private val unescoService: UnescoHeritageService,
    private val europeanaService: EuropeanaCulturalService,
    private val municipalService: MunicipalOpenDataService,
    private val wikidataSearchService: WikidataSearchService,
    private val recordRecentSearch: suspend (String, String) -> Unit,
    private val enrichmentTrigger: suspend (List<ScoutSpotEntity>) -> Unit
) {

    private companion object {
        const val TAG = "ScoutGemRepository"
    }

    /**
     * Discovers real, authentic, named spots near the user's location using Firebase AI Logic (Gemini 3.8 Flash).
     * Falls back to Overpass API / offline generator if AI discovery is offline or unavailable.
     */
    suspend fun syncLiveOsmGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        radiusMeters: Int = 1500,
        category: String = "all",
        customQuery: String = ""
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (customQuery.isNotBlank()) {
            recordRecentSearch(customQuery, category)
        }
        // First try Firebase AI Logic (Gemini 3.5 Flash-Lite / 3.8 Flash) for high quality verified spots
        val geminiResult = geminiSpotService.discoverGems(userLat, userLng, category, radiusMeters, customQuery)
        if (geminiResult.isSuccess) {
            val spots = geminiResult.getOrNull().orEmpty()
            if (spots.isNotEmpty()) {
                scoutDao.clearFallbackSpots()
                deduplicateAndInsert(spots)
                return@withContext Result.success(spots.size)
            }
        }

        // Fallback to Overpass API scraper with exponential backoff if AI is unavailable or returned empty
        retryWithExponentialBackoff(maxAttempts = 2, initialDelayMs = 500L, factor = 1.5) {
            val fetchResult = overpassClient.fetchNearbyNodes(userLat, userLng, radiusMeters, category)
            fetchResult.fold(
                onSuccess = { osmEntities ->
                    if (osmEntities.isNotEmpty()) {
                        scoutDao.clearFallbackSpots()
                    }
                    val rawSpots = if (osmEntities.isNotEmpty()) {
                        osmEntities
                    } else {
                        overpassClient.generateNearbyFallbackSpots(userLat, userLng, 6, category)
                    }
                    val spotsWithinRadius = if (userLat != 0.0 || userLng != 0.0) {
                        rawSpots.filter { spot ->
                            com.example.data.location.LocationTracker.distanceBetween(userLat, userLng, spot.latitude, spot.longitude) <= 50000f
                        }
                    } else {
                        rawSpots
                    }
                    deduplicateAndInsert(spotsWithinRadius)
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichmentTrigger(spotsWithinRadius)
                    }
                    Result.success(spotsWithinRadius.size)
                },
                onFailure = { _ ->
                    val fallbackSpots = overpassClient.generateNearbyFallbackSpots(userLat, userLng, 6, category)
                    deduplicateAndInsert(fallbackSpots)
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichmentTrigger(fallbackSpots)
                    }
                    Result.success(fallbackSpots.size)
                }
            )
        }
    }

    /**
     * Fetches touristic spots directly from OpenTripMap global POI service into Room.
     */
    suspend fun syncOpenTripMapGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        radiusMeters: Int = 3000
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = openTripMapService.fetchTouristicPlaces(userLat, userLng, radiusMeters)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichmentTrigger(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches heritage monuments and landmarks from Wikidata SPARQL graph service into Room.
     */
    suspend fun syncWikidataGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        category: String = "all"
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = wikidataSearchService.fetchNearbyWikidataSpots(userLat, userLng, category)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichmentTrigger(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches gazetteer geographical POIs from GeoNames database into Room.
     */
    suspend fun syncGeonamesGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = geonamesService.fetchNearbyGeonamesPOIs(userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichmentTrigger(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches official UNESCO World Heritage properties into Room.
     */
    suspend fun syncUnescoGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = unescoService.fetchNearbyUnescoSites(userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichmentTrigger(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches European cultural heritage repository items into Room.
     */
    suspend fun syncEuropeanaGems(
        locationName: String = "Lisbon",
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = europeanaService.fetchEuropeanaCulturalSpots(locationName, userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichmentTrigger(spots)
            }
        }
        Result.success(spots.size)
    }

    /**
     * Fetches municipal open data public art, walking trails, and parks into Room.
     */
    suspend fun syncMunicipalGems(
        cityName: String = "Lisbon",
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = withContext(Dispatchers.IO) {
        val spots = municipalService.fetchMunicipalDataSpots(cityName, userLat, userLng)
        if (spots.isNotEmpty()) {
            deduplicateAndInsert(spots)
            CoroutineScope(Dispatchers.IO).launch {
                enrichmentTrigger(spots)
            }
        }
        Result.success(spots.size)
    }

    fun mergedPlaceToEntity(merged: MergedPlace, userLat: Double, userLng: Double): ScoutSpotEntity {
        val primarySource = merged.sources.firstOrNull()
        val providerBadge = primarySource?.sourceName ?: "Multi-Source"
        val sourceId = primarySource?.sourceId ?: "merged_${merged.latitude}_${merged.longitude}"
        val spotId = "place_${sourceId.hashCode()}"

        val distanceMeters = Geo.calculateDistanceMeters(userLat, userLng, merged.latitude, merged.longitude)
        val walkMins = Geo.calculateWalkMinutes(distanceMeters)
        val formattedDist = Geo.formatDistance(distanceMeters)

        val licenseText = merged.license ?: "Open Data Commons / CC BY-SA"
        val tagsString = merged.sources.joinToString("|||") { "${it.sourceName}:${it.sourceId}" }

        return ScoutSpotEntity(
            id = spotId,
            title = merged.primaryName,
            description = merged.primaryDescription.ifBlank { "Multi-source tourist attraction." },
            category = merged.category,
            distance = formattedDist,
            walkMinutes = walkMins,
            rating = null, // Never invent ratings
            reviewCount = null, // Never invent review counts
            imageUrl = merged.imageUrl ?: "",
            badgeLabel = providerBadge,
            statusBadge = "Aggregated Place",
            tagsRaw = tagsString,
            proTip = "Multi-source place ($providerBadge). License: $licenseText.",
            contributor = "@$providerBadge",
            latitude = merged.latitude,
            longitude = merged.longitude,
            locationName = "Nearby Landmark"
        )
    }

    /**
     * Queries all active touristic place sources simultaneously via PlaceRepository or direct source services.
     */
    suspend fun syncMultiSourceGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        cityName: String = "Lisbon",
        sourceIds: List<String> = listOf("open_trip_map", "wikivoyage_wikidata", "overpass_osm", "geonames_gazetteer", "unesco_world_heritage", "europeana_cultural", "municipal_open_data", "firebase_gemini_ai")
    ): Result<Int> = withContext(Dispatchers.IO) {
        val enabledSources = sourceIds.toSet()
        var totalFetched = 0

        // 1) Cached spatial pipeline: the authoritative implementation for the four
        //    geohash-tile-cached providers. Issued once (no duplicate per-source
        //    requests) and only for the subset the caller actually requested.
        val requestedPipelineIds = enabledSources.intersect(ScoutGemRepository.PIPELINE_SOURCE_IDS)
        if (placeRepository != null && requestedPipelineIds.isNotEmpty()) {
            try {
                val placeResult = placeRepository.getNearbyPlaces(
                    userLat, userLng, radiusMeters = 3000, sourceIds = requestedPipelineIds
                )
                val mergedEntities = placeResult.places.map { mergedPlaceToEntity(it, userLat, userLng) }
                if (mergedEntities.isNotEmpty()) {
                    deduplicateAndInsert(mergedEntities)
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichmentTrigger(mergedEntities)
                    }
                } else if (placeResult.isStale) {
                    SafeLog.w(TAG, "Multi-source pipeline returned no places (offline cache unavailable for $requestedPipelineIds)")
                } else {
                    SafeLog.i(TAG, "Multi-source pipeline returned no places for $requestedPipelineIds")
                }
                placeResult.providerFailures.forEach { failure ->
                    // A provider outage is not "no places here": report it so partial
                    // results are never mistaken for complete coverage.
                    SafeLog.w(
                        TAG,
                        "Place provider ${failure.providerId} did not answer (kind=${failure.kind}); " +
                            "result is partial=${placeResult.isPartial}"
                    )
                }
                totalFetched += mergedEntities.size
            } catch (e: Exception) {
                // One failing provider must never discard the other providers' results.
                SafeLog.w(TAG, "Multi-source pipeline failed for $requestedPipelineIds: ${e.message}")
            }
        }

        // 2) Providers owned by this repository, each invoked exactly once and only
        //    when requested. Failures are logged, not propagated.
        if ("unesco_world_heritage" in enabledSources) {
            val result = try { syncUnescoGems(userLat, userLng) } catch (e: Exception) { Result.failure(e) }
            totalFetched += logProviderSync("unesco_world_heritage", result)
        }
        if ("europeana_cultural" in enabledSources) {
            val result = try { syncEuropeanaGems(cityName, userLat, userLng) } catch (e: Exception) { Result.failure(e) }
            totalFetched += logProviderSync("europeana_cultural", result)
        }
        if ("municipal_open_data" in enabledSources) {
            val result = try { syncMunicipalGems(cityName, userLat, userLng) } catch (e: Exception) { Result.failure(e) }
            totalFetched += logProviderSync("municipal_open_data", result)
        }

        // 3) Firebase AI Logic (Gemini) discovery, only when requested, with the
        //    curated Lisbon fallback already carrying non-invented coordinates.
        if ("firebase_gemini_ai" in enabledSources) {
            val result = try {
                val geminiSpots = geminiSpotService.discoverGems(userLat, userLng, radiusMeters = 3000)
                    .getOrNull().orEmpty()
                if (geminiSpots.isNotEmpty()) {
                    deduplicateAndInsert(geminiSpots)
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichmentTrigger(geminiSpots)
                    }
                }
                Result.success(geminiSpots.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
            totalFetched += logProviderSync("firebase_gemini_ai", result)
        }

        Result.success(totalFetched)
    }

    /**
     * Records a single provider's sync outcome as a diagnostic log line, returning
     * the number of places that provider contributed. Distinguishes provider errors
     * from legitimate empty results.
     */
    private fun logProviderSync(sourceId: String, result: Result<Int>): Int {
        return result.fold(
            onSuccess = { count ->
                if (count == 0) SafeLog.i(TAG, "Provider '$sourceId' returned no places")
                count
            },
            onFailure = { error ->
                SafeLog.w(TAG, "Provider '$sourceId' failed: ${error.message}")
                0
            }
        )
    }

    private suspend fun deduplicateAndInsert(spots: List<ScoutSpotEntity>) {
        val existingSpots = scoutDao.getAllSpotsList()
        val unique = mutableListOf<ScoutSpotEntity>()

        spots.forEach { newSpot ->
            val newTitleNorm = newSpot.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            val isDuplicate = existingSpots.any { existing ->
                val existingTitleNorm = existing.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                existing.id == newSpot.id ||
                (newTitleNorm.isNotBlank() && newTitleNorm == existingTitleNorm) ||
                (com.example.data.location.LocationTracker.distanceBetween(
                    existing.latitude, existing.longitude, newSpot.latitude, newSpot.longitude
                ) < 80f && existing.category == newSpot.category)
            } || unique.any { added ->
                val addedTitleNorm = added.title.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
                (newTitleNorm.isNotBlank() && newTitleNorm == addedTitleNorm) ||
                (com.example.data.location.LocationTracker.distanceBetween(
                    added.latitude, added.longitude, newSpot.latitude, newSpot.longitude
                ) < 80f && added.category == newSpot.category)
            }

            if (!isDuplicate) {
                unique.add(newSpot)
            }
        }

        if (unique.isNotEmpty()) {
            scoutDao.insertSpots(unique)
        }
    }

    /**
     * Executes a suspendable block with exponential backoff retries.
     */
    suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelayMs
        var lastResult: Result<T> = Result.failure(IllegalStateException("No attempts made"))

        for (attempt in 1..maxAttempts) {
            lastResult = block()
            if (lastResult.isSuccess) {
                if (attempt > 1) {
                    SafeLog.i("ScoutGemRepository", "Spot discovery succeeded on retry attempt #$attempt")
                }
                return lastResult
            }

            if (attempt < maxAttempts) {
                SafeLog.w(
                    "ScoutGemRepository",
                    "Spot discovery attempt #$attempt failed. Retrying in ${currentDelay}ms... Error: ${lastResult.exceptionOrNull()?.message}"
                )
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }

        SafeLog.e(
            "ScoutGemRepository",
            "All $maxAttempts attempts failed for spot discovery: ${lastResult.exceptionOrNull()?.message}"
        )
        return lastResult
    }
}

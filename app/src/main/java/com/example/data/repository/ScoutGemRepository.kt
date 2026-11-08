package com.example.data.repository

import com.example.util.SafeLog
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.RecentSearchDao
import com.example.data.local.RecentSearchEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.location.DefaultLocationConfig
import com.example.data.model.ScoutGem
import com.example.data.model.toScoutGem
import com.example.data.network.GeminiSpotService
import com.example.data.network.OverpassRetrofitClient
import com.example.data.places.MergedPlace
import com.example.data.places.PlaceRepository
import com.example.util.Geo
import com.example.util.LocationPhotoResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline-First Repository for ScoutGem locations.
 * Room is the single source of truth; live spot discovery results from Firebase AI Logic (Gemini 3.8 Flash)
 * and Overpass API are inserted into Room in the background so the app remains 100% functional offline.
 */
open class ScoutGemRepository(
    private val scoutDao: ScoutDao,
    private val discoveredGemDao: DiscoveredGemDao? = null,
    private val recentSearchDao: RecentSearchDao? = null,
    val placeRepository: PlaceRepository? = null,
    val overpassClient: OverpassRetrofitClient = OverpassRetrofitClient(),
    val geminiSpotService: GeminiSpotService = GeminiSpotService(),
    val openTripMapService: com.example.data.network.OpenTripMapService = com.example.data.network.OpenTripMapService(),
    val geonamesService: com.example.data.network.GeonamesService = com.example.data.network.GeonamesService(),
    val unescoService: com.example.data.network.UnescoHeritageService = com.example.data.network.UnescoHeritageService(),
    val europeanaService: com.example.data.network.EuropeanaCulturalService = com.example.data.network.EuropeanaCulturalService(),
    val municipalService: com.example.data.network.MunicipalOpenDataService = com.example.data.network.MunicipalOpenDataService(),
    val wikidataSearchService: com.example.data.network.WikidataSearchService = com.example.data.network.WikidataSearchService(),
    val photoResolver: com.example.util.PhotoResolver = com.example.util.LocationPhotoResolver
) {

    companion object {
        private const val TAG = "ScoutGemRepository"

        // The four provider ids served by the geohash-tile-cached PlaceRepository
        // pipeline; every other source id is owned by this repository directly.
        val PIPELINE_SOURCE_IDS = setOf("open_trip_map", "wikivoyage_wikidata", "overpass_osm", "geonames_gazetteer")

        // Photo-enrichment queue parameters.
        const val ENRICHMENT_BATCH_SIZE = 30
        const val MAX_PHOTO_ATTEMPTS = 5
        // Per-session safety cap: guards against an unbounded stream of fresh Room
        // inserts keeping the drain alive forever. Remaining candidates are picked
        // up by the next scheduled drain.
        const val MAX_ENRICHMENTS_PER_SESSION = 2000
        const val ENRICHMENT_DELAY_MS = 50L
    }

    open val recentSearches: Flow<List<RecentSearchEntity>> =
        recentSearchDao?.getRecentSearches() ?: flowOf(emptyList())

    open suspend fun recordRecentSearch(queryText: String, category: String = "all"): Unit = withContext(Dispatchers.IO) {
        val trimmed = queryText.trim()
        if (trimmed.isBlank() || recentSearchDao == null) return@withContext

        val existing = recentSearchDao.findByQueryText(trimmed)
        if (existing != null) {
            recentSearchDao.insertSearch(
                existing.copy(
                    timestamp = System.currentTimeMillis(),
                    category = category
                )
            )
        } else {
            recentSearchDao.insertSearch(
                RecentSearchEntity(
                    queryText = trimmed,
                    category = category,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        recentSearchDao.trimExcessSearches()
    }

    open suspend fun removeRecentSearch(id: String): Int = withContext(Dispatchers.IO) {
        recentSearchDao?.deleteSearchById(id) ?: 0
    }

    open suspend fun clearAllRecentSearches(): Int = withContext(Dispatchers.IO) {
        recentSearchDao?.clearAllSearches() ?: 0
    }

    private val cacheMaintenance = GemCacheMaintenance(scoutDao)
    val lastCleanupReport: StateFlow<AutoCleanupReport?> = cacheMaintenance.lastCleanupReport

    /**
     * Diagnostic telemetry log flow for the Overpass API inspector.
     */
    val lastDiagnosticLog: kotlinx.coroutines.flow.StateFlow<com.example.data.network.OverpassDiagnosticLog?> =
        overpassClient.lastDiagnosticLog

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    /**
     * Reactive stream of all ScoutGems directly backed by Room database.
     * Automatically triggers reactive background photo resolution via Wikimedia Commons API
     * whenever new spot entities stream into Room.
     *
     * The observer only SCHEDULES enrichment: eligibility is re-derived from Room and the
     * queue state on every drain, so nothing is permanently excluded merely because it was
     * scheduled — candidates that a previous drain never reached are picked up again.
     */
    fun observeGems(
        userLat: Double = DefaultLocationConfig.activeFallbackLat,
        userLng: Double = DefaultLocationConfig.activeFallbackLng
    ): Flow<List<ScoutGem>> {
        return scoutDao.getAllSpots().map { spotEntities ->
            val schedulable = spotEntities.filter { spot ->
                photoResolver.isGenericOrStockPhoto(spot.imageUrl) &&
                    !completedPhotoIds.contains(spot.id) &&
                    !inProgressPhotoIds.contains(spot.id) &&
                    !photoEnrichment.isBackingOff(spot.id)
            }
            if (schedulable.isNotEmpty()) {
                repositoryScope.launch {
                    enrichSpotsPhotosInBackground(schedulable)
                }
            }
            spotEntities.map { entity -> entity.toScoutGem(userLat, userLng) }
        }
    }

    /**
     * Reactive stream of bookmarked ScoutGems backed by Room.
     */
    fun observeSavedGems(
        userLat: Double = DefaultLocationConfig.activeFallbackLat,
        userLng: Double = DefaultLocationConfig.activeFallbackLng
    ): Flow<List<ScoutGem>> {
        return scoutDao.getSavedSpots().map { spotEntities ->
            spotEntities.map { entity -> entity.toScoutGem(userLat, userLng) }
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

    suspend fun performAutoCleanup(
        userLat: Double = DefaultLocationConfig.activeFallbackLat,
        userLng: Double = DefaultLocationConfig.activeFallbackLng,
        maxAgeDays: Int = 30,
        maxRadiusKm: Double = 100.0
    ): AutoCleanupReport = cacheMaintenance.performAutoCleanup(userLat, userLng, maxAgeDays, maxRadiusKm)

    /**
     * Manually forces a purge of all transient cached POIs from the local database.
     * Strictly preserves all saved (isSaved=1), discovered (isDiscovered=1), and drafted (isOfflineDraft=1) gems.
     */
    suspend fun clearTransientCache(): Int = cacheMaintenance.clearTransientCache()
    /**
     * Discovers real, authentic, named spots near the user's location using Firebase AI Logic (Gemini 3.8 Flash).
     * Falls back to Overpass API / offline generator if AI discovery is offline or unavailable.
     */
    open suspend fun syncLiveOsmGems(
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
                        enrichSpotsPhotosInBackground(spotsWithinRadius)
                    }
                    Result.success(spotsWithinRadius.size)
                },
                onFailure = { _ ->
                    val fallbackSpots = overpassClient.generateNearbyFallbackSpots(userLat, userLng, 6, category)
                    deduplicateAndInsert(fallbackSpots)
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichSpotsPhotosInBackground(fallbackSpots)
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
                enrichSpotsPhotosInBackground(spots)
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
                enrichSpotsPhotosInBackground(spots)
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
                enrichSpotsPhotosInBackground(spots)
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
                enrichSpotsPhotosInBackground(spots)
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
                enrichSpotsPhotosInBackground(spots)
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
                enrichSpotsPhotosInBackground(spots)
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
        val requestedPipelineIds = enabledSources.intersect(PIPELINE_SOURCE_IDS)
        if (placeRepository != null && requestedPipelineIds.isNotEmpty()) {
            try {
                val placeResult = placeRepository.getNearbyPlaces(
                    userLat, userLng, radiusMeters = 3000, sourceIds = requestedPipelineIds
                )
                val mergedEntities = placeResult.places.map { mergedPlaceToEntity(it, userLat, userLng) }
                if (mergedEntities.isNotEmpty()) {
                    deduplicateAndInsert(mergedEntities)
                    CoroutineScope(Dispatchers.IO).launch {
                        enrichSpotsPhotosInBackground(mergedEntities)
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
                        enrichSpotsPhotosInBackground(geminiSpots)
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
     * Toggles bookmark state in Room database.
     */
    suspend fun toggleSave(gemId: String, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        scoutDao.setSavedStatus(gemId, !currentlySaved)
    }

    /**
     * Toggles discovered state in Room database.
     */
    suspend fun toggleDiscovered(gemId: String, currentlyDiscovered: Boolean) = withContext(Dispatchers.IO) {
        val newDiscovered = !currentlyDiscovered
        val timestamp = if (newDiscovered) System.currentTimeMillis() else 0L
        scoutDao.setDiscoveredStatus(gemId, newDiscovered, timestamp)
    }

    /**
     * Executes an on-demand Overpass API diagnostic query and returns telemetry.
     */
    suspend fun runDiagnosticQuery(
        userLat: Double = 38.7112,
        userLng: Double = -9.1423,
        radiusMeters: Int = 1500
    ): com.example.data.network.OverpassDiagnosticLog = withContext(Dispatchers.IO) {
        val log = overpassClient.fetchDiagnosticLog(userLat, userLng, radiusMeters)
        if (!log.isFallbackTriggered && log.rawJson.isNotBlank()) {
            val adapter = com.squareup.moshi.Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
                .adapter(com.example.data.network.model.OverpassResponse::class.java)
            try {
                val response = adapter.fromJson(log.rawJson)
                if (response != null && response.elements.isNotEmpty()) {
                    val entities = overpassClient.mapElementsToEntities(response.elements, userLat, userLng)
                    if (entities.isNotEmpty()) {
                        scoutDao.insertSpots(entities)
                    }
                }
            } catch (e: Exception) {
                // Safe recovery: JSON parsing of raw Overpass telemetry failed, log warning telemetry and return raw log
                SafeLog.w("ScoutGemRepository", "Failed to parse Overpass diagnostic JSON response", e)
            }
        }
        log
    }

    private val photoEnrichment = GemPhotoEnrichment(scoutDao, photoResolver)

    // Photo-enrichment queue state, owned by GemPhotoEnrichment and delegated here
    // because observeGems and the enrichment tests read them through the repository surface.
    internal val inProgressPhotoIds: MutableSet<String> get() = photoEnrichment.inProgressPhotoIds
    internal val completedPhotoIds: MutableSet<String> get() = photoEnrichment.completedPhotoIds
    internal val failedPhotoAttempts: MutableMap<String, GemPhotoEnrichment.PhotoAttemptInfo> get() = photoEnrichment.failedPhotoAttempts

    // Deterministic test seams for backoff timing and request pacing; production
    // uses real wall-clock time and the 50 ms inter-request delay.
    internal var currentTimeMs: () -> Long
        get() = photoEnrichment.currentTimeMs
        set(value) { photoEnrichment.currentTimeMs = value }
    internal var enrichmentPacingMs: () -> Long
        get() = photoEnrichment.enrichmentPacingMs
        set(value) { photoEnrichment.enrichmentPacingMs = value }

    /**
     * Automatically enriches spots having placeholder or generic image URLs
     * by querying live Wikipedia & Wikimedia Commons APIs for 1200px HD resolution metadata
     * and caching the authentic image URLs directly in the Room database for permanent offline availability.
     *
     * Delegates to [GemPhotoEnrichment]; stays `open` so test fakes can replace the whole drain.
     */
    open suspend fun enrichSpotsPhotosInBackground(spots: List<ScoutSpotEntity> = emptyList()) =
        photoEnrichment.enrichSpotsPhotosInBackground(spots)

    /**
     * Triggered automatically when device transitions from offline to online state.
     * Resets failed photo attempts so queued spots can be retried.
     */
    suspend fun triggerPhotoMetadataReSync() = withContext(Dispatchers.IO) {
        failedPhotoAttempts.clear()
        inProgressPhotoIds.clear()
        val allSpots = scoutDao.getAllSpotsList()
        val unEnriched = allSpots.filter { photoResolver.isGenericOrStockPhoto(it.imageUrl) }
        if (unEnriched.isNotEmpty()) {
            val (savedSpots, feedSpots) = unEnriched.partition { it.isSaved }
            SafeLog.i(
                TAG,
                "Automatic Network Reconnection Re-Sync: Found ${unEnriched.size} spots needing photos (${savedSpots.size} saved/bookmarked, ${feedSpots.size} feed)."
            )
            enrichSpotsPhotosInBackground(savedSpots + feedSpots)
        }
    }

    /**
     * On-demand photo refresh for a single spot (e.g. from the SpotDetailBottomSheet).
     */
    suspend fun refreshSpotPhoto(spotId: String): Boolean = withContext(Dispatchers.IO) {
        val spot = scoutDao.getSpotById(spotId) ?: return@withContext false
        try {
            val resolved = com.example.util.LocationPhotoResolver.resolveEnrichedPhoto(
                title = spot.title,
                category = spot.category,
                districtOrLocation = spot.locationName,
                latitude = spot.latitude,
                longitude = spot.longitude,
                spotId = spot.id
            )
            if (resolved.url.isNotBlank() && !com.example.util.LocationPhotoResolver.isGenericOrStockPhoto(resolved.url)) {
                scoutDao.updateImageUrl(spot.id, resolved.url)
                return@withContext true
            }
        } catch (e: Exception) {
            SafeLog.w("ScoutGemRepository", "Failed on-demand photo refresh for $spotId: ${e.message}")
        }
        false
    }
}

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

    private val discoverySync = GemDiscoverySync(
        scoutDao = scoutDao,
        placeRepository = placeRepository,
        overpassClient = overpassClient,
        geminiSpotService = geminiSpotService,
        openTripMapService = openTripMapService,
        geonamesService = geonamesService,
        unescoService = unescoService,
        europeanaService = europeanaService,
        municipalService = municipalService,
        wikidataSearchService = wikidataSearchService,
        // Lambdas dispatch through this repository's open methods so test fakes
        // that override them still intercept discovery-side calls.
        recordRecentSearch = { query, category -> recordRecentSearch(query, category) },
        enrichmentTrigger = { spots -> enrichSpotsPhotosInBackground(spots) }
    )

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
    ): Result<T> = discoverySync.retryWithExponentialBackoff(maxAttempts, initialDelayMs, factor, block)

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
    ): Result<Int> = discoverySync.syncLiveOsmGems(userLat, userLng, radiusMeters, category, customQuery)

    /**
     * Fetches touristic spots directly from OpenTripMap global POI service into Room.
     */
    suspend fun syncOpenTripMapGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        radiusMeters: Int = 3000
    ): Result<Int> = discoverySync.syncOpenTripMapGems(userLat, userLng, radiusMeters)

    /**
     * Fetches heritage monuments and landmarks from Wikidata SPARQL graph service into Room.
     */
    suspend fun syncWikidataGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        category: String = "all"
    ): Result<Int> = discoverySync.syncWikidataGems(userLat, userLng, category)

    /**
     * Fetches gazetteer geographical POIs from GeoNames database into Room.
     */
    suspend fun syncGeonamesGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = discoverySync.syncGeonamesGems(userLat, userLng)

    /**
     * Fetches official UNESCO World Heritage properties into Room.
     */
    suspend fun syncUnescoGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = discoverySync.syncUnescoGems(userLat, userLng)

    /**
     * Fetches European cultural heritage repository items into Room.
     */
    suspend fun syncEuropeanaGems(
        locationName: String = "Lisbon",
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = discoverySync.syncEuropeanaGems(locationName, userLat, userLng)

    /**
     * Fetches municipal open data public art, walking trails, and parks into Room.
     */
    suspend fun syncMunicipalGems(
        cityName: String = "Lisbon",
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG
    ): Result<Int> = discoverySync.syncMunicipalGems(cityName, userLat, userLng)

    fun mergedPlaceToEntity(merged: MergedPlace, userLat: Double, userLng: Double): ScoutSpotEntity =
        discoverySync.mergedPlaceToEntity(merged, userLat, userLng)

    /**
     * Queries all active touristic place sources simultaneously via PlaceRepository or direct source services.
     */
    suspend fun syncMultiSourceGems(
        userLat: Double = DefaultLocationConfig.DEFAULT_LAT,
        userLng: Double = DefaultLocationConfig.DEFAULT_LNG,
        cityName: String = "Lisbon",
        sourceIds: List<String> = listOf("open_trip_map", "wikivoyage_wikidata", "overpass_osm", "geonames_gazetteer", "unesco_world_heritage", "europeana_cultural", "municipal_open_data", "firebase_gemini_ai")
    ): Result<Int> = discoverySync.syncMultiSourceGems(userLat, userLng, cityName, sourceIds)

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

package com.example.data.places

import com.example.util.Geo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Repository for spatial tile caching, fetching, merging, and serving places from external PlaceSources.
 *
 * Cache coverage is tracked by [PlaceCoverageEntity] rows recording, per provider, the
 * query center + radius of each SUCCESSFUL query. A new request reuses the cache for a
 * provider only when that provider's recorded coverage fully contains the new request
 * (geodesic containment, not just matching geohash tiles) and the data has not expired.
 * A provider that answered with ZERO places also produces a coverage row: an empty
 * successful answer is valid coverage information, while a provider FAILURE never is —
 * failures never write coverage rows, so no outage is ever mistaken for "no places here".
 *
 * Provider failures are isolated and reported: one failing provider never discards the
 * other providers' results, and failing providers never clear another provider's cached
 * data (the cache write merges previously cached rows back in).
 */
class PlaceRepository(
    private val cachedPlaceDao: CachedPlaceDao,
    val placeSources: List<PlaceSource> = emptyList(),
    private val placeCoverageDao: PlaceCoverageDao? = null
) {

    companion object {
        const val CACHE_EXPIRATION_MS = 7 * 24 * 3600 * 1000L // 7 days
        const val COVERAGE_STATUS_SUCCESS = "success"
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val attributionsType = Types.newParameterizedType(List::class.java, PlaceSourceAttribution::class.java)
    private val jsonAdapter = moshi.adapter<List<PlaceSourceAttribution>>(attributionsType)

    suspend fun getNearbyPlaces(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 3000,
        currentTimeMs: Long = System.currentTimeMillis(),
        sourceIds: Set<String>? = null
    ): PlaceRepositoryResult = withContext(Dispatchers.IO) {
        // Only the requested subset of the configured providers is invoked; a null
        // filter means every configured source is enabled.
        val activeSources = if (sourceIds == null) placeSources
            else placeSources.filter { it.sourceId in sourceIds }
        val requestedProviderIds = activeSources.map { it.sourceId }.toSet()
        val centerTile = GeohashUtils.encode(lat, lon, precision = 5)
        val coveringTiles = GeohashUtils.getCoveringTiles(lat, lon, radiusMeters)

        // 1. Provider coverage of the FRESH cache. A provider counts as covered only
        //    when a successful coverage record fully contains this exact request —
        //    same or smaller area, with the query center inside — and is not expired.
        //    Cache age alone never proves coverage: one previously fetched place does
        //    not prove the full requested area was queried, and records left over from
        //    a previously enabled provider must never be served for another selection.
        val coverageRows = placeCoverageDao?.getAll().orEmpty()
        val coveredProviderIds = requestedProviderIds.filter { providerId ->
            coverageCovers(coverageRows, providerId, lat, lon, radiusMeters, currentTimeMs)
        }.toSet()
        val providersNeedingFetch = activeSources.filter { it.sourceId !in coveredProviderIds }

        // 2. Every requested provider is covered by fresh successful coverage records:
        //    return the cached places of the requested providers, filtered by radius.
        //    No network call — even when the cached pages are empty, because a
        //    successful empty coverage record genuinely means "no places in this area".
        //    (When no coverage store is configured, fall back to the legacy
        //    attribution-based coverage signal so behavior stays defined.)
        val effectivelyCovered = if (placeCoverageDao == null) {
            legacyCoveredProviderIds(currentTimeMs, coveringTiles, requestedProviderIds)
        } else {
            coveredProviderIds
        }
        val providersToFetch =
            if (placeCoverageDao == null) activeSources.filter { it.sourceId !in effectivelyCovered }
            else providersNeedingFetch

        if (providersToFetch.isEmpty()) {
            val cachedEntities = cachedPlaceDao.getPlacesForTiles(coveringTiles)
            val freshCachedEntities = cachedEntities.filter {
                (currentTimeMs - it.fetchedTimestamp) < CACHE_EXPIRATION_MS
            }
            val cachedMerged = freshCachedEntities
                .map { entityToMergedPlace(it) }
                .filterRequestedProviders(requestedProviderIds)
                .filterByRadius(lat, lon, radiusMeters)
            return@withContext PlaceRepositoryResult(
                places = cachedMerged,
                isStale = false,
                sourceTileKey = centerTile
            )
        }

        // 3. Fetch ONLY the providers not covered by the fresh cache (one request
        //    each). Each provider's outcome is collected individually: a provider
        //    failure is isolated — its results are simply absent while the other
        //    providers' results survive — and reported to the caller.
        val fetchOutcomes = mutableListOf<Pair<String, Result<List<PlaceCandidate>>>>()
        val networkCandidates = mutableListOf<PlaceCandidate>()

        if (providersToFetch.isNotEmpty()) {
            try {
                coroutineScope {
                    val fetchJobs = providersToFetch.map { source ->
                        async { source.sourceId to source.fetchNear(lat, lon, radiusMeters) }
                    }
                    fetchOutcomes.addAll(fetchJobs.awaitAll())
                }
            } catch (_: Exception) {
                // A structural failure of the fan-out itself: treat all uncovered
                // providers as failed; the stale-cache fallback below applies.
                providersToFetch.forEach { source ->
                    fetchOutcomes.add(source.sourceId to Result.failure(PlaceSourceFailure.TemporaryFailure()))
                }
            }
        }

        val providerFailures = mutableListOf<ProviderFailure>()
        val succeededProviderIds = mutableListOf<String>()
        fetchOutcomes.forEach { (sourceId, res) ->
            if (res.isSuccess) {
                succeededProviderIds.add(sourceId)
                networkCandidates.addAll(res.getOrNull().orEmpty())
            } else {
                providerFailures.add(ProviderFailure(sourceId, failureKindOf(res.exceptionOrNull())))
            }
        }
        val networkSuccess = succeededProviderIds.isNotEmpty()

        // 4. If at least one requested provider delivered an answer, merge its
        //    candidates together with the fresh cached places (ALL of their providers,
        //    so the cache write never evicts a provider's rows merely because it was
        //    not re-requested this time or because another provider failed), update
        //    the Room cache for the tile, record coverage ONLY for the providers that
        //    actually succeeded, and return only the places that carry at least one
        //    requested provider.
        if (networkSuccess) {
            val cachedCandidates = freshCachedEntitiesFor(coveringTiles, currentTimeMs)
                .map { entityToMergedPlace(it) }
                .flatMap { mergedPlaceToCandidates(it) }
            val mergedPlaces = PlaceMerger.mergeCandidates(networkCandidates + cachedCandidates)
            val cacheEntities = mergedPlaces.map { mergedToEntity(it, centerTile, currentTimeMs) }

            cachedPlaceDao.replaceTilePlaces(centerTile, cacheEntities)
            placeCoverageDao?.upsertAll(
                succeededProviderIds.map { providerId ->
                    PlaceCoverageEntity(
                        providerId = providerId,
                        centerLat = lat,
                        centerLon = lon,
                        radiusMeters = radiusMeters,
                        status = COVERAGE_STATUS_SUCCESS,
                        fetchedAt = currentTimeMs
                    )
                }
            )

            val radiusFiltered = mergedPlaces
                .filterRequestedProviders(requestedProviderIds)
                .filterByRadius(lat, lon, radiusMeters)

            return@withContext PlaceRepositoryResult(
                places = radiusFiltered,
                isStale = false,
                sourceTileKey = centerTile,
                providerFailures = providerFailures
            )
        }

        // 5. Offline or every fetched provider failed: serve whatever the cache holds
        //    for the requested providers, marked stale. A failed request never
        //    overwrites previously cached places with an empty result, and empty
        //    coverage is never recorded for a failed provider. An empty cache for the
        //    selection yields an empty stale result — never another provider's records.
        val cachedEntities = cachedPlaceDao.getPlacesForTiles(coveringTiles)
        val staleMerged = cachedEntities
            .map { entityToMergedPlace(it) }
            .filterRequestedProviders(requestedProviderIds)
            .filterByRadius(lat, lon, radiusMeters)

        return@withContext if (staleMerged.isNotEmpty()) {
            PlaceRepositoryResult(
                places = staleMerged,
                isStale = true,
                sourceTileKey = centerTile,
                providerFailures = providerFailures
            )
        } else {
            PlaceRepositoryResult(
                places = emptyList(),
                isStale = true,
                sourceTileKey = centerTile,
                providerFailures = providerFailures
            )
        }
    }

    /**
     * True when [providerId]'s latest successful coverage record is fresh AND fully
     * contains this request: the distance from the recorded center to the new center
     * plus the new radius must not exceed the recorded radius. Exact geodesic
     * containment — matching geohash tiles alone are not sufficient.
     */
    private fun coverageCovers(
        coverageRows: List<PlaceCoverageEntity>,
        providerId: String,
        lat: Double,
        lon: Double,
        radiusMeters: Int,
        currentTimeMs: Long
    ): Boolean {
        val row = coverageRows.find { it.providerId == providerId } ?: return false
        if (row.status != COVERAGE_STATUS_SUCCESS) return false
        if (currentTimeMs - row.fetchedAt >= CACHE_EXPIRATION_MS) return false
        if (radiusMeters > row.radiusMeters) return false
        val centerDistance = Geo.calculateDistanceMeters(row.centerLat, row.centerLon, lat, lon)
        return !centerDistance.isNaN() && centerDistance + radiusMeters <= row.radiusMeters
    }

    /** Legacy coverage derivation (no coverage store configured): a fresh cached
     *  place carrying the provider's attribution signals coverage. */
    private suspend fun legacyCoveredProviderIds(
        currentTimeMs: Long,
        coveringTiles: List<String>,
        requestedProviderIds: Set<String>
    ): Set<String> {
        val freshCachedEntities = cachedPlaceDao.getPlacesForTiles(coveringTiles).filter {
            (currentTimeMs - it.fetchedTimestamp) < CACHE_EXPIRATION_MS
        }
        return freshCachedEntities
            .flatMap { providerIdsOf(it) }
            .intersect(requestedProviderIds)
    }

    private suspend fun freshCachedEntitiesFor(
        coveringTiles: List<String>,
        currentTimeMs: Long
    ): List<CachedPlaceEntity> = cachedPlaceDao.getPlacesForTiles(coveringTiles).filter {
        (currentTimeMs - it.fetchedTimestamp) < CACHE_EXPIRATION_MS
    }

    private fun failureKindOf(throwable: Throwable?): ProviderFailureKind = when (throwable) {
        is PlaceSourceFailure.AuthConfigFailure -> ProviderFailureKind.AUTH_CONFIG
        is PlaceSourceFailure.RateLimited -> ProviderFailureKind.RATE_LIMITED
        is PlaceSourceFailure.TemporaryFailure -> ProviderFailureKind.TEMPORARY
        else -> ProviderFailureKind.TEMPORARY
    }

    private fun providerIdsOf(entity: CachedPlaceEntity): Set<String> = try {
        jsonAdapter.fromJson(entity.sourcesJson).orEmpty()
            .mapNotNull { it.providerId.takeIf { id -> id.isNotBlank() } }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun List<MergedPlace>.filterRequestedProviders(requestedProviderIds: Set<String>): List<MergedPlace> =
        filter { place -> place.providerIds.any { it in requestedProviderIds } }

    private fun List<MergedPlace>.filterByRadius(lat: Double, lon: Double, radiusMeters: Int): List<MergedPlace> =
        filter { place ->
            val dist = Geo.calculateDistanceMeters(lat, lon, place.latitude, place.longitude)
            !dist.isNaN() && dist <= radiusMeters
        }

    /** Reconstructs candidates (one per attribution) so a cached merged place survives
     *  re-merging with freshly fetched candidates without losing its provenance. */
    private fun mergedPlaceToCandidates(place: MergedPlace): List<PlaceCandidate> =
        place.sources.map { attribution ->
            PlaceCandidate(
                sourceName = attribution.sourceName,
                sourceId = attribution.sourceId,
                providerId = attribution.providerId,
                name = place.primaryName,
                latitude = place.latitude,
                longitude = place.longitude,
                category = place.category,
                description = place.primaryDescription,
                imageUrl = place.imageUrl,
                sourceUrl = attribution.sourceUrl ?: place.sourceUrl,
                license = attribution.license ?: place.license,
                languageCode = place.languageCode
            )
        }

    private fun entityToMergedPlace(entity: CachedPlaceEntity): MergedPlace {
        val attributions = try {
            jsonAdapter.fromJson(entity.sourcesJson).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        return MergedPlace(
            primaryName = entity.primaryName,
            primaryDescription = entity.primaryDescription,
            latitude = entity.latitude,
            longitude = entity.longitude,
            category = entity.category,
            imageUrl = entity.imageUrl,
            sourceUrl = entity.sourceUrl,
            license = entity.license,
            languageCode = entity.languageCode,
            sources = attributions
        )
    }

    private fun mergedToEntity(merged: MergedPlace, tileKey: String, timestamp: Long): CachedPlaceEntity {
        val safeName = PlaceMerger.normalizeName(merged.primaryName).replace(" ", "_")
        val placeId = "merged_${merged.latitude}_${merged.longitude}_$safeName"
        val sourcesJson = try {
            jsonAdapter.toJson(merged.sources)
        } catch (e: Exception) {
            "[]"
        }
        return CachedPlaceEntity(
            placeId = placeId,
            primaryName = merged.primaryName,
            primaryDescription = merged.primaryDescription,
            latitude = merged.latitude,
            longitude = merged.longitude,
            category = merged.category,
            imageUrl = merged.imageUrl,
            sourceUrl = merged.sourceUrl,
            license = merged.license,
            languageCode = merged.languageCode,
            sourcesJson = sourcesJson,
            tileKey = tileKey,
            fetchedTimestamp = timestamp
        )
    }
}

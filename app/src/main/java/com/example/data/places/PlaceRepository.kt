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
 */
class PlaceRepository(
    private val cachedPlaceDao: CachedPlaceDao,
    val placeSources: List<PlaceSource> = emptyList()
) {

    companion object {
        const val CACHE_EXPIRATION_MS = 7 * 24 * 3600 * 1000L // 7 days
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

        // 1. Cached places across covering tiles, split by age into fresh windows.
        val cachedEntities = cachedPlaceDao.getPlacesForTiles(coveringTiles)
        val freshCachedEntities = cachedEntities.filter {
            (currentTimeMs - it.fetchedTimestamp) < CACHE_EXPIRATION_MS
        }

        // 2. Provider coverage of the FRESH cache: a requested provider counts as
        // covered only when a fresh cached place carries that provider's attribution.
        // Cache age alone never proves coverage — records left over from a previously
        // enabled provider must never be served for a different provider selection.
        val coveredProviderIds = freshCachedEntities
            .flatMap { providerIdsOf(it) }
            .intersect(requestedProviderIds)
        val providersNeedingFetch = activeSources.filter { it.sourceId !in coveredProviderIds }

        // 3. Every requested provider is covered by fresh cache: return cached places
        // of the requested providers only, filtered by radius. No network call.
        if (providersNeedingFetch.isEmpty() && freshCachedEntities.isNotEmpty()) {
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

        // 4. Fetch ONLY the providers not covered by the fresh cache (one request
        // each). A provider failure is isolated: its results are simply absent while
        // the other providers' results survive.
        val networkCandidates = mutableListOf<PlaceCandidate>()
        var networkSuccess = false

        if (providersNeedingFetch.isNotEmpty()) {
            try {
                coroutineScope {
                    val fetchJobs = providersNeedingFetch.map { source ->
                        async {
                            source.fetchNear(lat, lon, radiusMeters)
                        }
                    }
                    val results = fetchJobs.awaitAll()
                    results.forEach { res ->
                        if (res.isSuccess) {
                            networkSuccess = true
                            networkCandidates.addAll(res.getOrNull().orEmpty())
                        }
                    }
                }
            } catch (_: Exception) {
                networkSuccess = false
            }
        }

        // 5. If the missing providers delivered candidates, merge them together with
        // the fresh cached places (ALL of their providers, so the cache write never
        // evicts a provider's rows merely because it was not re-requested this time),
        // update the Room cache for the tile, and return only the places that carry
        // at least one requested provider.
        if (networkSuccess) {
            val cachedCandidates = freshCachedEntities
                .map { entityToMergedPlace(it) }
                .flatMap { mergedPlaceToCandidates(it) }
            val mergedPlaces = PlaceMerger.mergeCandidates(networkCandidates + cachedCandidates)
            val cacheEntities = mergedPlaces.map { mergedToEntity(it, centerTile, currentTimeMs) }

            cachedPlaceDao.replaceTilePlaces(centerTile, cacheEntities)

            val radiusFiltered = mergedPlaces
                .filterRequestedProviders(requestedProviderIds)
                .filterByRadius(lat, lon, radiusMeters)

            return@withContext PlaceRepositoryResult(
                places = radiusFiltered,
                isStale = false,
                sourceTileKey = centerTile
            )
        }

        // 6. Offline or every missing-provider fetch failed: serve whatever the cache
        // holds for the requested providers, marked stale. An empty cache for the
        // selection yields an empty stale result — never another provider's records.
        val staleMerged = cachedEntities
            .map { entityToMergedPlace(it) }
            .filterRequestedProviders(requestedProviderIds)
            .filterByRadius(lat, lon, radiusMeters)

        return@withContext if (staleMerged.isNotEmpty()) {
            PlaceRepositoryResult(
                places = staleMerged,
                isStale = true,
                sourceTileKey = centerTile
            )
        } else {
            PlaceRepositoryResult(
                places = emptyList(),
                isStale = true,
                sourceTileKey = centerTile
            )
        }
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

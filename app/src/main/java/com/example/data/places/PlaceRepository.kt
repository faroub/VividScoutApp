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
    private val placeSources: List<PlaceSource> = emptyList()
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
        currentTimeMs: Long = System.currentTimeMillis()
    ): PlaceRepositoryResult = withContext(Dispatchers.IO) {
        val centerTile = GeohashUtils.encode(lat, lon, precision = 5)
        val coveringTiles = GeohashUtils.getCoveringTiles(lat, lon, radiusMeters)

        // 1. Look up cached places across covering neighbor tiles
        val cachedEntities = cachedPlaceDao.getPlacesForTiles(coveringTiles)

        val isCacheFresh = cachedEntities.isNotEmpty() &&
                cachedEntities.all { (currentTimeMs - it.fetchedTimestamp) < CACHE_EXPIRATION_MS }

        // 2. If fresh cache exists (< 7 days old), filter by distance and return
        if (isCacheFresh) {
            val cachedMerged = cachedEntities.map { entityToMergedPlace(it) }
                .filter { place ->
                    val dist = Geo.calculateDistanceMeters(lat, lon, place.latitude, place.longitude)
                    !dist.isNaN() && dist <= radiusMeters
                }

            return@withContext PlaceRepositoryResult(
                places = cachedMerged,
                isStale = false,
                sourceTileKey = centerTile
            )
        }

        // 3. If missing or stale, fetch from enabled PlaceSources concurrently
        val networkCandidates = mutableListOf<PlaceCandidate>()
        var networkSuccess = false

        if (placeSources.isNotEmpty()) {
            try {
                coroutineScope {
                    val fetchJobs = placeSources.map { source ->
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

        // 4. If network call succeeded, merge with PlaceMerger and update Room cache transactionally
        if (networkSuccess && networkCandidates.isNotEmpty()) {
            val mergedPlaces = PlaceMerger.mergeCandidates(networkCandidates)
            val cacheEntities = mergedPlaces.map { mergedToEntity(it, centerTile, currentTimeMs) }

            cachedPlaceDao.replaceTilePlaces(centerTile, cacheEntities)

            val radiusFiltered = mergedPlaces.filter { place ->
                val dist = Geo.calculateDistanceMeters(lat, lon, place.latitude, place.longitude)
                !dist.isNaN() && dist <= radiusMeters
            }

            return@withContext PlaceRepositoryResult(
                places = radiusFiltered,
                isStale = false,
                sourceTileKey = centerTile
            )
        }

        // 5. If network fails or yields no candidates, fallback to cached data marked as stale
        if (cachedEntities.isNotEmpty()) {
            val staleMerged = cachedEntities.map { entityToMergedPlace(it) }
                .filter { place ->
                    val dist = Geo.calculateDistanceMeters(lat, lon, place.latitude, place.longitude)
                    !dist.isNaN() && dist <= radiusMeters
                }

            return@withContext PlaceRepositoryResult(
                places = staleMerged,
                isStale = true,
                sourceTileKey = centerTile
            )
        }

        PlaceRepositoryResult(
            places = emptyList(),
            isStale = true,
            sourceTileKey = centerTile
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

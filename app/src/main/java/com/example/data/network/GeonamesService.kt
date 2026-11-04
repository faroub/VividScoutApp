package com.example.data.network

import com.example.data.local.ScoutSpotEntity
import com.example.data.places.GeonamesSource
import com.example.util.Geo
import com.example.util.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service to retrieve geographical gazetteer POIs, hilltops, historical monuments,
 * and geographical features from GeoNames database via secure backend Cloud Function.
 */
class GeonamesService(
    private val geonamesSource: GeonamesSource = GeonamesSource()
) {

    companion object {
        private const val TAG = "GeonamesService"
    }

    /**
     * Fetches nearby geographical POIs and gazetteer landmarks from GeoNames via secure nearbyPlaces Cloud Function.
     * Enforces strict coordinate validation, radius filtering, and zero demo credentials in Android source code.
     */
    suspend fun fetchNearbyGeonamesPOIs(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 3000
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        // Validate input coordinates
        if (lat.isNaN() || !lat.isFinite() || lon.isNaN() || !lon.isFinite() ||
            lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            SafeLog.w(TAG, "Invalid coordinates provided to GeonamesService: lat=$lat, lon=$lon")
            return@withContext emptyList()
        }

        val result = geonamesSource.fetchNear(lat, lon, radiusMeters)
        if (result.isFailure) return@withContext emptyList()

        val candidates = result.getOrNull().orEmpty()
        val verifiedSpots = mutableListOf<ScoutSpotEntity>()

        for (candidate in candidates) {
            val spotLat = candidate.latitude
            val spotLon = candidate.longitude

            // Validate candidate coordinates (reject NaN, out of bounds, or invalid Null Island)
            if (spotLat.isNaN() || !spotLat.isFinite() || spotLon.isNaN() || !spotLon.isFinite() ||
                spotLat < -90.0 || spotLat > 90.0 || spotLon < -180.0 || spotLon > 180.0 ||
                (spotLat == 0.0 && spotLon == 0.0)) {
                continue
            }

            // Enforce exact geodesic radius filtering
            val distMeters = Geo.calculateDistanceMeters(lat, lon, spotLat, spotLon)
            if (distMeters.isNaN() || distMeters > radiusMeters) {
                continue
            }

            verifiedSpots.add(
                ScoutSpotEntity(
                    id = candidate.sourceId,
                    title = candidate.name,
                    description = candidate.description.ifBlank { "Gazetteer landmark cataloged on GeoNames database." },
                    category = candidate.category,
                    distance = Geo.formatDistance(distMeters),
                    walkMinutes = Geo.calculateWalkMinutes(distMeters),
                    rating = null, // Never invent ratings
                    reviewCount = null, // Never invent review counts
                    imageUrl = candidate.imageUrl.orEmpty(),
                    badgeLabel = "GeoNames",
                    statusBadge = "GeoNames Gazetteer",
                    tagsRaw = "geonames|||gazetteer|||${candidate.category}",
                    proTip = "GeoNames gazetteer entry (${candidate.name}). License: CC BY 4.0.",
                    contributor = "@geonames_gazetteer",
                    latitude = spotLat,
                    longitude = spotLon,
                    locationName = "GeoNames POI"
                )
            )
        }

        verifiedSpots
    }
}

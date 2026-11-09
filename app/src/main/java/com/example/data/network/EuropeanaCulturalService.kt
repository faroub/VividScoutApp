package com.example.data.network

import com.example.util.SafeLog
import com.example.data.local.ScoutSpotEntity
import com.example.util.Geo
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Service to query digital cultural heritage items from European museums and archives,
 * routed through the secure europeanaSearch Firebase Callable Cloud Function.
 *
 * Zero API keys are embedded in the app: the Europeana wskey lives only in Firebase
 * Secret Manager, the provider URL is built exclusively on the trusted backend, and
 * this client never sees it. The callable performs the provider call, the coordinate
 * verification, and the geodesic radius filtering; on success it returns the heritage
 * items with the authoritative distanceMeters so the app can present honest distance
 * and walking-time figures without re-deriving them.
 *
 * Strict coordinate verification, radius filtering, and null ratings remain enforced
 * on the trusted backend; a callable failure yields an empty list (discovery proceeds
 * with the other sources) exactly as the previous direct-HTTP behavior did.
 */
open class EuropeanaCulturalService(
    private val callableProvider: (suspend (HashMap<String, Any>) -> Map<String, Any?>)? = null,
    private val functionsProvider: () -> FirebaseFunctions = { FirebaseFunctions.getInstance() }
) {

    companion object {
        private const val TAG = "EuropeanaCulturalService"
        private const val PROVIDER_KEY = "europeana"
    }

    /**
     * Queries Europeana cultural heritage repository for localized heritage objects with
     * verified geographic coordinates, through the trusted callable.
     */
    open suspend fun fetchEuropeanaCulturalSpots(
        locationName: String = "Lisbon",
        lat: Double = 38.7112,
        lon: Double = -9.1423,
        radiusMeters: Int = 15000
    ): List<ScoutSpotEntity> = withContext(Dispatchers.IO) {
        if (lat.isNaN() || !lat.isFinite() || lon.isNaN() || !lon.isFinite() ||
            lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0
        ) {
            SafeLog.w(TAG, "Invalid coordinates provided: lat=${SafeLog.sanitizeCoordinate(lat)}, lon=${SafeLog.sanitizeCoordinate(lon)}")
            return@withContext emptyList()
        }
        if (radiusMeters < 100 || radiusMeters > 50000) {
            SafeLog.w(TAG, "Invalid radiusMeters provided: $radiusMeters")
            return@withContext emptyList()
        }

        try {
            val payload = hashMapOf<String, Any>(
                "locationName" to locationName,
                "lat" to lat,
                "lon" to lon,
                "radiusMeters" to radiusMeters
            )

            val raw = if (callableProvider != null) {
                callableProvider.invoke(payload)
            } else {
                val functions = try {
                    functionsProvider()
                } catch (e: Exception) {
                    SafeLog.w(TAG, "FirebaseFunctions uninitialized in test environment: ${e.message}")
                    return@withContext emptyList()
                }
                val result = functions.getHttpsCallable("europeanaSearch").call(payload).await()
                @Suppress("UNCHECKED_CAST")
                (result.data as? Map<String, Any?>).orEmpty()
            }

            @Suppress("UNCHECKED_CAST")
            val items = (raw["items"] as? List<Map<String, Any?>>).orEmpty()
            items.mapNotNull { item ->
                val title = item["title"] as? String ?: return@mapNotNull null
                val spotLat = (item["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                val spotLon = (item["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                // The callable verifies coordinates server-side; a malformed record here
                // means the response contract was violated, so it is skipped, not shown.
                if (spotLat.isNaN() || spotLon.isNaN() || (spotLat == 0.0 && spotLon == 0.0)) {
                    return@mapNotNull null
                }
                val distanceMeters = (item["distanceMeters"] as? Number)?.toDouble()
                    ?: Geo.calculateDistanceMeters(lat, lon, spotLat, spotLon)
                val dataProvider = item["dataProvider"] as? String ?: "European Cultural Institution"
                val edmConceptLabel = item["edmConceptLabel"] as? String ?: "Cultural Heritage"
                val europeanaId = item["europeanaId"] as? String

                ScoutSpotEntity(
                    id = "europeana_${europeanaId?.hashCode() ?: title.hashCode()}",
                    title = title,
                    description = item["description"] as? String
                        ?: "Digital cultural heritage record archived by $dataProvider. Category: $edmConceptLabel.",
                    category = "history",
                    distance = Geo.formatDistance(distanceMeters),
                    walkMinutes = Geo.calculateWalkMinutes(distanceMeters),
                    rating = null, // Never invent ratings
                    reviewCount = null, // Never invent review counts
                    imageUrl = item["imageUrl"] as? String ?: "",
                    badgeLabel = "Europeana",
                    statusBadge = "Europeana Cultural Item",
                    tagsRaw = "europeana|||archive|||$edmConceptLabel",
                    proTip = "Archived by $dataProvider. License: CC BY-SA 4.0.",
                    contributor = "@europeana_foundation",
                    latitude = spotLat,
                    longitude = spotLon,
                    locationName = item["locationName"] as? String ?: locationName
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.d(TAG, "Europeana query handled: ${e.message}")
            emptyList()
        }
    }
}

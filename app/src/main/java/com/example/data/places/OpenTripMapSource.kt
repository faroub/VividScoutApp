package com.example.data.places

import com.example.util.SafeLog
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * OpenTripMap place data source routed through secure Firebase Callable Cloud Function (nearbyPlaces).
 * Ensures zero API keys are embedded in the Android APK.
 *
 * The callable returns the provider outcome contract: "ok" (the provider answered — even
 * with zero places, which is a valid answer), or a failure kind (transient / auth-config /
 * rate-limit). A failure is surfaced as a typed [PlaceSourceFailure] instead of an empty
 * success list, so the repository never treats a provider outage as "no places here".
 */
class OpenTripMapSource(
    private val callableProvider: (suspend (HashMap<String, Any>) -> Map<String, Any?>)? = null
) : PlaceSource {

    override val sourceId: String = "open_trip_map"

    companion object {
        private const val TAG = "OpenTripMapSource"
        private const val PROVIDER_KEY = "opentripmap"
    }

    override suspend fun fetchNear(
        lat: Double,
        lon: Double,
        radiusMeters: Int
    ): Result<List<PlaceCandidate>> = withContext(Dispatchers.IO) {
        // Validation: Coordinates
        if (lat.isNaN() || !lat.isFinite() || lon.isNaN() || !lon.isFinite() || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            SafeLog.w(TAG, "Invalid coordinates provided: lat=${SafeLog.sanitizeCoordinate(lat)}, lon=${SafeLog.sanitizeCoordinate(lon)}")
            return@withContext Result.success(emptyList())
        }

        // Validation: Radius
        if (radiusMeters < 100 || radiusMeters > 50000) {
            SafeLog.w(TAG, "Invalid radiusMeters provided: $radiusMeters")
            return@withContext Result.success(emptyList())
        }

        try {
            val payload = hashMapOf<String, Any>(
                "lat" to lat,
                "lon" to lon,
                "radiusMeters" to radiusMeters,
                "sources" to listOf("opentripmap")
            )

            val raw = if (callableProvider != null) {
                callableProvider.invoke(payload)
            } else {
                val functions = try {
                    FirebaseFunctions.getInstance()
                } catch (e: Exception) {
                    SafeLog.w(TAG, "FirebaseFunctions uninitialized in test environment: ${e.message}")
                    return@withContext Result.success(emptyList())
                }
                val result = functions.getHttpsCallable("nearbyPlaces").call(payload).await()
                @Suppress("UNCHECKED_CAST")
                (result.data as? Map<String, Any?>).orEmpty()
            }

            @Suppress("UNCHECKED_CAST")
            val rawList = (raw["places"] as? List<Map<String, Any?>>).orEmpty()
            @Suppress("UNCHECKED_CAST")
            val providerStatus = (raw["providerStatus"] as? Map<String, Any?>)?.get(PROVIDER_KEY) as? String

            when (providerStatus) {
                "transient" -> {
                    SafeLog.w(TAG, "OpenTripMap provider temporarily unavailable")
                    return@withContext Result.failure(PlaceSourceFailure.TemporaryFailure())
                }
                "auth-config" -> {
                    SafeLog.w(TAG, "OpenTripMap provider credentials are missing or rejected")
                    return@withContext Result.failure(PlaceSourceFailure.AuthConfigFailure())
                }
                "rate-limit" -> {
                    SafeLog.w(TAG, "OpenTripMap provider rate limited the request")
                    return@withContext Result.failure(PlaceSourceFailure.RateLimited())
                }
                // "ok", or an older backend not yet reporting provider status.
                else -> Unit
            }

            val candidates = rawList.mapNotNull { item ->
                val name = item["name"] as? String ?: return@mapNotNull null
                val itemLat = (item["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                val itemLon = (item["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null

                PlaceCandidate(
                    sourceName = item["sourceName"] as? String ?: "OpenTripMap API",
                    sourceId = item["sourceId"] as? String ?: "otm_${name.hashCode()}",
                    providerId = sourceId,
                    name = name,
                    latitude = itemLat,
                    longitude = itemLon,
                    category = item["category"] as? String ?: "sights",
                    description = item["description"] as? String ?: "",
                    imageUrl = item["imageUrl"] as? String,
                    sourceUrl = item["sourceUrl"] as? String,
                    license = item["license"] as? String ?: "ODbL",
                    languageCode = item["languageCode"] as? String ?: "en"
                )
            }

            Result.success(candidates)
        } catch (e: FirebaseFunctionsException) {
            val failure = when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    SafeLog.w(TAG, "Rate limit exceeded (max 60/hr/UID) querying OpenTripMap via nearbyPlaces")
                    PlaceSourceFailure.RateLimited()
                }
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> {
                    SafeLog.w(TAG, "Firebase Auth required to query OpenTripMap via nearbyPlaces")
                    PlaceSourceFailure.AuthConfigFailure()
                }
                else -> {
                    SafeLog.w(TAG, "Firebase Functions error querying OpenTripMap: ${e.message}")
                    PlaceSourceFailure.TemporaryFailure()
                }
            }
            Result.failure(failure)
        } catch (e: Exception) {
            SafeLog.w(TAG, "Network or parsing error querying OpenTripMap: ${e.message}")
            Result.failure(PlaceSourceFailure.TemporaryFailure())
        }
    }
}

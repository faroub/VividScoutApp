package com.example.data.places

import com.example.util.SafeLog
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * GeoNames place data source routed through secure Firebase Callable Cloud Function (nearbyPlaces).
 * Ensures zero usernames/credentials are embedded in the Android APK.
 */
class GeonamesSource(
    private val functionsProvider: () -> FirebaseFunctions = { FirebaseFunctions.getInstance() }
) : PlaceSource {

    companion object {
        private const val TAG = "GeonamesSource"
    }

    override suspend fun fetchNear(
        lat: Double,
        lon: Double,
        radiusMeters: Int
    ): Result<List<PlaceCandidate>> = withContext(Dispatchers.IO) {
        try {
            val functions = try {
                functionsProvider()
            } catch (e: Exception) {
                SafeLog.w(TAG, "FirebaseFunctions uninitialized in test environment: ${e.message}")
                return@withContext Result.success(emptyList())
            }

            val payload = hashMapOf<String, Any>(
                "lat" to lat,
                "lon" to lon,
                "radiusMeters" to radiusMeters,
                "sources" to listOf("geonames")
            )

            val result = functions.getHttpsCallable("nearbyPlaces").call(payload).await()
            val rawList = result.data as? List<Map<String, Any?>> ?: return@withContext Result.success(emptyList())

            val candidates = rawList.mapNotNull { item ->
                val name = item["name"] as? String ?: return@mapNotNull null
                val itemLat = (item["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                val itemLon = (item["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null

                PlaceCandidate(
                    sourceName = item["sourceName"] as? String ?: "GeoNames Gazetteer",
                    sourceId = item["sourceId"] as? String ?: "geonames_${name.hashCode()}",
                    name = name,
                    latitude = itemLat,
                    longitude = itemLon,
                    category = item["category"] as? String ?: "history",
                    description = item["description"] as? String ?: "",
                    imageUrl = item["imageUrl"] as? String,
                    sourceUrl = item["sourceUrl"] as? String,
                    license = item["license"] as? String ?: "CC BY 4.0",
                    languageCode = item["languageCode"] as? String ?: "en"
                )
            }

            Result.success(candidates)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    SafeLog.w(TAG, "Rate limit exceeded (max 60/hr/UID) querying GeoNames via nearbyPlaces")
                }
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> {
                    SafeLog.w(TAG, "Firebase Auth required to query GeoNames via nearbyPlaces")
                }
                else -> {
                    SafeLog.w(TAG, "Firebase Functions error querying GeoNames: ${e.message}")
                }
            }
            Result.success(emptyList())
        } catch (e: Exception) {
            SafeLog.w(TAG, "Network or parsing error querying GeoNames: ${e.message}")
            Result.success(emptyList())
        }
    }
}

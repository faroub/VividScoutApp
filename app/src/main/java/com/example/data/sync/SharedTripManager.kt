package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.R
import com.example.data.local.ScoutSpotEntity
import com.example.util.SafeLog
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class SharedSpotItem(
    val spotId: String = "",
    val title: String = "",
    val category: String = "sights",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val imageUrl: String? = null
)

data class SharedTrip(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Shared Scout Trip",
    val spots: List<SharedSpotItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val expireAt: Long = System.currentTimeMillis() + (30L * 24 * 3600 * 1000L) // 30 days TTL
) {
    val shareableLink: String
        get() = "https://vividscout.app/trip/$id"

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expireAt
}

object SharedTripManager {

    private const val TAG = "SharedTripManager"
    const val TTL_DURATION_MS = 30L * 24 * 3600 * 1000L // 30 days

    private fun getFirestore(context: Context): FirebaseFirestore? {
        return try {
            val app = if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            } ?: return null

            val databaseId = try {
                context.getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                ""
            }

            if (databaseId.isNotBlank()) {
                FirebaseFirestore.getInstance(app, databaseId)
            } else {
                FirebaseFirestore.getInstance(app)
            }
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to initialize Firestore instance for shared trips: ${e.message}")
            null
        }
    }

    /**
     * Creates a validated SharedTrip instance containing up to 50 valid spot items,
     * filtering out invalid coordinates or malformed spots, and assigning a 30-day TTL expiration.
     */
    fun createSharedTrip(
        title: String,
        spots: List<ScoutSpotEntity>,
        currentTimeMs: Long = System.currentTimeMillis()
    ): SharedTrip {
        val validSpots = spots.filter { spot ->
            !spot.latitude.isNaN() && !spot.longitude.isNaN() &&
            spot.latitude in -90.0..90.0 && spot.longitude in -180.0..180.0 &&
            !(spot.latitude == 0.0 && spot.longitude == 0.0) &&
            spot.title.isNotBlank()
        }.take(50)

        val spotItems = validSpots.map { spot ->
            SharedSpotItem(
                spotId = spot.id.take(128),
                title = spot.title.trim().take(120),
                category = spot.category.take(32),
                latitude = spot.latitude,
                longitude = spot.longitude,
                imageUrl = spot.imageUrl?.take(2048)
            )
        }

        val randomId = UUID.randomUUID().toString()

        return SharedTrip(
            id = randomId,
            title = title.trim().ifBlank { "Shared Scout Itinerary" }.take(120),
            spots = spotItems,
            createdAt = currentTimeMs,
            expireAt = currentTimeMs + TTL_DURATION_MS
        )
    }

    /**
     * Parses trip ID from deep link URIs:
     * - https://vividscout.app/trip/{tripId}
     * - vividscout://trip/{tripId}
     * - vividscout://trip?id={tripId}
     */
    fun parseTripIdFromUri(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString.trim())
            val path = uri.path ?: ""
            val queryId = uri.getQueryParameter("id")

            when {
                !queryId.isNullOrBlank() -> queryId.trim()
                path.startsWith("/trip/") -> path.removePrefix("/trip/").trim()
                path.startsWith("/") -> path.removePrefix("/").trim()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Persists shared trip document to Firestore under shared_trips/{tripId}
     * using the configured named Firestore database and Firestore Timestamps for TTL compatibility.
     */
    suspend fun publishSharedTripToFirestore(
        context: Context,
        trip: SharedTrip
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val app = if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseApp.getInstance() else FirebaseApp.initializeApp(context)
            if (app == null) return@withContext Result.failure(IllegalStateException("FirebaseApp uninitialized."))

            val auth = FirebaseAuth.getInstance(app)
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }

            // Payload mirrors the schema enforced server-side by functions/sharedTrips.js.
            val spotMaps = trip.spots.map { spot ->
                mapOf(
                    "spotId" to spot.spotId,
                    "title" to spot.title,
                    "category" to spot.category,
                    "latitude" to spot.latitude,
                    "longitude" to spot.longitude,
                    "imageUrl" to (spot.imageUrl ?: "")
                )
            }

            val payload = hashMapOf<String, Any>(
                "id" to trip.id,
                "title" to trip.title,
                "spots" to spotMaps,
                "createdAt" to trip.createdAt,
                "expireAt" to trip.expireAt
            )

            // Shared trips are written by the trusted publishSharedTrip Cloud Function
            // (firestore.rules denies direct client creation of shared_trips).
            val functions = FirebaseFunctions.getInstance(app)
            val result = functions.getHttpsCallable("publishSharedTrip").call(payload).await()

            @Suppress("UNCHECKED_CAST")
            val link = (result.data as? Map<*, *>)?.get("shareableLink") as? String
                ?: trip.shareableLink
            SafeLog.i(TAG, "Published shared trip '${trip.id}' via Cloud Function")
            Result.success(link)
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to publish shared trip to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Parses one waypoints element into a [SharedSpotItem], rejecting entries with
     * missing title or invalid coordinates.
     */
    private fun parseSharedSpotItem(item: Map<String, Any?>): SharedSpotItem? {
        val name = item["title"] as? String ?: return null
        val lat = (item["latitude"] as? Number)?.toDouble() ?: return null
        val lng = (item["longitude"] as? Number)?.toDouble() ?: return null

        if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0 || (lat == 0.0 && lng == 0.0)) {
            return null
        }

        return SharedSpotItem(
            spotId = item["spotId"] as? String ?: "",
            title = name,
            category = item["category"] as? String ?: "sights",
            latitude = lat,
            longitude = lng,
            imageUrl = item["imageUrl"] as? String
        )
    }

    /**
     * Fetches shared trip document by exact document ID from Firestore.
     * Enforces 30-day TTL expiration check.
     */
    suspend fun fetchSharedTripFromFirestore(
        context: Context,
        tripId: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Result<SharedTrip> = withContext(Dispatchers.IO) {
        try {
            val firestore = getFirestore(context)
                ?: return@withContext Result.failure(IllegalStateException("Firestore instance unavailable."))

            val docSnap = firestore.collection("shared_trips").document(tripId).get().await()

            if (!docSnap.exists()) {
                return@withContext Result.failure(IllegalStateException("Shared trip not found or expired."))
            }

            val expireAt = when (val expireVal = docSnap.get("expireAt")) {
                is Timestamp -> expireVal.toDate().time
                is Number -> expireVal.toLong()
                else -> 0L
            }

            if (currentTimeMs >= expireAt) {
                return@withContext Result.failure(IllegalStateException("This shared trip link has expired (30-day limit)."))
            }

            val title = docSnap.getString("title") ?: "Shared Scout Trip"
            val createdAt = when (val createdVal = docSnap.get("createdAt")) {
                is Timestamp -> createdVal.toDate().time
                is Number -> createdVal.toLong()
                else -> currentTimeMs
            }

            // Accepts the list serialization written by the publishSharedTrip Cloud
            // Function (and legacy documents shared before schema enforcement).
            @Suppress("UNCHECKED_CAST")
            val rawSpots = docSnap.get("spots") as? List<Map<String, Any?>> ?: emptyList()
            val spotItems = rawSpots.mapNotNull { item -> parseSharedSpotItem(item) }

            val trip = SharedTrip(
                id = tripId,
                title = title,
                spots = spotItems,
                createdAt = createdAt,
                expireAt = expireAt
            )

            Result.success(trip)
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to fetch shared trip '$tripId': ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Launches Android native sharing intent for a shared trip link.
     */
    fun shareTripNativeIntent(context: Context, trip: SharedTrip) {
        val shareText = "🗺️ Check out my shared VividScout walking itinerary: '${trip.title}'\n${trip.shareableLink}\n\n(Link expires in 30 days)"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val chooserIntent = Intent.createChooser(sendIntent, "Share Walking Itinerary")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    fun isTripExpired(trip: SharedTrip, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return currentTimeMs >= trip.expireAt
    }
}

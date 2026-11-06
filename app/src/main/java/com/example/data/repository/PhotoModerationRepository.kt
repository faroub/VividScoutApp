package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.util.PhotoProcessor
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class SpotPhotoData(
    val id: String,
    val spotId: String,
    val photoUrl: String,
    val ownerUid: String,
    val reportsCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isHidden: Boolean
        get() = PhotoProcessor.isPhotoHiddenByReports(reportsCount)
}

/**
 * Photo workflow surface: real photo upload from an Android Photo Picker [Uri],
 * atomic reporting of an existing photo document by its real Firestore document ID,
 * and a live stream of a spot's non-moderated photos.
 */
interface SpotPhotoRepository {
    suspend fun uploadPhoto(context: Context, spotId: String, uri: Uri): Result<SpotPhotoData>
    suspend fun reportPhoto(photoId: String): Result<Int>
    fun photosForSpotFlow(spotId: String): Flow<List<SpotPhotoData>>
}

/**
 * Repository enforcing atomic photo reporting, duplicate report prevention,
 * and 3-report moderation hiding logic.
 */
class PhotoModerationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val storageService: FirebasePhotoStorageService = FirebasePhotoStorageService()
) : SpotPhotoRepository {

    companion object {
        private const val TAG = "PhotoModerationRepo"
        private const val PHOTOS_COLLECTION = "spot_photos"
    }

    override suspend fun uploadPhoto(context: Context, spotId: String, uri: Uri): Result<SpotPhotoData> =
        storageService.uploadSpotPhotoFromUri(spotId = spotId, context = context, uri = uri)

    /**
     * Streams the photo metadata documents of one spot, mapped from Firestore snapshots.
     * Documents are mapped field-by-field (Firestore cannot reflect into Kotlin data
     * classes); photos hidden by the real moderation state are filtered by callers via
     * [SpotPhotoData.isHidden].
     */
    override fun photosForSpotFlow(spotId: String): Flow<List<SpotPhotoData>> = callbackFlow {
        val registration = firestore.collection(PHOTOS_COLLECTION)
            .whereEqualTo("spotId", spotId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    SafeLog.w(TAG, "Photo stream for spot '$spotId' failed: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                val photos = snapshot?.documents?.mapNotNull { document ->
                    val photoUrl = document.getString("photoUrl") ?: return@mapNotNull null
                    val ownerUid = document.getString("ownerUid") ?: return@mapNotNull null
                    SpotPhotoData(
                        id = document.id,
                        spotId = document.getString("spotId") ?: spotId,
                        photoUrl = photoUrl,
                        ownerUid = ownerUid,
                        reportsCount = document.getLong("reportsCount")?.toInt() ?: 0,
                        timestamp = document.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
                trySend(photos)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Executes atomic photo report creation and counter increment in a single transaction.
     * Prevents duplicate reports from the same user UID.
     */
    override suspend fun reportPhoto(photoId: String): Result<Int> = withContext(Dispatchers.IO) {
        val currentUserUid = auth.currentUser?.uid
        if (currentUserUid.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authentication required to report photos."))
        }

        try {
            val photoRef = firestore.collection("spot_photos").document(photoId)
            val reportRef = photoRef.collection("reports").document(currentUserUid)

            // Execute atomic transaction for per-user uniqueness and counter increment
            val newReportCount = firestore.runTransaction { transaction ->
                val reportDoc = transaction.get(reportRef)
                if (reportDoc.exists()) {
                    throw IllegalStateException("User $currentUserUid has already reported photo $photoId.")
                }

                val photoDoc = transaction.get(photoRef)
                val currentCount = photoDoc.getLong("reportsCount")?.toInt() ?: 0
                val updatedCount = currentCount + 1

                // 1. Create per-user unique report document
                val reportData = hashMapOf<String, Any>(
                    "reporterUid" to currentUserUid,
                    "timestamp" to System.currentTimeMillis()
                )
                transaction.set(reportRef, reportData)

                // 2. Increment reportsCount on photo document. The counter is written as an
                // explicit transaction-read value (rather than FieldValue.increment) so the
                // Firestore security rules child-create check can observe the before/after
                // counter state of the atomic report transaction.
                transaction.update(photoRef, "reportsCount", updatedCount)

                updatedCount
            }.await()

            SafeLog.i(TAG, "Successfully submitted report for photo $photoId. New reportsCount = $newReportCount")
            Result.success(newReportCount)
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to submit photo report: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Checks if a given photo is hidden from the UI (reportsCount >= 3).
     */
    fun isPhotoHidden(reportsCount: Int): Boolean {
        return PhotoProcessor.isPhotoHiddenByReports(reportsCount)
    }
}

package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.util.PhotoProcessor
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
 * Raised when a photo report cannot be submitted because the security rules rejected the
 * atomic transaction — i.e. this Firebase account has already reported the photo (or the
 * photo disappeared mid-transaction). Clear and recoverable: the UI shows the message and
 * the user can simply dismiss it.
 */
class PhotoReportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Raised when the photo document being reported no longer exists in Firestore. */
class PhotoNotFoundException(message: String) : Exception(message)

/**
 * State of a spot's photo stream. A failed listener (for example a missing Firestore
 * composite index) must never masquerade as an empty photo collection, so errors are
 * surfaced as [Error] rather than silently terminating the flow with no data.
 */
sealed interface SpotPhotosState {
    data object Loading : SpotPhotosState
    data class Loaded(val photos: List<SpotPhotoData>) : SpotPhotosState
    data class Error(val throwable: Throwable) : SpotPhotosState
}

/**
 * Photo workflow surface: real photo upload from an Android Photo Picker [Uri],
 * atomic reporting of an existing photo document by its real Firestore document ID,
 * and a bounded live stream of a spot's newest photos that distinguishes loaded
 * data, an empty result, and listener failure.
 */
interface SpotPhotoRepository {
    suspend fun uploadPhoto(context: Context, spotId: String, uri: Uri): Result<SpotPhotoData>
    suspend fun reportPhoto(photoId: String): Result<Int>
    fun photosForSpotFlow(spotId: String): Flow<SpotPhotosState>
    suspend fun getOlderPhotos(spotId: String, beforeTimestamp: Long): Result<List<SpotPhotoData>>
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
        // Page size bounding every photo query. Opening a spot must never download its
        // entire photo history; older photos are paged in explicitly by the UI via
        // getOlderPhotos(). Exposed so the UI can tell a full page ("more may exist")
        // from a partial page ("everything is loaded").
        const val PHOTOS_PAGE_SIZE = 20
    }

    override suspend fun uploadPhoto(context: Context, spotId: String, uri: Uri): Result<SpotPhotoData> =
        storageService.uploadSpotPhotoFromUri(spotId = spotId, context = context, uri = uri)

    /**
     * Streams the newest photo metadata documents of one spot, mapped from Firestore
     * snapshots. Documents are mapped field-by-field (Firestore cannot reflect into
     * Kotlin data classes); photos hidden by the real moderation state are filtered
     * by callers via [SpotPhotosData.isHidden].
     *
     * The query is bounded to the newest [PHOTOS_PAGE_SIZE] photos so opening a
     * popular spot never downloads its full history; older photos are fetched
     * explicitly through [getOlderPhotos]. Real-time behavior for newly added photos
     * is preserved as long as they land inside the visible page.
     *
     * The query requires the composite index declared in firestore.indexes.json
     * (spotId ASC, timestamp DESC). A listener failure — e.g. a missing index in a
     * freshly created Firebase project — surfaces as [SpotPhotosState.Error] instead
     * of an indefinitely empty photo grid: the UI must be able to distinguish an empty
     * spot from a query that never returned anything.
     */
    override fun photosForSpotFlow(spotId: String): Flow<SpotPhotosState> = callbackFlow {
        val registration = firestore.collection(PHOTOS_COLLECTION)
            .whereEqualTo("spotId", spotId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(PhotoModerationRepository.PHOTOS_PAGE_SIZE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    SafeLog.w(TAG, "Photo stream for spot '$spotId' failed: ${error.message}")
                    trySend(SpotPhotosState.Error(error))
                    return@addSnapshotListener
                }
                trySend(SpotPhotosState.Loaded(snapshot?.documents?.mapNotNull { it.toSpotPhotoData(spotId) } ?: emptyList()))
            }
        awaitClose { registration.remove() }
    }

    /**
     * One-shot pagination query for the photos older than the ones already shown.
     * Uses the same ordering and the same composite index (spotId ASC, timestamp DESC)
     * as [photosForSpotFlow], continuing strictly after [beforeTimestamp] — typically
     * the oldest timestamp of the already loaded set. Returns at most
     * [PHOTOS_PAGE_SIZE] documents; a page smaller than the bound tells the caller the
     * history is exhausted. A query failure returns a failed Result so the UI can
     * distinguish "no older photos" from "the query failed".
     */
    override suspend fun getOlderPhotos(spotId: String, beforeTimestamp: Long): Result<List<SpotPhotoData>> =
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection(PHOTOS_COLLECTION)
                    .whereEqualTo("spotId", spotId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .startAfter(beforeTimestamp)
                    .limit(PhotoModerationRepository.PHOTOS_PAGE_SIZE.toLong())
                    .get()
                    .await()
                Result.success(snapshot.documents.mapNotNull { it.toSpotPhotoData(spotId) })
            } catch (e: Exception) {
                SafeLog.w(TAG, "Older-photos query for spot '$spotId' failed: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Executes atomic photo report creation and counter increment in a single transaction.
     *
     * The Firestore security rules deny ALL reads of spot_photos/{photoId}/reports/{userId}
     * (reporter identities are private), so this transaction intentionally NEVER reads the
     * user's report document. Report uniqueness is enforced by the security rules'
     * before/after existence checks: the parent photo's reportsCount may only increase by
     * exactly one while the caller's report document transitions from absent to present.
     * A duplicate attempt is therefore rejected server-side and surfaces here as a
     [PhotoReportException] with a clear, recoverable message.
     */
    override suspend fun reportPhoto(photoId: String): Result<Int> = withContext(Dispatchers.IO) {
        val currentUserUid = auth.currentUser?.uid
        if (currentUserUid.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authentication required to report photos."))
        }

        try {
            val photoRef = firestore.collection(PHOTOS_COLLECTION).document(photoId)
            val reportRef = photoRef.collection("reports").document(currentUserUid)

            // Execute atomic transaction: the counter is written as an explicit
            // transaction-read value (rather than FieldValue.increment) so the Firestore
            // security rules child-create check can observe the before/after counter state
            // of the atomic report transaction. Only the parent photo document is read —
            // it is publicly readable — the report document is write-only by design.
            val newReportCount = firestore.runTransaction { transaction ->
                val photoDoc = transaction.get(photoRef)
                if (!photoDoc.exists()) {
                    throw PhotoNotFoundException("This photo no longer exists and cannot be reported.")
                }

                val currentCount = photoDoc.getLong("reportsCount")?.toInt() ?: 0
                val updatedCount = currentCount + 1

                // 1. Create per-user unique report document. Uniqueness is NOT checked by a
                // client read (forbidden) — it is enforced by the security rules' requirement
                // that the report document must not exist before and must exist after this
                // transaction.
                val reportData = hashMapOf<String, Any>(
                    "reporterUid" to currentUserUid,
                    "timestamp" to System.currentTimeMillis()
                )
                transaction.set(reportRef, reportData)

                // 2. Increment reportsCount on photo document.
                transaction.update(photoRef, "reportsCount", updatedCount)

                updatedCount
            }.await()

            SafeLog.i(TAG, "Successfully submitted report for photo $photoId. New reportsCount = $newReportCount")
            Result.success(newReportCount)
        } catch (e: FirebaseFirestoreException) {
            // After the parent-existence pre-check above, a rules rejection of this atomic
            // transaction means the user's report document already exists (duplicate report
            // from this Firebase account or a concurrent attempt), or the photo was removed
            // mid-transaction. Reporter documents are unreadable by design, so the server
            // rejection is the only authoritative duplicate signal — surface it clearly.
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                e.code == FirebaseFirestoreException.Code.ABORTED
            ) {
                Result.failure(
                    PhotoReportException(
                        "You have already reported this photo. Each photo can only be reported once per account.",
                        e
                    )
                )
            } else {
                Result.failure(e)
            }
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

/**
 * Maps one Firestore photo metadata document to [SpotPhotoData], or null when the
 * document lacks the mandatory photoUrl/ownerUid fields (the query stream and the
 * pagination query share this single mapping).
 */
private fun DocumentSnapshot.toSpotPhotoData(spotId: String): SpotPhotoData? {
    val photoUrl = getString("photoUrl") ?: return null
    val ownerUid = getString("ownerUid") ?: return null
    return SpotPhotoData(
        id = id,
        spotId = getString("spotId") ?: spotId,
        photoUrl = photoUrl,
        ownerUid = ownerUid,
        reportsCount = getLong("reportsCount")?.toInt() ?: 0,
        timestamp = getLong("timestamp") ?: 0L
    )
}

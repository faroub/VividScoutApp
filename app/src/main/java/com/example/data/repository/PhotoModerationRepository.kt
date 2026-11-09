package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.util.PhotoProcessor
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
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
    // Registered photo documents carry no ownerUid (publicly readable docs never carry
    // UIDs); kept for the local upload path, blank for server-mapped documents.
    val ownerUid: String = "",
    val photoPath: String = "",
    val reportsCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val moderationState: String? = null
) {
    /**
     * A photo is hidden from the UI when the trusted moderation sweep took it away
     * (moderationState "hidden" at the report threshold, or "removed" after manual
     * review — both authoritative backend states) OR when it locally crossed the
     * report threshold before the sweep ran.
     */
    val isHidden: Boolean
        get() = moderationState == "removed" || moderationState == "hidden" ||
            PhotoProcessor.isPhotoHiddenByReports(reportsCount)
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
    data class Loaded(
        val photos: List<SpotPhotoData>,
        /**
         * Cursor positioned at the OLDEST document of this bounded page. It is the
         * starting point for the first older-photos page. A compound (timestamp +
         * document id) cursor keeps the ordering deterministic even when several photos
         * share one timestamp: Firestore resolves the query position against both
         * ordering fields, so equal timestamps neither skip nor repeat documents.
         * Hidden photos are among the candidates, so the cursor is derived from the raw
         * page — never from the filtered display list.
         */
        val pageCursor: PhotoPageCursor? = null
    ) : SpotPhotosState
    data class Error(val throwable: Throwable) : SpotPhotosState
}

/**
 * Compound pagination cursor for the photo ordering (timestamp DESC, document id DESC):
     * the position of the last fetched document. Using both fields (not the timestamp
     * alone) disambiguates equal timestamps, so pages never skip or repeat photos.
     */
data class PhotoPageCursor(val timestamp: Long, val documentId: String)

/**
 * One page of older photos. [nextCursor] is null when the page was shorter than the page
 * size (the history is exhausted — there is provably nothing older), and non-null when a
 * full page was returned (a further page may exist). The cursor of the page's last
 * document lets the next query resume at an unambiguous position, even when several
 * photos share the same timestamp.
 */
data class OlderPhotosPage(
    val photos: List<SpotPhotoData>,
    val nextCursor: PhotoPageCursor?
)

/**
 * Photo workflow surface: real photo upload from an Android Photo Picker [Uri],
 * reporting of an existing photo through the trusted reportPhoto Cloud Function,
 * and a bounded live stream of a spot's newest photos that distinguishes loaded
 * data, an empty result, and listener failure.
 */
interface SpotPhotoRepository {
    suspend fun uploadPhoto(context: Context, spotId: String, uri: Uri): Result<SpotPhotoData>
    suspend fun reportPhoto(photoId: String): Result<Int>
    fun photosForSpotFlow(spotId: String): Flow<SpotPhotosState>
    suspend fun getOlderPhotos(spotId: String, cursor: PhotoPageCursor?): Result<OlderPhotosPage>
}

/**
 * Repository enforcing atomic, rate-limited photo reporting through the reportPhoto
 * Cloud Function, duplicate report prevention, and report-threshold moderation hiding.
 */
class PhotoModerationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val storageService: FirebasePhotoStorageService = FirebasePhotoStorageService(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
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
     * by callers via [SpotPhotoData.isHidden].
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
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(PhotoModerationRepository.PHOTOS_PAGE_SIZE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    SafeLog.w(TAG, "Photo stream for spot '$spotId' failed: ${error.message}")
                    trySend(SpotPhotosState.Error(error))
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents ?: emptyList()
                val photos = documents.mapNotNull { it.toSpotPhotoData(spotId) }
                trySend(SpotPhotosState.Loaded(photos, documents.lastOrNull()?.toPhotoPageCursor()))
            }
        awaitClose { registration.remove() }
    }

    /**
     * One-shot pagination query for the photos older than the ones already shown.
     * Uses the same ordering and the same composite index (spotId ASC, timestamp DESC)
     * as [photosForSpotFlow], continuing strictly AFTER the given [cursor] — the
     * compound (timestamp, document id) position of the oldest photo fetched so far.
     * A compound cursor resolves ties between equal timestamps unambiguously (the
     * ordering includes document id), so pages never skip or repeat photos.
     *
     * Returns at most [PHOTOS_PAGE_SIZE] documents; the page's cursor is null when the
     * page was shorter than the page bound, which tells the caller the history is
     * exhausted. A query failure returns a failed Result so the UI can distinguish
     * "no older photos" from "the query failed".
     */
    override suspend fun getOlderPhotos(spotId: String, cursor: PhotoPageCursor?): Result<OlderPhotosPage> =
        withContext(Dispatchers.IO) {
            try {
                val baseQuery = firestore.collection(PHOTOS_COLLECTION)
                    .whereEqualTo("spotId", spotId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                val query = if (cursor != null) {
                    baseQuery.startAfter(cursor.timestamp, cursor.documentId)
                } else {
                    baseQuery
                }
                val snapshot = query
                    .limit(PhotoModerationRepository.PHOTOS_PAGE_SIZE.toLong())
                    .get()
                    .await()
                val documents = snapshot.documents
                val photos = documents.mapNotNull { it.toSpotPhotoData(spotId) }
                // The completion signal is derived from the RAW page size (hidden and
                // malformed-mapping documents included) — never from the filtered list,
                // which would misreport an exhausted page.
                val nextCursor = if (documents.size >= PhotoModerationRepository.PHOTOS_PAGE_SIZE) {
                    documents.lastOrNull()?.toPhotoPageCursor()
                } else {
                    null
                }
                Result.success(OlderPhotosPage(photos, nextCursor))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SafeLog.w(TAG, "Older-photos query for spot '$spotId' failed: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Submits a photo report through the trusted reportPhoto Cloud Function.
     *
     * The callable is the authoritative boundary: inside one Firestore transaction it
     * re-checks the photo exists and is not already hidden/removed, rejects a duplicate
     * report from this account, consumes the per-UID rate limit, and creates the report
     * document together with the exact +1 reportsCount increment (the server-side
     * equivalent of the atomic client transaction older app versions wrote directly).
     * Every definitive rejection surfaces here as a typed failure with a clear,
     * recoverable message.
     */
    override suspend fun reportPhoto(photoId: String): Result<Int> = withContext(Dispatchers.IO) {
        val currentUserUid = auth.currentUser?.uid
        if (currentUserUid.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authentication required to report photos."))
        }

        try {
            val result = functions.getHttpsCallable("reportPhoto")
                .call(mapOf("photoId" to photoId))
                .await()

            val reportsCount = reportPhotoCountFromResponse(result.data)
            SafeLog.i(TAG, "Successfully submitted report for photo $photoId. New reportsCount = $reportsCount")
            Result.success(reportsCount)
        } catch (e: FirebaseFunctionsException) {
            // Definitive rejections from the trusted backend, mapped to clear outcomes.
            Result.failure(photoReportFailureFor(e.code, e))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
 * Maps a definitive reportPhoto Cloud Function rejection to the typed failure the UI
 * surfaces. The callable's contract: already-exists = this account already reported;
 * not-found = the photo disappeared; failed-precondition = the photo is hidden/removed;
 * unauthenticated = anonymous or missing sign-in. Anything else is an unexpected
 * backend condition and passes the original exception through unchanged for generic
 * error handling.
 */
internal fun photoReportFailureFor(
    code: FirebaseFunctionsException.Code,
    e: Exception
): Exception = when (code) {
    FirebaseFunctionsException.Code.ALREADY_EXISTS -> PhotoReportException(
        "You have already reported this photo. Each photo can only be reported once per account.",
        e
    )
    FirebaseFunctionsException.Code.NOT_FOUND -> PhotoNotFoundException(
        "This photo no longer exists and cannot be reported."
    )
    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> PhotoReportException(
        "This photo is no longer available and cannot be reported.",
        e
    )
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> PhotoReportException(
        "Sign in with a real account to report photos.",
        e
    )
    else -> {
        SafeLog.w("PhotoModerationRepo", "Failed to submit photo report: ${e.message}")
        e
    }
}

/**
 * Reads the authoritative reportsCount the reportPhoto Cloud Function returns for a
 * successful report. The callable's response shape is part of its contract; anything
 * that does not match it is a programming error (not a user-facing condition), so it
 * fails loudly instead of silently reporting a fabricated count.
 */
internal fun reportPhotoCountFromResponse(data: Any?): Int {
    val reportsCount = (data as? Map<*, *>)?.get("reportsCount")
    return (reportsCount as? Number)?.toInt()
        ?: error("Malformed reportPhoto response: expected reportsCount, got '$data'")
}

/**
 * Maps one Firestore photo metadata document to [SpotPhotoData], or null when the
 * document lacks the mandatory photoUrl field (the query stream and the pagination
 * query share this single mapping). The authoritative moderationState field is carried
 * through so [SpotPhotoData.isHidden] reflects backend removal as well as the local
 * report threshold. Registered photo documents carry no ownerUid (the ownership index
 * is server-side); the local model keeps a blank default for parity with the upload
 * path.
 */
private fun DocumentSnapshot.toSpotPhotoData(spotId: String): SpotPhotoData? {
    val photoUrl = getString("photoUrl") ?: return null
    return SpotPhotoData(
        id = id,
        spotId = getString("spotId") ?: spotId,
        photoUrl = photoUrl,
        ownerUid = getString("ownerUid") ?: "",
        photoPath = getString("photoPath") ?: "",
        reportsCount = getLong("reportsCount")?.toInt() ?: 0,
        timestamp = getLong("timestamp") ?: 0L,
        moderationState = getString("moderationState")
    )
}

/**
 * The compound cursor describing this document's position in the query's ordering
 * (timestamp DESC, document id DESC). Derived from the raw document — including
 * moderation-hidden ones — so paging continues at the true last position.
 */
private fun DocumentSnapshot.toPhotoPageCursor(): PhotoPageCursor =
    PhotoPageCursor(timestamp = getLong("timestamp") ?: 0L, documentId = id)

package com.example.data.sync

import android.content.Context

import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.TipVoteDao
import com.example.data.local.isDemoFallbackSpot
import com.example.util.SafeLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * The write-side classification of a local spot's image reference for the public
 * submission document (firestore.rules isValidPhotoUri accepts only '' or an owned
 * Storage path). Pure so the security boundary is unit-testable.
 */
enum class SubmissionPhotoKind { None, NeedsUpload, AlreadyUploaded, Rejected }

internal fun classifySubmissionPhoto(imageUrl: String?): SubmissionPhotoKind = when {
    imageUrl.isNullOrBlank() -> SubmissionPhotoKind.None
    imageUrl.startsWith("ic_cat_") -> SubmissionPhotoKind.None
    imageUrl.startsWith("content://") -> SubmissionPhotoKind.NeedsUpload
    imageUrl.startsWith("spot_photos/") -> SubmissionPhotoKind.AlreadyUploaded
    else -> SubmissionPhotoKind.Rejected
}

/**
 * The display-side classification of a submission document's stored photoUri. Pure so
 * the never-render-an-external-URL contract is unit-testable.
 */
enum class SubmissionDisplayPhoto { NoPhoto, StoragePath, ExternalRejected }

internal fun classifySubmissionPhotoForDisplay(rawPhotoUri: String?): SubmissionDisplayPhoto = when {
    rawPhotoUri.isNullOrBlank() -> SubmissionDisplayPhoto.NoPhoto
    rawPhotoUri.startsWith("spot_photos/") -> SubmissionDisplayPhoto.StoragePath
    else -> SubmissionDisplayPhoto.ExternalRejected
}

/**
 * Outcome of a community post attempt through the trusted callables. PENDING covers
 * every non-destructive failure (no auth, no network, backend error, rejected payload):
 * the local record stays queued and is retried later. RATE_LIMITED is surfaced to the
 * user as an explicit message instead of a silent queued retry. SYNCED means the
 * server confirmed the write.
 */
enum class CommunitySyncOutcome { SYNCED, PENDING, RATE_LIMITED }

/**
 * Manages bidirectional cloud synchronization between local Room entities and Firebase Firestore.
 * Handles uploading offline drafts (isOfflineDraft == true) and user-authored community tips,
 * and fetching community-shared gems and peer tips from the cloud.
 *
 * Structure: this object remains the public facade for private spot sync (drafts and
 * owner-scoped notes). Community write operations (tip posts, atomic upvotes, vote
 * reconciliation, pending-tip retries) live in [CommunitySyncWriter] and community
 * downloads (paginated tips/submissions fetch, private-note restore) live in
 * [CommunitySyncDownloader]. Firebase/App Check/auth bootstrapping is shared through
 * [FirebaseAccess]. All public signatures here are unchanged thin delegations.
 */
object FirestoreSyncManager {

    /**
     * Uploads an individual offline draft spot to the Firestore 'community_submissions' collection.
     *
     * Private scout notes are NEVER written to the public submission document: Firestore has no
     * field-level access control, so every field of a publicly readable document is world-visible.
     * Instead the note is persisted under the owner-scoped path
     * users/{uid}/private_spot_notes/{spotId}, written with the authenticated UID — never a
     * caller-provided one. Both writes are idempotent (the callable replays the same id; the note
     * uses set-merge), so a retry after a partial failure cannot leak the note into the public
     * document. The submission document carries no ownerUid: ownership is recorded server-side
     * in the ownership index, invisible in the public collection.
     */
    suspend fun syncDraftSpotToCloud(
        context: Context,
        spot: ScoutSpotEntity,
        scoutDao: ScoutDao,
        discoveredGemDao: DiscoveredGemDao?
    ): Boolean = withContext(Dispatchers.IO) {
        // Trusted boundary: illustrative offline-demo records (every Overpass mirror
        // failed or returned nothing) must never be published to Firestore as genuine
        // community submissions. They are not real places.
        if (spot.isDemoFallbackSpot) {
            SafeLog.w(TAG, "Refusing to sync illustrative demo spot '${spot.id}' as a community submission")
            return@withContext false
        }
        if (!FirebaseAccess.ensureFirebaseAuth(context)) {
            SafeLog.w(TAG, "Cannot sync draft '${spot.id}': Firebase Auth required for Firestore writes")
            return@withContext false
        }
        try {
            // The public submission may carry only '' or an owned Storage path
            // (firestore.rules isValidPhotoUri). A locally selected photo (content://)
            // is uploaded through the trusted finalizePhotoUpload pipeline now — sync
            // requires auth and network anyway — and the canonical path is stored.
            // Legacy/fabricated external URLs are never rewritten into the document.
            val photoKind = classifySubmissionPhoto(spot.imageUrl)
            if (photoKind == SubmissionPhotoKind.Rejected) {
                SafeLog.w(TAG, "Dropping non Storage photo reference from submission '${spot.id}'")
            }
            val submissionPhotoPath = when (photoKind) {
                SubmissionPhotoKind.None, SubmissionPhotoKind.Rejected -> ""
                SubmissionPhotoKind.AlreadyUploaded -> spot.imageUrl ?: ""
                SubmissionPhotoKind.NeedsUpload -> {
                    val localUri = spot.imageUrl?.let { android.net.Uri.parse(it) }
                        ?: return@withContext false
                    val upload = com.example.data.repository.FirebasePhotoStorageService()
                        .uploadSpotPhotoFromUri(spot.id, context, localUri)
                    val photo = upload.getOrElse { e ->
                        SafeLog.w(TAG, "Submission photo upload failed for draft '${spot.id}': ${e.message}; draft stays queued")
                        return@withContext false
                    }
                    photo.photoPath
                }
            }

            val docData = hashMapOf(
                "id" to spot.id,
                "title" to spot.title,
                "description" to spot.description,
                "category" to spot.category,
                "locationName" to spot.locationName,
                "latitude" to spot.latitude,
                "longitude" to spot.longitude,
                "photoUri" to submissionPhotoPath,
                "contributor" to spot.contributor,
                "timestamp" to spot.timestamp
            )

            // Community submissions are written ONLY by the trusted submitCommunitySpot
            // Cloud Function (firestore.rules denies direct client creation): the
            // payload carries the client-controlled fields, the upvote/ownership state
            // stays server-owned, and a retry of the same id replays instead of
            // duplicating. A resource-exhausted response means the 24-hour per-UID
            // limit was hit — the draft stays queued locally and syncs when the window
            // resets; no false success is reported.
            val app = FirebaseAccess.getFirebaseApp(context) ?: return@withContext false
            try {
                FirebaseFunctions.getInstance(app).getHttpsCallable("submitCommunitySpot")
                    .call(docData).await()
            } catch (e: FirebaseFunctionsException) {
                if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                    SafeLog.w(TAG, "Submission '${spot.id}' hit the daily post limit; the draft stays queued and syncs when the limit resets")
                } else {
                    SafeLog.w(TAG, "Failed to submit community spot '${spot.id}' via Cloud Function: ${e.message}")
                }
                return@withContext false
            }

            // The private note belongs under the owner-scoped collection only. A failure
            // here fails the whole sync so the draft is retried later (both writes are
            // idempotent); the note must never be dropped silently nor moved to the
            // public document as a fallback.
            if (!syncPrivateSpotNoteToCloud(context, spot.id, spot.userNotes)) {
                SafeLog.w(TAG, "Private note for spot '${spot.id}' could not be synced; the public submission was left without it and the draft will be retried")
                return@withContext false
            }

            val syncedSpot = spot.copy(
                isOfflineDraft = false,
                statusBadge = "Synced to Cloud"
            )
            scoutDao.updateSpot(syncedSpot)
            discoveredGemDao?.markAsSynced(spot.id)
            SafeLog.i(TAG, "Successfully synced spot '${spot.title}' to cloud Firestore")
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to sync draft spot '${spot.id}' to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Persists a spot's private note under the owner-scoped path
     * users/{authenticatedUid}/private_spot_notes/{spotId}. The collection path key is the
     * authenticated UID — a caller cannot target another user's notes — and the public
     * submission documents never receive this field.
     */
    suspend fun syncPrivateSpotNoteToCloud(
        context: Context,
        spotId: String,
        notes: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (!FirebaseAccess.ensureFirebaseAuth(context)) {
            SafeLog.w(TAG, "Cannot sync private note for '$spotId': Firebase Auth required")
            return@withContext false
        }
        val firestore = FirebaseAccess.getFirestore(context) ?: return@withContext false
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false
        try {
            val noteData = hashMapOf(
                "spotId" to spotId,
                "ownerUid" to currentUid,
                "notes" to (notes ?: ""),
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(USERS_COLLECTION)
                .document(currentUid)
                .collection(PRIVATE_NOTES_COLLECTION)
                .document(spotId)
                .set(noteData, SetOptions.merge())
                .await()
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to sync private note for spot '$spotId': ${e.message}", e)
            false
        }
    }

    /**
     * Synchronizes all pending offline drafts (isOfflineDraft == true) to Firestore.
     */
    suspend fun syncAllOfflineDrafts(
        context: Context,
        scoutDao: ScoutDao,
        discoveredGemDao: DiscoveredGemDao?
    ): Int = withContext(Dispatchers.IO) {
        val drafts = scoutDao.getAllSpotsSync().filter { it.isOfflineDraft }
        var successCount = 0
        for (draft in drafts) {
            if (syncDraftSpotToCloud(context, draft, scoutDao, discoveredGemDao)) {
                successCount++
            }
        }
        successCount
    }

    /**
     * Posts a community tip or status update through the trusted postCommunityTip
     * Cloud Function. See [CommunitySyncWriter.postCommunityTipToCloud].
     */
    suspend fun postCommunityTipToCloud(
        context: Context,
        tip: CommunityTipEntity,
        tipDao: CommunityTipDao? = null
    ): CommunitySyncOutcome = CommunitySyncWriter.postCommunityTipToCloud(context, tip, tipDao)

    /**
     * Atomically records a +1 upvote together with the vote document in a Firestore
     * transaction, rejecting second votes. See [CommunitySyncWriter.incrementTipUpvoteInCloud].
     */
    suspend fun incrementTipUpvoteInCloud(
        context: Context,
        tipId: String
    ): Boolean = CommunitySyncWriter.incrementTipUpvoteInCloud(context, tipId)

    /**
     * Reconciles local vote state with the cloud vote document. See
     * [CommunitySyncWriter.reconcileTipVoteInCloud].
     */
    suspend fun reconcileTipVoteInCloud(
        context: Context,
        tipId: String,
        tipDao: CommunityTipDao,
        tipVoteDao: TipVoteDao?
    ): Boolean = CommunitySyncWriter.reconcileTipVoteInCloud(context, tipId, tipDao, tipVoteDao)

    /**
     * Retries uploading all pending locally authored community tips. See
     * [CommunitySyncWriter.syncPendingTipsToCloud].
     */
    suspend fun syncPendingTipsToCloud(
        context: Context,
        tipDao: CommunityTipDao
    ): Int = CommunitySyncWriter.syncPendingTipsToCloud(context, tipDao)

    /**
     * Downloads recent remote community tips and submissions into Room. See
     * [CommunitySyncDownloader.syncRemoteContentDown].
     */
    suspend fun syncRemoteContentDown(
        context: Context,
        tipDao: CommunityTipDao,
        scoutDao: ScoutDao
    ): Pair<Int, Int> = CommunitySyncDownloader.syncRemoteContentDown(context, tipDao, scoutDao)
}

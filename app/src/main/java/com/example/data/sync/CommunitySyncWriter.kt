package com.example.data.sync

import android.content.Context
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.TipVoteDao
import com.example.util.SafeLog
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Community write-side synchronization extracted from [FirestoreSyncManager]: posting
 * tips through the trusted postCommunityTip Cloud Function, the atomic upvote
 * transaction, vote reconciliation, and the pending-tip retry queue.
 *
 * No direct client writes to community_tips: creation goes exclusively through the
 * callable (firestore.rules denies direct client creation), and upvotes mutate the
 * counter only inside a Firestore transaction together with the vote document.
 */
internal object CommunitySyncWriter {

    /**
     * Posts a community tip or status update to the Firestore 'community_tips' collection
     * through the trusted postCommunityTip Cloud Function (firestore.rules denies
     * direct client creation). Idempotent: a retry of the same tip id replays the
     * already-published document on the backend, which preserves the server-owned
     * upvotes counters and never duplicates the tip.
     *
     * Sample/demo tips (seed tips, simulated peer-mesh gossip) are refused: they are
     * illustrative content authored by the app, never real community contributions,
     * so they must never reach Firestore under a real user's identity.
     */
    suspend fun postCommunityTipToCloud(
        context: Context,
        tip: CommunityTipEntity,
        tipDao: CommunityTipDao? = null
    ): CommunitySyncOutcome = withContext(Dispatchers.IO) {
        if (tip.isSampleContent) {
            SafeLog.w(TAG, "Refusing to upload sample tip '${tip.id}' as a real community contribution")
            return@withContext CommunitySyncOutcome.PENDING
        }
        if (!FirebaseAccess.ensureFirebaseAuth(context)) {
            SafeLog.w(TAG, "Cannot post tip '${tip.id}': Firebase Auth required for community posts")
            return@withContext CommunitySyncOutcome.PENDING
        }
        val app = FirebaseAccess.getFirebaseApp(context) ?: return@withContext CommunitySyncOutcome.PENDING
        try {
            // Payload mirrors the client-controlled fields accepted server-side by
            // functions/community.js. The upvotes counter and the ownership record are
            // server-owned and intentionally absent from the payload.
            val payload = hashMapOf<String, Any>(
                "id" to tip.id,
                "spotId" to tip.spotId,
                "spotTitle" to tip.spotTitle,
                "authorName" to tip.authorName,
                "content" to tip.content,
                "timestamp" to tip.timestamp,
                "isStatusUpdate" to tip.isStatusUpdate,
                "statusCategory" to tip.statusCategory,
                "legacyUpvotes" to tip.legacyUpvotes
            )

            FirebaseFunctions.getInstance(app).getHttpsCallable("postCommunityTip")
                .call(payload).await()

            tipDao?.markTipAsSynced(tip.id)
            SafeLog.i(TAG, "Posted community tip '${tip.id}' to cloud Firestore")
            CommunitySyncOutcome.SYNCED
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                // The 24-hour per-UID limit: the tip stays local and is retried once the
                // window resets — surfaced to the user as an explicit message.
                SafeLog.w(TAG, "Community tip '${tip.id}' hit the daily post limit; it stays local and will sync when the limit resets")
                CommunitySyncOutcome.RATE_LIMITED
            } else {
                SafeLog.w(TAG, "Failed to post community tip '${tip.id}' via Cloud Function: ${e.message}")
                CommunitySyncOutcome.PENDING
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Failed to post community tip '${tip.id}' to Firestore: ${e.message}", e)
            CommunitySyncOutcome.PENDING
        }
    }

    /**
     * Synchronizes a +1 upvote for a community tip using a Firestore transaction.
     * Checks if the vote document at community_tips/{tipId}/votes/{currentUid} already exists,
     * rejecting second votes from the same user, and writing the vote doc together with the upvote increment.
     */
    suspend fun incrementTipUpvoteInCloud(
        context: Context,
        tipId: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!FirebaseAccess.ensureFirebaseAuth(context)) return@withContext false
        val firestore = FirebaseAccess.getFirestore(context) ?: return@withContext false
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            SafeLog.w(TAG, "Cannot upvote tip '$tipId': No authenticated user UID found")
            return@withContext false
        }

        try {
            val tipRef = firestore.collection(TIPS_COLLECTION).document(tipId)
            val voteRef = tipRef.collection("votes").document(currentUid)

            val voteSuccess = firestore.runTransaction { transaction ->
                val tipSnap = transaction.get(tipRef)
                if (!tipSnap.exists() || !tipSnap.contains("legacyUpvotes")) {
                    SafeLog.w(TAG, "Cannot upvote tip '$tipId': Document is missing 'legacyUpvotes' field. Aborting transaction.")
                    return@runTransaction false
                }

                val voteSnap = transaction.get(voteRef)
                if (voteSnap.exists()) {
                    SafeLog.w(TAG, "User '${SafeLog.sanitizeUid(currentUid)}' has already voted on tip '$tipId'. Rejecting second vote.")
                    return@runTransaction false
                }

                val currentUpvotes = tipSnap.getLong("upvotes") ?: 0L

                val voteData = hashMapOf<String, Any>(
                    "uid" to currentUid,
                    "timestamp" to System.currentTimeMillis()
                )

                // Set vote doc in votes subcollection AND increment tip upvotes by 1
                transaction.set(voteRef, voteData)
                transaction.update(tipRef, "upvotes", currentUpvotes + 1)
                true
            }.await()

            if (voteSuccess) {
                SafeLog.i(TAG, "Successfully recorded vote from user '${SafeLog.sanitizeUid(currentUid)}' for tip '$tipId'")
            }
            voteSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to execute upvote transaction for tip '$tipId': ${e.message}")
            false
        }
    }

    /**
     * Reconciles user vote state from Cloud Firestore when an upvote transaction encounters an ambiguous outcome or duplicate vote error.
     * If user vote doc exists in Cloud Firestore, ensures local Room DB records the vote.
     */
    suspend fun reconcileTipVoteInCloud(
        context: Context,
        tipId: String,
        tipDao: CommunityTipDao,
        tipVoteDao: TipVoteDao?
    ): Boolean = withContext(Dispatchers.IO) {
        if (!FirebaseAccess.ensureFirebaseAuth(context)) return@withContext false
        val firestore = FirebaseAccess.getFirestore(context) ?: return@withContext false
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false

        try {
            val voteRef = firestore.collection(TIPS_COLLECTION).document(tipId).collection("votes").document(currentUid)
            val voteSnap = voteRef.get().await()

            if (voteSnap.exists()) {
                if (tipVoteDao != null && !tipVoteDao.hasUserVoted(tipId, currentUid)) {
                    tipVoteDao.recordVoteAndIncrementCount(tipId, currentUid, tipDao)
                }
                return@withContext true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SafeLog.w(TAG, "Reconciliation check failed for tip '$tipId': ${e.message}")
        }
        false
    }

    /**
     * Retries uploading all pending locally authored community tips to Firestore.
     */
    suspend fun syncPendingTipsToCloud(
        context: Context,
        tipDao: CommunityTipDao
    ): Int = withContext(Dispatchers.IO) {
        val pendingTips = tipDao.getLocallyAuthoredTipsSync().filter { !it.isSyncedToCloud }
        var successCount = 0
        for (tip in pendingTips) {
            if (postCommunityTipToCloud(context, tip, tipDao) == CommunitySyncOutcome.SYNCED) {
                successCount++
            }
        }
        successCount
    }
}

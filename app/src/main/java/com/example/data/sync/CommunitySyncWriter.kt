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
 * tips through the trusted postCommunityTip Cloud Function, upvotes through the
 * trusted castTipVote Cloud Function, vote reconciliation, and the pending-tip
 * retry queue.
 *
 * No direct client writes to community_tips: creation goes exclusively through the
 * postCommunityTip callable and votes exclusively through castTipVote (firestore.rules
 * denies direct client creation); each callable mutates the counters only inside a
 * server-side Firestore transaction together with its child document.
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
     * Synchronizes a +1 upvote for a community tip through the trusted castTipVote
     * Cloud Function. Inside one server-side transaction the callable re-checks the
     * tip exists, rejects the tip owner's self-vote and a second vote from the same
     * account, consumes the per-UID rate limit, and writes the vote document together
     * with the exact +1 upvote increment. Keeping the increment server-side prevents
     * a rejected or replayed vote from ever desynchronizing the counter.
     *
     * Returns false for every definitive rejection (duplicate vote, self-vote, rate
     * limit, missing tip) as well as for transient failures — callers reconcile the
     * local vote state via [reconcileTipVoteInCloud] when the outcome is ambiguous.
     */
    suspend fun incrementTipUpvoteInCloud(
        context: Context,
        tipId: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!FirebaseAccess.ensureFirebaseAuth(context)) return@withContext false
        val app = FirebaseAccess.getFirebaseApp(context) ?: return@withContext false
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            SafeLog.w(TAG, "Cannot upvote tip '$tipId': No authenticated user UID found")
            return@withContext false
        }

        try {
            FirebaseFunctions.getInstance(app).getHttpsCallable("castTipVote")
                .call(mapOf("tipId" to tipId)).await()
            SafeLog.i(TAG, "Successfully recorded vote from user '${SafeLog.sanitizeUid(currentUid)}' for tip '$tipId'")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.ALREADY_EXISTS,
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> {
                    // A definitive server-side rejection: this account already voted, or
                    // it owns the tip (self-votes are refused). Not a transient failure.
                    SafeLog.w(TAG, "Vote on tip '$tipId' rejected by backend: ${e.message}")
                }
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    SafeLog.w(TAG, "Vote on tip '$tipId' hit the hourly vote limit; try again later")
                }
                else -> {
                    SafeLog.w(TAG, "Failed to record vote for tip '$tipId' via Cloud Function: ${e.message}")
                }
            }
            false
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to execute upvote call for tip '$tipId': ${e.message}")
            false
        }
    }

    /**
     * Submits a community-tip report through the trusted reportCommunityTip Cloud Function.
     *
     * The callable is the authoritative boundary: inside one Firestore transaction it
     * re-checks the tip exists and is not already hidden/removed, rejects the tip owner's
     * self-report and a duplicate report from this account, requires a non-anonymous
     * sign-in, consumes the per-UID rate limit, and writes the report document together
     * with the exact +1 reportsCount increment. Every definitive rejection surfaces here
     * as a typed failure with a clear, recoverable message.
     */
    suspend fun reportTipInCloud(
        context: Context,
        tipId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!FirebaseAccess.ensureFirebaseAuth(context)) {
            return@withContext Result.failure(IllegalStateException("Authentication required to report tips."))
        }
        val app = FirebaseAccess.getFirebaseApp(context)
            ?: return@withContext Result.failure(IllegalStateException("Firebase is not initialized."))

        try {
            val result = FirebaseFunctions.getInstance(app).getHttpsCallable("reportCommunityTip")
                .call(mapOf("tipId" to tipId)).await()

            val reportsCount = communityReportCountFromResponse(result.data)
            SafeLog.i(TAG, "Reported community tip '$tipId'. reportsCount = $reportsCount")
            Result.success(reportsCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            Result.failure(communityReportFailureFor(e.code, e))
        } catch (e: Exception) {
            SafeLog.w(TAG, "Failed to report community tip '$tipId': ${e.message}")
            Result.failure(e)
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

/**
 * A community-tip report rejection the UI surfaces as an explicit, recoverable message.
 */
class CommunityReportException(message: String, cause: Exception? = null) : Exception(message, cause)

/**
 * Maps a definitive reportCommunityTip Cloud Function rejection to the typed failure
 * the UI surfaces. The callable's contract: already-exists = this account already
 * reported the tip; not-found = the tip disappeared; permission-denied = the caller
 * owns the tip (self-reports are refused); failed-precondition = the tip is
 * hidden/removed; unauthenticated = anonymous or missing sign-in;
 * resource-exhausted = the hourly per-UID report limit was hit. Anything else is an
 * unexpected backend condition and passes the original exception through unchanged
 * for generic error handling.
 */
internal fun communityReportFailureFor(
    code: FirebaseFunctionsException.Code,
    e: Exception
): Exception = when (code) {
    FirebaseFunctionsException.Code.ALREADY_EXISTS -> CommunityReportException(
        "You already reported this tip. Each tip can only be reported once per account.",
        e
    )
    FirebaseFunctionsException.Code.NOT_FOUND -> CommunityReportException(
        "This tip no longer exists and cannot be reported."
    )
    FirebaseFunctionsException.Code.PERMISSION_DENIED -> CommunityReportException(
        "You cannot report your own tip.",
        e
    )
    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> CommunityReportException(
        "This tip is no longer available and cannot be reported.",
        e
    )
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> CommunityReportException(
        "Sign in with a real account to report tips.",
        e
    )
    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> CommunityReportException(
        "You've reached the hourly reporting limit. Try again later.",
        e
    )
    else -> {
        SafeLog.w(TAG, "Failed to submit tip report: ${e.message}")
        e
    }
}

/**
 * Reads the authoritative reportsCount the reportCommunityTip Cloud Function returns
 * for a successful report. The callable's response shape is part of its contract;
 * anything that does not match it is a programming error (not a user-facing
 * condition), so it fails loudly instead of silently reporting a fabricated count.
 */
internal fun communityReportCountFromResponse(data: Any?): Int {
    val reportsCount = (data as? Map<*, *>)?.get("reportsCount")
    return (reportsCount as? Number)?.toInt()
        ?: error("Malformed reportCommunityTip response: expected reportsCount, got '$data'")
}

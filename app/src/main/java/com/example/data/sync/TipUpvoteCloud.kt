package com.example.data.sync

import android.content.Context

/**
 * Interface abstracting Cloud Firestore upvote operations for community tips,
 * enabling direct unit testing and mock injection without Android Context dependencies.
 */
interface TipUpvoteCloud {
    suspend fun incrementTipUpvote(tipId: String): Boolean
}

/**
 * Production implementation delegating upvote transactions to FirestoreSyncManager.
 */
class FirestoreTipUpvoteCloud(
    private val context: Context?
) : TipUpvoteCloud {
    override suspend fun incrementTipUpvote(tipId: String): Boolean {
        val appCtx = context ?: return false
        return FirestoreSyncManager.incrementTipUpvoteInCloud(appCtx, tipId)
    }
}

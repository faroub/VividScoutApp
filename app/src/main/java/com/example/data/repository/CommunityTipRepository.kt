package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.ScoutDao
import com.example.data.local.TipVoteDao
import com.example.data.local.TipVoteEntity
import com.example.data.p2p.MeshSyncPhase
import com.example.data.p2p.PeerMeshSyncEngine
import com.example.data.p2p.PeerMeshSyncResult
import com.example.data.sync.FirestoreSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

class CommunityTipRepository(
    private val dao: CommunityTipDao,
    private val tipVoteDao: TipVoteDao? = null,
    private val context: Context? = null
) {
    val allTipsFlow: Flow<List<CommunityTipEntity>> = dao.getAllTipsFlow()
    val allStatusUpdatesFlow: Flow<List<CommunityTipEntity>> = dao.getAllStatusUpdatesFlow()

    fun getTipsForSpotFlow(spotId: String): Flow<List<CommunityTipEntity>> {
        return dao.getTipsForSpotFlow(spotId)
    }

    fun getLatestStatusForSpotFlow(spotId: String): Flow<CommunityTipEntity?> {
        return dao.getLatestStatusForSpot(spotId)
    }

    suspend fun postTip(
        spotId: String,
        spotTitle: String,
        content: String,
        isStatusUpdate: Boolean = false,
        statusCategory: String = "INSIGHT",
        authorName: String = "@you"
    ): CommunityTipEntity = withContext(Dispatchers.IO) {
        val newTip = CommunityTipEntity(
            id = "user_tip_${UUID.randomUUID().toString().take(8)}",
            spotId = spotId,
            spotTitle = spotTitle,
            authorName = authorName,
            content = content.trim(),
            timestamp = System.currentTimeMillis(),
            isStatusUpdate = isStatusUpdate,
            statusCategory = statusCategory,
            upvotes = 0,
            peerNodeId = "local-device",
            isLocallyAuthored = true
        )
        dao.insertTip(newTip)
        context?.let { ctx ->
            FirestoreSyncManager.postCommunityTipToCloud(ctx, newTip)
        }
        newTip
    }

    suspend fun syncWithCloud(scoutDao: ScoutDao? = null): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext Pair(0, 0)
        val db = AppDatabase.getDatabase(ctx)
        FirestoreSyncManager.syncPendingTipsToCloud(ctx, dao)
        FirestoreSyncManager.syncRemoteContentDown(
            ctx, dao, scoutDao ?: db.scoutDao()
        )
    }

    /**
     * Upvotes a community tip using persisted per-user vote tracking and cloud-first atomic synchronization.
     * Rejects duplicate votes from the same user, and updates Room locally only if the cloud call succeeds.
     */
    suspend fun upvoteTip(tipId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext false
        }

        // Fetch tip entity from database
        val tip = dao.getTipById(tipId) ?: return@withContext false

        // Unsynced local/seed tips cannot be voted on until synced to Firestore cloud
        if (!tip.isSyncedToCloud) {
            return@withContext false
        }

        // 1. Check persisted local vote table first. If user already voted, reject and change nothing.
        if (tipVoteDao?.hasUserVoted(tipId, userId) == true) {
            return@withContext false
        }

        // 2. Call cloud transaction first if cloud context is present
        val appCtx = context
        if (appCtx != null) {
            val cloudSuccess = FirestoreSyncManager.incrementTipUpvoteInCloud(appCtx, tipId)
            if (!cloudSuccess) {
                // Cloud transaction failed or rejected duplicate vote: change nothing locally
                return@withContext false
            }
        }

        // 3. Record local vote in tip_votes and increment local count
        tipVoteDao?.insertVote(TipVoteEntity(tipId = tipId, userId = userId))
        dao.incrementUpvote(tipId)
        true
    }

    suspend fun syncWithNearbyPeers(
        onPhaseChanged: (MeshSyncPhase) -> Unit = {}
    ): PeerMeshSyncResult = withContext(Dispatchers.IO) {
        val existingTips = dao.getAllTipsFlow().first()
        val existingIds = existingTips.map { it.id }.toSet()

        val (result, newTips) = PeerMeshSyncEngine.performGossipRound(
            existingTipIds = existingIds,
            onPhaseChanged = onPhaseChanged
        )

        if (newTips.isNotEmpty()) {
            dao.insertTips(newTips)
        }

        result
    }

    suspend fun ensureInitialTipsSeeded() = withContext(Dispatchers.IO) {
        val count = dao.getTipCount()
        if (count == 0) {
            val seedTips = PeerMeshSyncEngine.createSeedTips()
            dao.insertTips(seedTips)
        }
    }
}

package com.example.data.repository

import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.p2p.MeshSyncPhase
import com.example.data.p2p.PeerMeshSyncEngine
import com.example.data.p2p.PeerMeshSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

class CommunityTipRepository(
    private val dao: CommunityTipDao
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
            upvotes = 1,
            peerNodeId = "local-device",
            isLocallyAuthored = true
        )
        dao.insertTip(newTip)
        newTip
    }

    suspend fun upvoteTip(tipId: String) = withContext(Dispatchers.IO) {
        dao.incrementUpvote(tipId)
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

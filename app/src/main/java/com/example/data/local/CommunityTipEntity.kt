package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Peer-to-peer cached community tip or spot status update for a Lisbon gem.
 */
@Entity(
    tableName = "community_tips",
    indices = [
        Index(value = ["spotId"]),
        Index(value = ["timestamp"]),
        Index(value = ["isStatusUpdate"])
    ]
)
data class CommunityTipEntity(
    @PrimaryKey
    val id: String,
    val spotId: String,
    val spotTitle: String,
    val authorName: String,
    val content: String,
    val timestamp: Long,
    val isStatusUpdate: Boolean = false,
    val statusCategory: String = "INSIGHT", // STOCK_SHORTAGE, LINE_QUEUE, FRESH_BATCH, ATMOSPHERE, INSIGHT
    val upvotes: Int = 0,
    val peerNodeId: String = "local-device",
    val isLocallyAuthored: Boolean = false
) {
    /**
     * Flags pre-seeded or demo simulated tips so they are clearly presented
     * as sample content rather than real live users' posts.
     */
    val isSampleContent: Boolean
        get() = !isLocallyAuthored && (
            id.startsWith("seed_tip_") ||
            peerNodeId.startsWith("peer_seed_") ||
            id.startsWith("p2p_") ||
            peerNodeId.startsWith("peer_") ||
            peerNodeId.startsWith("demo_")
        )
}

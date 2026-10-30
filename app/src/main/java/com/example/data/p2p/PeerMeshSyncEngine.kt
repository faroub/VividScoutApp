package com.example.data.p2p

import com.example.data.local.CommunityTipEntity
import kotlinx.coroutines.delay
import java.util.UUID

data class PeerNode(
    val nodeId: String,
    val scoutHandle: String,
    val distanceMeters: Int,
    val transport: String, // "BLE Mesh" or "Wi-Fi Direct"
    val sharedTipsCount: Int
)

enum class MeshSyncPhase {
    IDLE,
    DISCOVERING_PEERS,
    EXCHANGING_GOSSIP,
    SYNC_SUCCESS
}

data class PeerMeshSyncResult(
    val connectedPeers: List<PeerNode>,
    val tipsReceived: Int,
    val tipsTransmitted: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Peer-to-Peer mesh synchronization engine simulating local ad-hoc communication
 * between nearby Lisbon scouts over BLE and Wi-Fi Direct.
 */
object PeerMeshSyncEngine {

    val simulatedPeers = listOf(
        PeerNode(
            nodeId = "peer_alfama_88",
            scoutHandle = "@clara_alfama",
            distanceMeters = 18,
            transport = "BLE Mesh",
            sharedTipsCount = 3
        ),
        PeerNode(
            nodeId = "peer_chiado_42",
            scoutHandle = "@miguel_chiado",
            distanceMeters = 34,
            transport = "Wi-Fi Direct",
            sharedTipsCount = 2
        ),
        PeerNode(
            nodeId = "peer_graca_19",
            scoutHandle = "@tiago_graca",
            distanceMeters = 55,
            transport = "BLE Mesh",
            sharedTipsCount = 4
        )
    )

    /**
     * Simulates an ad-hoc peer mesh gossip round:
     * 1. Broadcasts node presence.
     * 2. Reconciles gossip vector clock.
     * 3. Returns new peer-contributed community tips.
     */
    suspend fun performGossipRound(
        existingTipIds: Set<String>,
        onPhaseChanged: (MeshSyncPhase) -> Unit = {}
    ): Pair<PeerMeshSyncResult, List<CommunityTipEntity>> {
        onPhaseChanged(MeshSyncPhase.DISCOVERING_PEERS)
        delay(600) // Simulated discovery handshake

        onPhaseChanged(MeshSyncPhase.EXCHANGING_GOSSIP)
        delay(700) // Simulated packet exchange

        val now = System.currentTimeMillis()
        val candidateTips = listOf(
            CommunityTipEntity(
                id = "p2p_tip_101",
                spotId = "spot_2", // Pastelaria Santo António
                spotTitle = "Pastelaria Santo António",
                authorName = "@clara_alfama",
                content = "Warm custard batch pulled 12m ago! Cinnamon shakers refilled on the left bar.",
                timestamp = now - (14 * 60 * 1000L), // 14 mins ago (FRESH)
                isStatusUpdate = true,
                statusCategory = "FRESH_BATCH",
                upvotes = 7,
                peerNodeId = "peer_alfama_88"
            ),
            CommunityTipEntity(
                id = "p2p_tip_102",
                spotId = "spot_5", // Manteigaria
                spotTitle = "Manteigaria Fábrica",
                authorName = "@miguel_chiado",
                content = "Warning: Kitchen closing for quick 20m dough prep. Short wait right now.",
                timestamp = now - (45 * 60 * 1000L), // 45 mins ago (FRESH)
                isStatusUpdate = true,
                statusCategory = "LINE_QUEUE",
                upvotes = 12,
                peerNodeId = "peer_chiado_42"
            ),
            CommunityTipEntity(
                id = "p2p_tip_103",
                spotId = "spot_1", // Santa Luzia
                spotTitle = "Miradouro de Santa Luzia",
                authorName = "@tiago_graca",
                content = "Acoustic fado busker playing by the azulejos pergola nook. Beautiful quiet ambience.",
                timestamp = now - (3 * 3600 * 1000L), // 3 hours ago (ACTIVE)
                isStatusUpdate = false,
                statusCategory = "ATMOSPHERE",
                upvotes = 19,
                peerNodeId = "peer_graca_19"
            ),
            CommunityTipEntity(
                id = "p2p_tip_104",
                spotId = "spot_6", // Senhora do Monte
                spotTitle = "Senhora do Monte Highest Overlook",
                authorName = "@clara_alfama",
                content = "Windy on the north stone ledge. Bring a light layer if staying through twilight.",
                timestamp = now - (10 * 3600 * 1000L), // 10 hours ago (AGING)
                isStatusUpdate = false,
                statusCategory = "INSIGHT",
                upvotes = 8,
                peerNodeId = "peer_alfama_88"
            ),
            CommunityTipEntity(
                id = "p2p_tip_105",
                spotId = "spot_4", // Tile Tunnel
                spotTitle = "Hidden Tile Tunnel Shortcut",
                authorName = "@tiago_graca",
                content = "Tunnel dry and well lit today! Saves 10 minutes climbing up from Baixa.",
                timestamp = now - (18 * 3600 * 1000L), // 18 hours ago (AGING)
                isStatusUpdate = true,
                statusCategory = "INSIGHT",
                upvotes = 15,
                peerNodeId = "peer_graca_19"
            )
        )

        // Filter out tips that the local device already has
        val newIncomingTips = candidateTips.filterNot { it.id in existingTipIds }

        onPhaseChanged(MeshSyncPhase.SYNC_SUCCESS)
        delay(400)
        onPhaseChanged(MeshSyncPhase.IDLE)

        val result = PeerMeshSyncResult(
            connectedPeers = simulatedPeers,
            tipsReceived = newIncomingTips.size,
            tipsTransmitted = 2
        )

        return Pair(result, newIncomingTips)
    }

    /**
     * Seeds initial community tips across primary Lisbon gems.
     */
    fun createSeedTips(): List<CommunityTipEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            CommunityTipEntity(
                id = "seed_tip_1",
                spotId = "spot_2", // Pastelaria Santo António
                spotTitle = "Pastelaria Santo António — Warm Custard Batch",
                authorName = "@patricia_bakes",
                content = "Bakery fresh batch alert: Just came out of the oven 6m ago. Crust is perfection.",
                timestamp = now - (6 * 60 * 1000L), // 6m ago -> FRESH
                isStatusUpdate = true,
                statusCategory = "FRESH_BATCH",
                upvotes = 24,
                peerNodeId = "peer_seed_01"
            ),
            CommunityTipEntity(
                id = "seed_tip_2",
                spotId = "spot_2", // Pastelaria Santo António
                spotTitle = "Pastelaria Santo António — Warm Custard Batch",
                authorName = "@alfama_scout",
                content = "Bakery out of cinnamon tarts for the next 15 mins while new tray bakes. Regular pastel still available!",
                timestamp = now - (35 * 60 * 1000L), // 35m ago -> FRESH
                isStatusUpdate = true,
                statusCategory = "STOCK_SHORTAGE",
                upvotes = 15,
                peerNodeId = "peer_seed_02"
            ),
            CommunityTipEntity(
                id = "seed_tip_3",
                spotId = "spot_1", // Miradouro de Santa Luzia
                spotTitle = "Miradouro de Santa Luzia — Secret Pergola Nook",
                authorName = "@marco_lisbon",
                content = "Main balcony is packed with a walking tour, but the tiled pergola bench around the bend is completely empty.",
                timestamp = now - (90 * 60 * 1000L), // 1.5h ago -> FRESH
                isStatusUpdate = true,
                statusCategory = "LINE_QUEUE",
                upvotes = 32,
                peerNodeId = "peer_seed_03"
            ),
            CommunityTipEntity(
                id = "seed_tip_4",
                spotId = "spot_3", // Convento do Carmo
                spotTitle = "Convento do Carmo Ruins & Gothic Arches",
                authorName = "@pedro_historian",
                content = "Elevador de Santa Justa queue is 50 people deep. Walk up through Largo do Carmo instead for immediate entry.",
                timestamp = now - (4 * 3600 * 1000L), // 4h ago -> ACTIVE
                isStatusUpdate = true,
                statusCategory = "LINE_QUEUE",
                upvotes = 41,
                peerNodeId = "peer_seed_04"
            ),
            CommunityTipEntity(
                id = "seed_tip_5",
                spotId = "spot_5", // Manteigaria
                spotTitle = "Manteigaria Fábrica — Warm Tart Counter",
                authorName = "@lisbon_foodie",
                content = "No wait at the standing counter right now. Ring the brass bell when you want coffee!",
                timestamp = now - (14 * 3600 * 1000L), // 14h ago -> AGING
                isStatusUpdate = true,
                statusCategory = "LINE_QUEUE",
                upvotes = 19,
                peerNodeId = "peer_seed_05"
            ),
            CommunityTipEntity(
                id = "seed_tip_6",
                spotId = "spot_4", // Tile Tunnel
                spotTitle = "Hidden Tile Tunnel Shortcut — Alfama ↔ Baixa",
                authorName = "@ana_traveler",
                content = "Iron gate open as usual. Look for the brass falcon above the archway.",
                timestamp = now - (28 * 3600 * 1000L), // 28h ago -> EXPIRED
                isStatusUpdate = false,
                statusCategory = "INSIGHT",
                upvotes = 11,
                peerNodeId = "peer_seed_06"
            )
        )
    }
}

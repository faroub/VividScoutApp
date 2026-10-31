package com.example.data.p2p

import com.example.data.local.CommunityTipEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeerMeshSyncEngineTest {

    @Before
    fun setUp() {
        DemoModeProvider.reset()
    }

    @After
    fun tearDown() {
        DemoModeProvider.reset()
    }

    @Test
    fun testDefaultModeHasZeroDemoPeersAndNoSimulatedNetworkDelays() = runBlocking {
        // Assert demo peer sync is OFF by default in release/normal mode
        assertFalse("Demo peer sync must be disabled by default", DemoModeProvider.isDemoPeerSyncEnabled)
        assertTrue("simulatedPeers must be empty when demo mode is OFF", PeerMeshSyncEngine.simulatedPeers.isEmpty())

        var phaseChangedCount = 0
        val startTime = System.currentTimeMillis()
        val (result, tips) = PeerMeshSyncEngine.performGossipRound(
            existingTipIds = emptySet(),
            onPhaseChanged = { phaseChangedCount++ }
        )
        val elapsed = System.currentTimeMillis() - startTime

        // In release mode: 0 simulated peers, 0 incoming fake tips, fast execution (<100ms, no fake delays)
        assertEquals("Connected peers must be empty", 0, result.connectedPeers.size)
        assertEquals("Tips received must be 0", 0, result.tipsReceived)
        assertEquals("Tips transmitted must be 0", 0, result.tipsTransmitted)
        assertTrue("New tips list must be empty", tips.isEmpty())
        assertTrue("Must not incur fake network delay in release mode", elapsed < 300)
    }

    @Test
    fun testDemoModeReturnsSimulatedPeersWhenExplicitlyEnabled() = runBlocking {
        DemoModeProvider.isDemoPeerSyncEnabled = true

        assertEquals("Demo mode must return 3 simulated peers", 3, PeerMeshSyncEngine.simulatedPeers.size)

        val (result, tips) = PeerMeshSyncEngine.performGossipRound(existingTipIds = emptySet())

        assertEquals(3, result.connectedPeers.size)
        assertTrue(tips.isNotEmpty())
        assertEquals(tips.size, result.tipsReceived)
    }

    @Test
    fun testSampleContentIdentification() {
        // 1. Seed tips must be identified as sample content
        val seedTips = PeerMeshSyncEngine.createSeedTips()
        assertTrue(seedTips.isNotEmpty())
        seedTips.forEach { tip ->
            assertTrue("Seed tip ${tip.id} must be flagged as sample content", tip.isSampleContent)
        }

        // 2. Demo peer tips must be identified as sample content
        val demoTip = CommunityTipEntity(
            id = "p2p_tip_101",
            spotId = "spot_1",
            spotTitle = "Miradouro",
            authorName = "@clara_alfama",
            content = "Sample tip",
            timestamp = System.currentTimeMillis(),
            peerNodeId = "peer_alfama_88",
            isLocallyAuthored = false
        )
        assertTrue("Demo P2P tip must be flagged as sample content", demoTip.isSampleContent)

        // 3. User authored tip must NOT be identified as sample content
        val userTip = CommunityTipEntity(
            id = "user_tip_123",
            spotId = "spot_1",
            spotTitle = "Miradouro",
            authorName = "@you",
            content = "Real user note",
            timestamp = System.currentTimeMillis(),
            peerNodeId = "local-device",
            isLocallyAuthored = true
        )
        assertFalse("Locally authored tip must not be flagged as sample content", userTip.isSampleContent)
    }
}

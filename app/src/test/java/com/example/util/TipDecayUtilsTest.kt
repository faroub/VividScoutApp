package com.example.util

import com.example.data.p2p.MeshSyncPhase
import com.example.data.p2p.PeerMeshSyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TipDecayUtilsTest {

    @Test
    fun testDecayTierFreshUnderTwoHours() {
        val now = 10_000_000L
        val timestamp = now - (45 * 60 * 1000L) // 45 minutes ago

        val tier = TipDecayUtils.calculateDecayTier(timestamp, now)

        assertEquals(TipDecayTier.FRESH, tier)
        assertTrue(tier.isFreshOrActive)
    }

    @Test
    fun testDecayTierBoundaryTwoHours() {
        val now = 10_000_000L
        val timestamp = now - (2 * 60 * 60 * 1000L) // Exactly 2 hours ago

        val tier = TipDecayUtils.calculateDecayTier(timestamp, now)

        assertEquals(TipDecayTier.FRESH, tier)
        assertTrue(tier.isFreshOrActive)
    }

    @Test
    fun testDecayTierActiveBetweenTwoAndEightHours() {
        val now = 10_000_000L
        val timestamp = now - (5 * 60 * 60 * 1000L) // 5 hours ago

        val tier = TipDecayUtils.calculateDecayTier(timestamp, now)

        assertEquals(TipDecayTier.ACTIVE, tier)
        assertTrue(tier.isFreshOrActive)
    }

    @Test
    fun testDecayTierAgingBetweenEightAndTwentyFourHours() {
        val now = 10_000_000L
        val timestamp = now - (14 * 60 * 60 * 1000L) // 14 hours ago

        val tier = TipDecayUtils.calculateDecayTier(timestamp, now)

        assertEquals(TipDecayTier.AGING, tier)
        assertFalse(tier.isFreshOrActive)
    }

    @Test
    fun testDecayTierExpiredOverTwentyFourHours() {
        val now = 10_000_000L
        val timestamp = now - (25 * 60 * 60 * 1000L) // 25 hours ago

        val tier = TipDecayUtils.calculateDecayTier(timestamp, now)

        assertEquals(TipDecayTier.EXPIRED, tier)
        assertFalse(tier.isFreshOrActive)
    }

    @Test
    fun testFormatRelativeTime() {
        val now = 100_000_000L

        assertEquals("just now", TipDecayUtils.formatRelativeTime(now - 30_000L, now))
        assertEquals("15m ago", TipDecayUtils.formatRelativeTime(now - (15 * 60 * 1000L), now))
        assertEquals("4h ago", TipDecayUtils.formatRelativeTime(now - (4 * 3600 * 1000L), now))
        assertEquals("yesterday", TipDecayUtils.formatRelativeTime(now - (24 * 3600 * 1000L), now))
        assertEquals("3d ago", TipDecayUtils.formatRelativeTime(now - (3 * 24 * 3600 * 1000L), now))
    }

    @Test
    fun testPeerMeshSyncGossipRound() = runBlocking {
        val phasesRecorded = mutableListOf<MeshSyncPhase>()

        // 1. First sync with empty local cache -> receives all peer tips
        val (result1, newTips1) = PeerMeshSyncEngine.performGossipRound(
            existingTipIds = emptySet(),
            onPhaseChanged = { phasesRecorded.add(it) }
        )

        assertEquals(3, result1.connectedPeers.size)
        assertTrue(newTips1.isNotEmpty())
        assertEquals(newTips1.size, result1.tipsReceived)
        assertTrue(phasesRecorded.contains(MeshSyncPhase.DISCOVERING_PEERS))
        assertTrue(phasesRecorded.contains(MeshSyncPhase.EXCHANGING_GOSSIP))

        // 2. Second sync when tips already exist -> receives 0 duplicates
        val allIds = newTips1.map { it.id }.toSet()
        val (result2, newTips2) = PeerMeshSyncEngine.performGossipRound(
            existingTipIds = allIds
        )

        assertEquals(0, newTips2.size)
        assertEquals(0, result2.tipsReceived)
    }

    @Test
    fun testSeedTipsAreNonEmptyAndCategorized() {
        val seeds = PeerMeshSyncEngine.createSeedTips()
        assertTrue(seeds.size >= 5)

        val statusUpdates = seeds.filter { it.isStatusUpdate }
        assertTrue("Should include real-time spot status alerts", statusUpdates.isNotEmpty())

        val categories = seeds.map { it.statusCategory }.toSet()
        assertTrue(categories.contains("STOCK_SHORTAGE"))
        assertTrue(categories.contains("FRESH_BATCH"))
    }
}

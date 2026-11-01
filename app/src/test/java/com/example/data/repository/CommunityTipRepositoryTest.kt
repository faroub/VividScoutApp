package com.example.data.repository

import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommunityTipRepositoryTest {

    private lateinit var fakeDao: FakeCommunityTipDao
    private lateinit var repository: CommunityTipRepository

    @Before
    fun setUp() {
        fakeDao = FakeCommunityTipDao()
        repository = CommunityTipRepository(fakeDao, null)
    }

    @Test
    fun postTip_savesLocallyAndReturnsEntity() = runBlocking {
        val tip = repository.postTip(
            spotId = "spot_dresden_1",
            spotTitle = "Frauenkirche View",
            content = "Great afternoon lighting from Neumarkt terrace.",
            authorName = "@dresden_scout"
        )

        assertNotNull(tip)
        assertEquals("spot_dresden_1", tip.spotId)
        assertEquals("Frauenkirche View", tip.spotTitle)
        assertEquals("@dresden_scout", tip.authorName)
        assertEquals(0, tip.upvotes)
        assertTrue(tip.isLocallyAuthored)
        assertEquals(1, fakeDao.tips.size)
    }

    @Test
    fun upvoteTip_firstVoteSucceeds_secondVoteFromSameUserRejected_upvoteCountIncrementedOnce() = runBlocking {
        val initialTip = CommunityTipEntity(
            id = "tip_123",
            spotId = "spot_1",
            spotTitle = "Test Spot",
            authorName = "@test",
            content = "Great spot!",
            timestamp = System.currentTimeMillis(),
            upvotes = 3
        )
        fakeDao.tips.add(initialTip)

        val userA = "user_alpha_42"

        // First vote from user A must succeed
        val firstVoteResult = repository.upvoteTip("tip_123", userA)
        assertTrue("First vote from user A must succeed", firstVoteResult)

        // Upvote count should be incremented once from 3 to 4
        val updatedAfterFirstVote = fakeDao.getTipById("tip_123")
        assertNotNull(updatedAfterFirstVote)
        assertEquals(4, updatedAfterFirstVote?.upvotes)

        // Second vote from user A on the same tip must be rejected
        val secondVoteResult = repository.upvoteTip("tip_123", userA)
        assertFalse("Second vote from same user A must be rejected", secondVoteResult)

        // Upvote count must remain 4 (incremented only once)
        val updatedAfterSecondVote = fakeDao.getTipById("tip_123")
        assertNotNull(updatedAfterSecondVote)
        assertEquals(4, updatedAfterSecondVote?.upvotes)
    }

    // Fake CommunityTipDao for unit testing
    class FakeCommunityTipDao : CommunityTipDao {
        val tips = mutableListOf<CommunityTipEntity>()

        override fun getAllTipsFlow(): Flow<List<CommunityTipEntity>> = flowOf(tips)
        override fun getTipsForSpotFlow(spotId: String): Flow<List<CommunityTipEntity>> = flowOf(tips.filter { it.spotId == spotId })
        override fun getLatestStatusForSpot(spotId: String): Flow<CommunityTipEntity?> = flowOf(tips.lastOrNull { it.spotId == spotId && it.isStatusUpdate })
        override fun getAllStatusUpdatesFlow(): Flow<List<CommunityTipEntity>> = flowOf(tips.filter { it.isStatusUpdate })
        override suspend fun getTipById(tipId: String): CommunityTipEntity? = tips.find { it.id == tipId }
        override suspend fun insertTip(tip: CommunityTipEntity) { tips.add(tip) }
        override suspend fun insertTips(newTips: List<CommunityTipEntity>) { tips.addAll(newTips) }
        override suspend fun updateTip(tip: CommunityTipEntity) {
            val idx = tips.indexOfFirst { it.id == tip.id }
            if (idx >= 0) tips[idx] = tip
        }
        override suspend fun incrementUpvote(tipId: String) {
            val idx = tips.indexOfFirst { it.id == tipId }
            if (idx >= 0) {
                val t = tips[idx]
                tips[idx] = t.copy(upvotes = t.upvotes + 1)
            }
        }
        override suspend fun deleteTip(tipId: String) { tips.removeIf { it.id == tipId } }
        override suspend fun getLocallyAuthoredTipsSync(): List<CommunityTipEntity> = tips.filter { it.isLocallyAuthored }
        override suspend fun getTipCount(): Int = tips.size
    }
}

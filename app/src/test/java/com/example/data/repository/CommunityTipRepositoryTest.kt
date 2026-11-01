package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.TipVoteDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommunityTipRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var tipDao: CommunityTipDao
    private lateinit var tipVoteDao: TipVoteDao
    private lateinit var repository: CommunityTipRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tipDao = db.communityTipDao()
        tipVoteDao = db.tipVoteDao()
        repository = CommunityTipRepository(tipDao, tipVoteDao, context = null)
    }

    @After
    fun tearDown() {
        db.close()
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
    }

    @Test
    fun upvoteTip_firstVoteSucceeds_secondVoteFromSameUserRejected() = runBlocking {
        val initialTip = CommunityTipEntity(
            id = "tip_123",
            spotId = "spot_1",
            spotTitle = "Test Spot",
            authorName = "@test",
            content = "Great spot!",
            timestamp = System.currentTimeMillis(),
            upvotes = 3
        )
        tipDao.insertTip(initialTip)

        val userA = "user_alpha_42"

        // First vote from user A must succeed locally and in cloud
        val firstVoteResult = repository.upvoteTip("tip_123", userA)
        assertTrue("First vote from user A must succeed", firstVoteResult)

        // Upvote count should be incremented once from 3 to 4
        val updatedAfterFirstVote = tipDao.getTipById("tip_123")
        assertNotNull(updatedAfterFirstVote)
        assertEquals(4, updatedAfterFirstVote?.upvotes)

        // Second vote from user A on the same tip must be rejected
        val secondVoteResult = repository.upvoteTip("tip_123", userA)
        assertFalse("Second vote from same user A must be rejected", secondVoteResult)

        // Upvote count must remain 4 (incremented only once)
        val updatedAfterSecondVote = tipDao.getTipById("tip_123")
        assertNotNull(updatedAfterSecondVote)
        assertEquals(4, updatedAfterSecondVote?.upvotes)
    }

    @Test
    fun upvoteTip_votePersistsAcrossNewRepositoryInstanceBackedBySameDatabase() = runBlocking {
        val initialTip = CommunityTipEntity(
            id = "tip_persisted",
            spotId = "spot_2",
            spotTitle = "Persisted Spot",
            authorName = "@scout",
            content = "Awesome view!",
            timestamp = System.currentTimeMillis(),
            upvotes = 0
        )
        tipDao.insertTip(initialTip)

        val userId = "user_beta_99"

        // Perform vote on initial repository instance
        val voteSuccess = repository.upvoteTip("tip_persisted", userId)
        assertTrue("First vote on initial repository instance must succeed", voteSuccess)

        // Instantiate a NEW repository instance backed by the same Room database
        val newRepositoryInstance = CommunityTipRepository(
            dao = db.communityTipDao(),
            tipVoteDao = db.tipVoteDao(),
            context = null
        )

        // Attempt second vote using new repository instance
        val duplicateVoteOnNewInstance = newRepositoryInstance.upvoteTip("tip_persisted", userId)
        assertFalse("Second vote on new repository instance must be rejected by persisted vote table", duplicateVoteOnNewInstance)

        val finalTip = tipDao.getTipById("tip_persisted")
        assertEquals(1, finalTip?.upvotes)
    }
}

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
        assertEquals(0, tip.totalUpvotes)
        assertFalse("Newly posted tip starts unsynced", tip.isSyncedToCloud)
        assertTrue(tip.isLocallyAuthored)
    }

    @Test
    fun totalUpvotes_combinesLegacyUpvotesAndNewRecordedVotes() = runBlocking {
        val legacyTip = CommunityTipEntity(
            id = "tip_legacy_500",
            spotId = "spot_5",
            spotTitle = "Historical Bakery",
            authorName = "@patricia",
            content = "Pastel de nata out of oven",
            timestamp = System.currentTimeMillis(),
            legacyUpvotes = 24,
            upvotes = 0,
            isSyncedToCloud = true
        )
        tipDao.insertTip(legacyTip)

        val fetchedBeforeVote = tipDao.getTipById("tip_legacy_500")
        assertNotNull(fetchedBeforeVote)
        assertEquals(24, fetchedBeforeVote?.legacyUpvotes)
        assertEquals(0, fetchedBeforeVote?.upvotes)
        assertEquals(24, fetchedBeforeVote?.totalUpvotes)

        // Perform a new vote from user_101
        val voteResult = repository.upvoteTip("tip_legacy_500", "user_101")
        assertTrue(voteResult)

        val fetchedAfterVote = tipDao.getTipById("tip_legacy_500")
        assertEquals(24, fetchedAfterVote?.legacyUpvotes)
        assertEquals(1, fetchedAfterVote?.upvotes)
        assertEquals(25, fetchedAfterVote?.totalUpvotes)
    }

    @Test
    fun newVoteOnMigratedDocument_incrementsUpvotesAndLeavesLegacyUpvotesUnchanged() = runBlocking {
        val migratedTip = CommunityTipEntity(
            id = "tip_migrated_789",
            spotId = "spot_8",
            spotTitle = "Migrated Viewpoint",
            authorName = "@migrated_author",
            content = "Migrated tip with separate legacy upvotes",
            timestamp = System.currentTimeMillis(),
            legacyUpvotes = 42,
            upvotes = 0,
            isSyncedToCloud = true
        )
        tipDao.insertTip(migratedTip)

        val voteResult = repository.upvoteTip("tip_migrated_789", "voter_user_1")
        assertTrue("Vote on migrated document must succeed", voteResult)

        val updatedTip = tipDao.getTipById("tip_migrated_789")
        assertNotNull(updatedTip)
        assertEquals("legacyUpvotes must remain unchanged at 42", 42, updatedTip?.legacyUpvotes)
        assertEquals("recorded upvotes must be incremented to 1", 1, updatedTip?.upvotes)
        assertEquals("totalUpvotes must evaluate to legacyUpvotes + upvotes (43)", 43, updatedTip?.totalUpvotes)
    }

    @Test
    fun syncDown_migratedDocument_totalUpvotesEqualsLegacyPlusRecorded() = runBlocking {
        val migratedTipFromCloud = CommunityTipEntity(
            id = "tip_cloud_migrated_1",
            spotId = "spot_9",
            spotTitle = "Cloud Migrated Spot",
            authorName = "@cloud_scout",
            content = "Cloud content",
            timestamp = System.currentTimeMillis(),
            legacyUpvotes = 18,
            upvotes = 5,
            isSyncedToCloud = true
        )
        tipDao.insertTip(migratedTipFromCloud)

        val entity = tipDao.getTipById("tip_cloud_migrated_1")
        assertNotNull(entity)
        assertEquals(18, entity?.legacyUpvotes)
        assertEquals(5, entity?.upvotes)
        assertEquals("totalUpvotes must equal legacyUpvotes + upvotes (18 + 5 = 23)", 23, entity?.totalUpvotes)
    }

    @Test
    fun upvoteTip_syncedTipCanBeVoted() = runBlocking {
        val syncedTip = CommunityTipEntity(
            id = "tip_synced_100",
            spotId = "spot_1",
            spotTitle = "Synced Spot",
            authorName = "@synced_scout",
            content = "Great view!",
            timestamp = System.currentTimeMillis(),
            upvotes = 3,
            isSyncedToCloud = true
        )
        tipDao.insertTip(syncedTip)

        val voteResult = repository.upvoteTip("tip_synced_100", "user_123")
        assertTrue("Synced tip must be votable", voteResult)

        val updatedTip = tipDao.getTipById("tip_synced_100")
        assertEquals(4, updatedTip?.upvotes)
        assertEquals(4, updatedTip?.totalUpvotes)
    }

    @Test
    fun upvoteTip_unsyncedTipCannotBeVoted() = runBlocking {
        val unsyncedTip = CommunityTipEntity(
            id = "tip_unsynced_200",
            spotId = "spot_2",
            spotTitle = "Unsynced Local Draft",
            authorName = "@local_scout",
            content = "Local note",
            timestamp = System.currentTimeMillis(),
            upvotes = 0,
            isSyncedToCloud = false
        )
        tipDao.insertTip(unsyncedTip)

        val voteResult = repository.upvoteTip("tip_unsynced_200", "user_123")
        assertFalse("Unsynced tip must NOT be votable", voteResult)

        val updatedTip = tipDao.getTipById("tip_unsynced_200")
        assertEquals(0, updatedTip?.upvotes)
    }

    @Test
    fun markTipAsSynced_setsFlagAfterSuccessfulSync() = runBlocking {
        val draftTip = CommunityTipEntity(
            id = "tip_to_sync",
            spotId = "spot_3",
            spotTitle = "Draft Spot",
            authorName = "@author",
            content = "Local content",
            timestamp = System.currentTimeMillis(),
            upvotes = 0,
            isSyncedToCloud = false
        )
        tipDao.insertTip(draftTip)

        assertFalse("Initial draft is unsynced", tipDao.getTipById("tip_to_sync")?.isSyncedToCloud == true)

        // Mark as synced after successful cloud write
        tipDao.markTipAsSynced("tip_to_sync")

        val syncedTip = tipDao.getTipById("tip_to_sync")
        assertNotNull(syncedTip)
        assertTrue("isSyncedToCloud flag must be set to true after sync", syncedTip?.isSyncedToCloud == true)
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
            upvotes = 3,
            isSyncedToCloud = true
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
            upvotes = 0,
            isSyncedToCloud = true
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

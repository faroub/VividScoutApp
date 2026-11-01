package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CommunityTipDao
import com.example.data.local.CommunityTipEntity
import com.example.data.local.TipVoteDao
import com.example.data.local.TipVoteEntity
import com.example.data.sync.TipUpvoteCloud
import junit.framework.TestCase
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
        repository = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = FakeTipUpvoteCloud(returnResult = true)
        )
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
    fun upvoteTip_unmigratedDocument_rejectedWithNoCloudWrite() = runBlocking {
        val unmigratedTip = CommunityTipEntity(
            id = "tip_unmigrated_999",
            spotId = "spot_10",
            spotTitle = "Unmigrated Spot",
            authorName = "@unmigrated_scout",
            content = "Legacy content",
            timestamp = System.currentTimeMillis(),
            legacyUpvotes = 15,
            upvotes = 0,
            isSyncedToCloud = true,
            isMigrated = false
        )
        tipDao.insertTip(unmigratedTip)

        val fakeCloud = FakeTipUpvoteCloud(returnResult = true)
        val testRepo = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = fakeCloud
        )

        val voteResult = testRepo.upvoteTip("tip_unmigrated_999", "voter_user_42")

        assertFalse("Unmigrated tip must be rejected before cloud write", voteResult)
        assertEquals("Cloud service must NOT be called for unmigrated tip", 0, fakeCloud.callCount)

        val updatedTip = tipDao.getTipById("tip_unmigrated_999")
        assertNotNull(updatedTip)
        assertEquals(0, updatedTip?.upvotes)
        assertEquals(15, updatedTip?.legacyUpvotes)
    }

    @Test
    fun upvoteTip_migratedDocument_accepted() = runBlocking {
        val migratedTip = CommunityTipEntity(
            id = "tip_migrated_888",
            spotId = "spot_11",
            spotTitle = "Migrated Spot",
            authorName = "@migrated_scout",
            content = "Migrated content",
            timestamp = System.currentTimeMillis(),
            legacyUpvotes = 20,
            upvotes = 0,
            isSyncedToCloud = true,
            isMigrated = true
        )
        tipDao.insertTip(migratedTip)

        val fakeCloud = FakeTipUpvoteCloud(returnResult = true)
        val testRepo = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = fakeCloud
        )

        val voteResult = testRepo.upvoteTip("tip_migrated_888", "voter_user_42")

        assertTrue("Migrated tip must be accepted", voteResult)
        assertEquals("Cloud service must be called once", 1, fakeCloud.callCount)

        val updatedTip = tipDao.getTipById("tip_migrated_888")
        assertNotNull(updatedTip)
        assertEquals(1, updatedTip?.upvotes)
        assertEquals(20, updatedTip?.legacyUpvotes)
        assertEquals(21, updatedTip?.totalUpvotes)
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
    fun upvoteTip_whenContextIsNullAndDefaultCloudServiceUsed_returnsFalseAndChangesNothing() = runBlocking {
        val tip = CommunityTipEntity(
            id = "tip_null_ctx_1",
            spotId = "spot_1",
            spotTitle = "Null Context Spot",
            authorName = "@scout",
            content = "Null context test",
            timestamp = System.currentTimeMillis(),
            upvotes = 3,
            isSyncedToCloud = true
        )
        tipDao.insertTip(tip)

        // Repository created with context = null and default cloudService (FirestoreTipUpvoteCloud(null))
        val nullContextRepo = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null
        )

        val voteResult = nullContextRepo.upvoteTip("tip_null_ctx_1", "user_123")
        assertFalse("upvoteTip must return false when context is null and default cloud service is used", voteResult)

        // Local vote row must NOT be inserted
        assertFalse("Local vote row must NOT be inserted when context is null", tipVoteDao.hasUserVoted("tip_null_ctx_1", "user_123"))

        // Upvote count must remain unchanged at 3
        val updatedTip = tipDao.getTipById("tip_null_ctx_1")
        assertEquals(3, updatedTip?.upvotes)
    }

    @Test
    fun upvoteTip_cloudReturnsTrue_localVoteRecordedAndCountIncremented() = runBlocking {
        val tip = CommunityTipEntity(
            id = "tip_cloud_true_1",
            spotId = "spot_1",
            spotTitle = "Cloud Spot",
            authorName = "@cloud",
            content = "Cloud test content",
            timestamp = System.currentTimeMillis(),
            upvotes = 2,
            isSyncedToCloud = true
        )
        tipDao.insertTip(tip)

        val fakeCloud = FakeTipUpvoteCloud(returnResult = true)
        val cloudRepo = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = fakeCloud
        )

        val voteResult = cloudRepo.upvoteTip("tip_cloud_true_1", "user_cloud_1")
        assertTrue("upvoteTip should return true when cloud returns true", voteResult)
        assertEquals("Cloud service should be invoked once", 1, fakeCloud.callCount)

        // Local vote row must be inserted
        assertTrue("Local vote row must be recorded", tipVoteDao.hasUserVoted("tip_cloud_true_1", "user_cloud_1"))

        // Upvote count must be incremented by 1
        val updatedTip = tipDao.getTipById("tip_cloud_true_1")
        assertEquals(3, updatedTip?.upvotes)
    }

    @Test
    fun upvoteTip_cloudReturnsFalse_noLocalVoteInserted_countUnchanged_returnsFalse() = runBlocking {
        val tip = CommunityTipEntity(
            id = "tip_cloud_false_2",
            spotId = "spot_2",
            spotTitle = "Cloud False Spot",
            authorName = "@cloud_false",
            content = "Content",
            timestamp = System.currentTimeMillis(),
            upvotes = 5,
            isSyncedToCloud = true
        )
        tipDao.insertTip(tip)

        val fakeCloud = FakeTipUpvoteCloud(returnResult = false)
        val cloudRepo = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = fakeCloud
        )

        val voteResult = cloudRepo.upvoteTip("tip_cloud_false_2", "user_cloud_2")
        assertFalse("upvoteTip should return false when cloud returns false", voteResult)
        assertEquals("Cloud service should be invoked once", 1, fakeCloud.callCount)

        // Local vote row must NOT be inserted
        assertFalse("No local vote row should be inserted on cloud failure", tipVoteDao.hasUserVoted("tip_cloud_false_2", "user_cloud_2"))

        // Count must remain unchanged
        val updatedTip = tipDao.getTipById("tip_cloud_false_2")
        assertEquals(5, updatedTip?.upvotes)
    }

    @Test
    fun upvoteTip_cloudThrowsException_handledGracefully_noLocalVoteInserted_countUnchanged() = runBlocking {
        val tip = CommunityTipEntity(
            id = "tip_cloud_throw_3",
            spotId = "spot_3",
            spotTitle = "Throw Spot",
            authorName = "@throw_user",
            content = "Throw content",
            timestamp = System.currentTimeMillis(),
            upvotes = 10,
            isSyncedToCloud = true
        )
        tipDao.insertTip(tip)

        val fakeCloud = FakeTipUpvoteCloud(shouldThrow = true)
        val cloudRepo = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = fakeCloud
        )

        val voteResult = try {
            cloudRepo.upvoteTip("tip_cloud_throw_3", "user_cloud_3")
        } catch (e: Exception) {
            TestCase.fail("Exception must not escape upvoteTip: ${e.message}")
            false
        }

        assertFalse("upvoteTip should return false when cloud throws exception", voteResult)

        // Local vote row must NOT be inserted
        assertFalse("No local vote row should be inserted on cloud exception", tipVoteDao.hasUserVoted("tip_cloud_throw_3", "user_cloud_3"))

        // Count must remain unchanged
        val updatedTip = tipDao.getTipById("tip_cloud_throw_3")
        assertEquals(10, updatedTip?.upvotes)
    }

    @Test
    fun upvoteTip_secondVoteAfterSuccessfulFirstVote_doesNotCallCloudAgain() = runBlocking {
        val tip = CommunityTipEntity(
            id = "tip_cloud_dedupe_4",
            spotId = "spot_4",
            spotTitle = "Dedupe Spot",
            authorName = "@dedupe_user",
            content = "Dedupe content",
            timestamp = System.currentTimeMillis(),
            upvotes = 0,
            isSyncedToCloud = true
        )
        tipDao.insertTip(tip)

        val fakeCloud = FakeTipUpvoteCloud(returnResult = true)
        val cloudRepo = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = fakeCloud
        )

        val userX = "user_dedupe_x"

        // First vote succeeds and invokes cloud once
        val firstVoteResult = cloudRepo.upvoteTip("tip_cloud_dedupe_4", userX)
        assertTrue(firstVoteResult)
        assertEquals(1, fakeCloud.callCount)

        // Second vote from userX: local vote check fails first, cloud is NOT called again
        val secondVoteResult = cloudRepo.upvoteTip("tip_cloud_dedupe_4", userX)
        assertFalse(secondVoteResult)
        assertEquals("Cloud call count must remain 1 after second duplicate vote attempt", 1, fakeCloud.callCount)

        val updatedTip = tipDao.getTipById("tip_cloud_dedupe_4")
        assertEquals(1, updatedTip?.upvotes)
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
    fun upvoteTip_transactionRollbackOnSecondWriteFailure_preservesOriginalState() = runBlocking {
        val tip = CommunityTipEntity(
            id = "tip_rollback_999",
            spotId = "spot_10",
            spotTitle = "Rollback Spot",
            authorName = "@test_author",
            content = "Rollback test",
            timestamp = System.currentTimeMillis(),
            upvotes = 5,
            isSyncedToCloud = true
        )
        tipDao.insertTip(tip)

        // Create a throwing CommunityTipDao wrapper that forces incrementUpvote to throw an IllegalStateException
        val throwingCommunityTipDao = object : CommunityTipDao by tipDao {
            override suspend fun incrementUpvote(tipId: String) {
                throw IllegalStateException("Forced database failure during second write operation in transaction")
            }
        }

        val testRepo = CommunityTipRepository(
            dao = throwingCommunityTipDao,
            tipVoteDao = tipVoteDao,
            context = null,
            cloudService = FakeTipUpvoteCloud(returnResult = true)
        )

        val voteResult = try {
            testRepo.upvoteTip("tip_rollback_999", "user_rollback")
        } catch (e: Exception) {
            false
        }

        assertFalse("Upvote should fail/return false when second write in transaction throws", voteResult)

        // Assert that Room @Transaction rolled back the first write (vote row was NOT inserted)
        val hasVoted = tipVoteDao.hasUserVoted("tip_rollback_999", "user_rollback")
        assertFalse("Vote row must NOT be committed when transaction fails", hasVoted)

        // Assert that the tip upvote count in database did NOT change (remains 5)
        val tipAfterFailure = tipDao.getTipById("tip_rollback_999")
        assertNotNull(tipAfterFailure)
        assertEquals("Upvote count must remain unchanged at 5", 5, tipAfterFailure?.upvotes)
    }

    @Test
    fun upvoteTip_fakeDaoThrowingMidTransaction_rollbackAndUnchangedState() = runBlocking {
        val tip = CommunityTipEntity(
            id = "tip_fake_throw_1",
            spotId = "spot_11",
            spotTitle = "Fake Throw Spot",
            authorName = "@author_fake",
            content = "Fake throw content",
            timestamp = System.currentTimeMillis(),
            upvotes = 10,
            isSyncedToCloud = true
        )
        tipDao.insertTip(tip)

        val fakeCloud = FakeTipUpvoteCloud(returnResult = true)

        // Custom fake TipVoteDao that simulates a mid-transaction failure and state rollback
        var voteRowInsertedInFake = false
        val throwingTipVoteDao = object : TipVoteDao() {
            override suspend fun hasUserVoted(tipId: String, userId: String): Boolean = voteRowInsertedInFake
            override suspend fun insertVote(vote: TipVoteEntity) { voteRowInsertedInFake = true }
            override suspend fun deleteVote(tipId: String, userId: String) { voteRowInsertedInFake = false }
            override suspend fun getVoteCountForTip(tipId: String): Int = if (voteRowInsertedInFake) 1 else 0

            override suspend fun recordVoteAndIncrementCount(
                tipId: String,
                userId: String,
                communityTipDao: CommunityTipDao
            ) {
                // Simulate first write succeeds
                insertVote(TipVoteEntity(tipId = tipId, userId = userId))
                // Simulate second write throws and rolls back the fake
                try {
                    communityTipDao.incrementUpvote(tipId)
                } catch (e: Exception) {
                    voteRowInsertedInFake = false // Rollback simulated state
                    throw e
                }
            }
        }

        val throwingTipDao = object : CommunityTipDao by tipDao {
            override suspend fun incrementUpvote(tipId: String) {
                throw IllegalStateException("Fake second write failure")
            }
        }

        val testRepo = CommunityTipRepository(
            dao = throwingTipDao,
            tipVoteDao = throwingTipVoteDao,
            context = null,
            cloudService = fakeCloud
        )

        val voteResult = try {
            testRepo.upvoteTip("tip_fake_throw_1", "fake_voter_1")
        } catch (e: Exception) {
            false
        }

        assertFalse("upvoteTip must return false when second write throws in transaction", voteResult)
        assertFalse("Vote row must not be inserted when transaction fails", throwingTipVoteDao.hasUserVoted("tip_fake_throw_1", "fake_voter_1"))

        val finalTip = tipDao.getTipById("tip_fake_throw_1")
        assertEquals(10, finalTip?.upvotes)
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
            context = null,
            cloudService = FakeTipUpvoteCloud(returnResult = true)
        )

        // Attempt second vote using new repository instance
        val duplicateVoteOnNewInstance = newRepositoryInstance.upvoteTip("tip_persisted", userId)
        assertFalse("Second vote on new repository instance must be rejected by persisted vote table", duplicateVoteOnNewInstance)

        val finalTip = tipDao.getTipById("tip_persisted")
        assertEquals(1, finalTip?.upvotes)
    }

    // Fake TipUpvoteCloud implementation for unit testing
    class FakeTipUpvoteCloud(
        var returnResult: Boolean = true,
        var shouldThrow: Boolean = false
    ) : TipUpvoteCloud {
        var callCount = 0
        var lastTipIdCalled: String? = null

        override suspend fun incrementTipUpvote(tipId: String): Boolean {
            callCount++
            lastTipIdCalled = tipId
            if (shouldThrow) {
                throw IllegalStateException("Fake Cloud Exception during upvote transaction")
            }
            return returnResult
        }
    }
}

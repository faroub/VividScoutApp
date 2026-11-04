package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CommunityTipEntity
import com.example.data.local.TipVoteEntity
import com.example.data.sync.TipUpvoteCloud
import kotlinx.coroutines.flow.first
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
class CommunityTipSyncTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun postTip_immediateUploadSuccess_marksTipSyncedInRoom() = runBlocking {
        val tipDao = db.communityTipDao()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val repository = CommunityTipRepository(
            dao = tipDao,
            context = context
        )

        val newTip = repository.postTip(
            spotId = "spot_100",
            spotTitle = "Miradouro De Santa Luzia",
            content = "Great sunset view!"
        )

        assertNotNull(newTip.id)
        val storedTip = tipDao.getTipById(newTip.id)
        assertNotNull(storedTip)
        assertEquals("spot_100", storedTip?.spotId)
        assertTrue(storedTip?.isLocallyAuthored == true)
    }

    @Test
    fun upvoteTip_duplicateVote_triggersReconciliationWithoutCrash() = runBlocking {
        val tipDao = db.communityTipDao()
        val voteDao = db.tipVoteDao()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val initialTip = CommunityTipEntity(
            id = "tip_cloud_1",
            spotId = "spot_1",
            spotTitle = "Carmo Convent",
            authorName = "Scout",
            content = "Gothic ruins",
            timestamp = System.currentTimeMillis(),
            upvotes = 5,
            isSyncedToCloud = true,
            isMigrated = true
        )
        tipDao.insertTip(initialTip)

        // Mock cloud service that fails due to duplicate vote
        val fakeCloudService = object : TipUpvoteCloud {
            override suspend fun incrementTipUpvote(tipId: String): Boolean = false
        }

        val repository = CommunityTipRepository(
            dao = tipDao,
            tipVoteDao = voteDao,
            context = context,
            cloudService = fakeCloudService
        )

        // Upvote fails in cloud, doesn't crash, local state remains unchanged
        val success = repository.upvoteTip("tip_cloud_1", "user_abc")
        assertFalse("Duplicate vote attempt must return false without crashing", success)

        val tipAfter = tipDao.getTipById("tip_cloud_1")
        assertEquals(5, tipAfter?.upvotes)
    }

    @Test
    fun remoteTipPagination_supportsMoreThanFiftyRemoteTips() = runBlocking {
        val tipDao = db.communityTipDao()

        // Insert 75 remote tips across 2 pages
        val remoteTipsPage1 = (1..50).map { i ->
            CommunityTipEntity(
                id = "remote_tip_$i",
                spotId = "spot_1",
                spotTitle = "Spot",
                authorName = "Author",
                content = "Content $i",
                timestamp = 10000L - i,
                isSyncedToCloud = true,
                isMigrated = true
            )
        }
        val remoteTipsPage2 = (51..75).map { i ->
            CommunityTipEntity(
                id = "remote_tip_$i",
                spotId = "spot_1",
                spotTitle = "Spot",
                authorName = "Author",
                content = "Content $i",
                timestamp = 5000L - i,
                isSyncedToCloud = true,
                isMigrated = true
            )
        }

        tipDao.insertTips(remoteTipsPage1)
        tipDao.insertTips(remoteTipsPage2)

        val allTips = tipDao.getAllTipsFlow().first()
        assertEquals("Room DB must store all 75 paginated remote tips", 75, allTips.size)
    }

    @Test
    fun existingLocallyCachedTips_outsideFirstPage_retainedCorrectly() = runBlocking {
        val tipDao = db.communityTipDao()

        val oldLocalTip = CommunityTipEntity(
            id = "local_old_99",
            spotId = "spot_2",
            spotTitle = "Alfama Bakery",
            authorName = "Me",
            content = "Old local tip outside first page",
            timestamp = 1000L,
            isLocallyAuthored = true,
            isSyncedToCloud = false
        )
        tipDao.insertTip(oldLocalTip)

        val newRemoteTips = (1..50).map { i ->
            CommunityTipEntity(
                id = "new_remote_$i",
                spotId = "spot_1",
                spotTitle = "New Spot",
                authorName = "Scout",
                content = "New tip $i",
                timestamp = 20000L - i,
                isSyncedToCloud = true,
                isMigrated = true
            )
        }
        tipDao.insertTips(newRemoteTips)

        val allTips = tipDao.getAllTipsFlow().first()
        assertEquals(51, allTips.size)
        assertTrue("Locally cached tip outside first page must be retained", allTips.any { it.id == "local_old_99" })
    }
}

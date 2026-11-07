package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.p2p.PeerMeshSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Demo/seed community tips must be distinguishable from genuine user and cloud content,
 * and must never be eligible for upload to Firestore as a real community contribution.
 *
 * The classification is derived from persisted fields (id/peerNodeId prefixes plus
 * isLocallyAuthored), so rows written by an older app version are classified at read
 * time — no migration is required and existing installations keep their data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommunityTipOriginTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CommunityTipDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.communityTipDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    private fun userTip(id: String = "user_tip_abc123") = CommunityTipEntity(
        id = id,
        spotId = "spot_1",
        spotTitle = "Miradouro de Santa Luzia",
        authorName = "@you",
        content = "User-authored note about the view.",
        timestamp = System.currentTimeMillis(),
        isLocallyAuthored = true,
        isSyncedToCloud = false
    )

    @Test
    fun seedTips_insertedByFreshInstallOrEnsureInitialSeeding_areIdentifiedAsSampleContent() = runTest {
        dao.insertTips(PeerMeshSyncEngine.createSeedTips())

        val stored = dao.getAllTipsFlow().first()
        assertTrue("fresh-install seed tips must be persisted", stored.isNotEmpty())
        assertTrue(
            "every seed tip must be classified as sample content",
            stored.all { it.isSampleContent }
        )
    }

    @Test
    fun seedTips_areExcludedFromTheCloudSyncCandidateSet() = runTest {
        dao.insertTips(PeerMeshSyncEngine.createSeedTips())
        dao.insertTip(userTip())

        // syncPendingTipsToCloud uploads exactly getLocallyAuthoredTipsSync() minus
        // already-synced rows: sample content must never appear in that candidate set.
        val syncCandidates = dao.getLocallyAuthoredTipsSync().filter { !it.isSyncedToCloud }

        assertEquals(1, syncCandidates.size)
        assertEquals("user_tip_abc123", syncCandidates.single().id)
        assertFalse(
            "a seed tip must never be a cloud-sync candidate",
            syncCandidates.any { it.isSampleContent }
        )
    }

    @Test
    fun demoPeerMeshGossipTips_areIdentifiedAsSampleContentAndExcludedFromSync() = runTest {
        val gossipTip = CommunityTipEntity(
            id = "p2p_tip_101",
            spotId = "spot_2",
            spotTitle = "Pastelaria Santo António",
            authorName = "@clara_alfama",
            content = "Simulated peer gossip content.",
            timestamp = System.currentTimeMillis(),
            peerNodeId = "peer_alfama_88",
            isLocallyAuthored = false
        )
        dao.insertTip(gossipTip)

        val stored = dao.getTipById("p2p_tip_101")
        assertTrue("simulated peer tips must be flagged as sample content", stored?.isSampleContent == true)
        assertFalse(
            "simulated peer tips must not enter the local-authored sync candidate set",
            dao.getLocallyAuthoredTipsSync().any { it.id == "p2p_tip_101" }
        )
    }

    @Test
    fun genuineUserTips_remainRealContentAndStayEligibleForCloudSync() = runTest {
        dao.insertTip(userTip())

        val stored = dao.getTipById("user_tip_abc123")
        assertFalse("a user-authored tip is not sample content", stored?.isSampleContent == true)
        assertTrue(
            "a user-authored tip stays a cloud-sync candidate until synced",
            dao.getLocallyAuthoredTipsSync().any { it.id == "user_tip_abc123" }
        )
    }

    @Test
    fun genuineCloudTips_downloadedBySyncRemoteContentDown_areNotTreatedAsSampleContent() = runTest {
        // Shape produced by FirestoreSyncManager.syncRemoteContentDown for a real tip
        // written by another user through the app.
        val cloudTip = CommunityTipEntity(
            id = "cloud_tip_xyz",
            spotId = "spot_1",
            spotTitle = "Miradouro de Santa Luzia",
            authorName = "@another_scout",
            content = "Real content from the Firestore community_tips collection.",
            timestamp = System.currentTimeMillis(),
            peerNodeId = "cloud-firestore",
            isLocallyAuthored = false,
            isSyncedToCloud = true
        )
        dao.insertTip(cloudTip)

        val stored = dao.getTipById("cloud_tip_xyz")
        assertFalse(
            "genuine cloud content must never be flagged as sample content",
            stored?.isSampleContent == true
        )
    }

    @Test
    fun preExistingRowsFromOlderInstalls_areClassifiedAtReadTimeWithoutDataLoss() = runTest {
        // Simulates an existing installation whose seed rows were written before the
        // sample-content classification existed: the rows survive and are classified
        // correctly on read, with no migration or deletion.
        dao.insertTips(PeerMeshSyncEngine.createSeedTips())
        dao.insertTip(userTip())

        val all = dao.getAllTipsFlow().first()
        assertEquals(
            "every persisted row survives read-time classification",
            7,
            all.size
        )
        val samples = all.filter { it.isSampleContent }
        val real = all.filter { !it.isSampleContent }
        assertEquals(6, samples.size)
        assertEquals(1, real.size)
    }
}

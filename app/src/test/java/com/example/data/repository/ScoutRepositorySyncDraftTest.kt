package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.DiscoveredGemEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the offline-draft synchronization lifecycle in
 * ScoutRepository.syncDraft().
 *
 * The bug: syncDraft treated an upload result of false (auth failure, Firestore
 * failure, lost private note) as a SUCCESSFUL offline sync — flipping the draft to
 * isOfflineDraft = false with a "Synced Offline" badge and marking its discovered gem
 * synced. The draft then vanished from the retry queue while the UI showed it as
 * SYNCED (OfflineSyncState) even though nothing had left the device.
 *
 * The correct contract: FirestoreSyncManager.syncDraftSpotToCloud returns true only
 * after BOTH the public submission and the private note reached Firestore, and marks
 * the local state itself. A false outcome — or a missing application context — must
 * leave every local row untouched: the draft stays queued for retry and no "synced"
 * status is displayed. Retries must be safe: repeated attempts never duplicate rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScoutRepositorySyncDraftTest {

    private lateinit var db: AppDatabase
    private lateinit var scoutDao: ScoutDao
    private lateinit var discoveredGemDao: DiscoveredGemDao
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scoutDao = db.scoutDao()
        discoveredGemDao = db.discoveredGemDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedDraft(
        id: String = "draft_1",
        title: String = "Hidden Alfama Courtyard"
    ): ScoutSpotEntity = ScoutSpotEntity(
        id = id,
        title = title,
        description = "Community discovered secret spot with offline GPS coordinates tagged.",
        category = "sights",
        distance = "10m radius",
        walkMinutes = 1,
        imageUrl = "ic_cat_sights",
        badgeLabel = "COMMUNITY GEM",
        statusBadge = "Saved Offline",
        tagsRaw = "Offline Queued|||GPS Cached: ±3m|||Photo Stored Locally",
        proTip = "Saved in local Room database. Auto-sync will transmit when reconnected.",
        contributor = "@you (Offline)",
        isSaved = false,
        isOfflineDraft = true,
        isDiscovered = true,
        discoveredTimestamp = 1_700_000_000_000L,
        userNotes = "Private note that must never be lost",
        offlineCached = true,
        locationName = "Lisbon",
        latitude = 38.7112,
        longitude = -9.1423,
        timestamp = 1_700_000_000_000L
    )

    private suspend fun seedGem(spot: ScoutSpotEntity) {
        discoveredGemDao.insertDiscoveredGem(
            DiscoveredGemEntity(
                id = spot.id,
                title = spot.title,
                description = spot.description,
                category = spot.category,
                locationName = spot.locationName,
                latitude = spot.latitude,
                longitude = spot.longitude,
                notes = "Private note that must never be lost",
                discoveredAt = 1_700_000_000_000L,
                isSynced = false
            )
        )
    }

    private suspend fun assertDraftUntouched(spot: ScoutSpotEntity) {
        val fromDb = scoutDao.getSpotById(spot.id)
        assertEquals("The draft must stay in the scout_spots table", spot.id, fromDb?.id)
        assertTrue(
            "A failed sync must keep isOfflineDraft = true so the draft stays retryable",
            fromDb?.isOfflineDraft == true
        )
        assertEquals(
            "A failed sync must not rewrite the status badge to anything 'synced'",
            "Saved Offline",
            fromDb?.statusBadge
        )
        val gem = discoveredGemDao.getGemById(spot.id)
        assertFalse(
            "A failed sync must not mark the discovered gem as synced",
            gem?.isSynced == true
        )
        val userNotes = scoutDao.getSpotById(spot.id)?.userNotes
        assertEquals(
            "A failed sync must not drop or clear the private note",
            "Private note that must never be lost",
            userNotes
        )
        assertTrue(
            "A failed sync must keep the draft visible in the offline-drafts queue",
            scoutDao.getOfflineDrafts().first().any { it.id == spot.id }
        )
    }

    /**
     * Test repository whose cloud upload outcome is scripted. On a true outcome the
     * fake reproduces the documented FirestoreSyncManager contract: it has already
     * marked the spot "Synced to Cloud" (isOfflineDraft = false) and the gem synced
     * locally. On false it changes nothing.
     */
    private class scriptedCloudRepository(
        private val dao: ScoutDao,
        private val gems: DiscoveredGemDao,
        context: Context,
        private val outcomes: List<Boolean>,
        private val noteFailureForSuccessIds: Set<String> = emptySet()
    ) : ScoutRepository(dao, gems, null, context) {

        val uploadAttempts = mutableListOf<String>()
        var scriptedSuccessCount = 0

        override suspend fun uploadDraftToCloud(
            context: Context,
            spot: ScoutSpotEntity
        ): Boolean {
            uploadAttempts.add(spot.id)
            val outcome = outcomes.getOrNull(uploadAttempts.size - 1) ?: outcomes.last()
            if (!outcome) return false
            if (spot.id in noteFailureForSuccessIds) {
                // Public submission landed, the private note did not: the real manager
                // returns false here BEFORE touching any local success state.
                return false
            }
            scriptedSuccessCount++
            dao.updateSpot(spot.copy(isOfflineDraft = false, statusBadge = "Synced to Cloud"))
            gems.markAsSynced(spot.id)
            return true
        }
    }

    @Test
    fun successfulUpload_marksSpotAndGemSyncedOnlyThroughTheManagerTransition() = runBlocking {
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        val repository = scriptedCloudRepository(scoutDao, discoveredGemDao, context, listOf(true))
        val result = repository.syncDraft(draft)

        assertTrue("syncDraft must report the cloud upload outcome", result)
        assertEquals("The scripted cloud upload must be exercised exactly once", 1, repository.uploadAttempts.size)

        val synced = scoutDao.getSpotById(draft.id)
        assertEquals(
            "A successful upload transitions the badge through the manager's 'Synced to Cloud', " +
                "not through a repository-owned 'Synced Offline' rewrite",
            "Synced to Cloud",
            synced?.statusBadge
        )
        assertFalse("A successful upload clears the offline-draft flag", synced?.isOfflineDraft == true)
        assertTrue(
            "A successful upload marks the discovered gem synced by the same operation",
            discoveredGemDao.getGemById(draft.id)?.isSynced == true
        )
        assertTrue(
            "A successfully synced draft leaves the retry queue",
            scoutDao.getOfflineDrafts().first().none { it.id == draft.id }
        )
    }

    @Test
    fun authenticationFailure_keepsDraftQueuedAndUnsynced() = runBlocking {
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        val repository = scriptedCloudRepository(scoutDao, discoveredGemDao, context, listOf(false))
        val result = repository.syncDraft(draft)

        assertFalse("An authentication failure must not be reported as a sync", result)
        assertDraftUntouched(draft)
    }

    @Test
    fun firestoreFailure_keepsDraftQueuedAndUnsynced() = runBlocking {
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        val repository = scriptedCloudRepository(scoutDao, discoveredGemDao, context, listOf(false))
        val result = repository.syncDraft(draft)

        assertFalse("A Firestore failure must not be reported as a sync", result)
        assertDraftUntouched(draft)
    }

    @Test
    fun privateNoteFailure_keepsDraftQueuedAndUnsynced() = runBlocking {
        // The public submission reached Firestore but the private note did not: the
        // manager returns false for the whole draft, and the spot must not be reported
        // as fully synced while its note is still missing from the cloud.
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        val repository = scriptedCloudRepository(
            scoutDao, discoveredGemDao, context,
            outcomes = listOf(true),
            noteFailureForSuccessIds = setOf(draft.id)
        )
        val result = repository.syncDraft(draft)

        assertFalse("A draft whose private note failed to upload is not fully synced", result)
        assertEquals("No local success state may be written for a partial upload", 0, repository.scriptedSuccessCount)
        assertDraftUntouched(draft)
    }

    @Test
    fun missingApplicationContext_keepsDraftQueuedAndUnsynced() = runBlocking {
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        // No application context, so no cloud operation can even be attempted.
        val repository = ScoutRepository(scoutDao, discoveredGemDao, null, null)
        val result = repository.syncDraft(draft)

        assertFalse("A missing application context must not be reported as a sync", result)
        assertDraftUntouched(draft)
    }

    @Test
    fun retryAfterPreviousFailure_keepsDraftQueuedWithoutDuplicates() = runBlocking {
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        val repository = scriptedCloudRepository(
            scoutDao, discoveredGemDao, context, listOf(false, false, false)
        )

        repeat(3) { attempt ->
            val result = repository.syncDraft(draft)
            assertFalse("Retry attempt ${attempt + 1} must surface the failed upload outcome", result)
            assertDraftUntouched(draft)
        }

        assertEquals("Every retry must be forwarded to the cloud uploader", 3, repository.uploadAttempts.size)
        assertEquals(
            "Retries must never duplicate the local row (requeue-safe on failure)",
            1,
            scoutDao.getAllSpotsSync().count { it.id == draft.id }
        )
        assertEquals(
            "Retries must never duplicate the discovered gem",
            1,
            discoveredGemDao.getAllDiscoveredGems().first().count { it.id == draft.id }
        )
    }

    @Test
    fun successfulRetryAfterFailure_updatesLocalStateCorrectly() = runBlocking {
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        val repository = scriptedCloudRepository(
            scoutDao, discoveredGemDao, context, listOf(false, true)
        )

        // First attempt fails (e.g. offline): everything stays queued.
        val firstResult = repository.syncDraft(draft)
        assertFalse(firstResult)
        assertDraftUntouched(draft)

        // Retry succeeds: the manager's transition flips the state exactly once.
        val secondResult = repository.syncDraft(draft)
        assertTrue("A successful retry must be reported as a sync", secondResult)
        assertEquals(1, repository.scriptedSuccessCount)

        val synced = scoutDao.getSpotById(draft.id)
        assertEquals("Synced to Cloud", synced?.statusBadge)
        assertFalse("The successful retry clears the offline-draft flag", synced?.isOfflineDraft == true)
        assertTrue("The successful retry marks the gem synced", discoveredGemDao.getGemById(draft.id)?.isSynced == true)
        assertTrue(
            "Only still-unsynced drafts remain in the retry queue",
            scoutDao.getOfflineDrafts().first().none { it.id == draft.id }
        )
        assertEquals(
            "The successful retry updates the existing row instead of adding a duplicate",
            1,
            scoutDao.getAllSpotsSync().count { it.id == draft.id }
        )
    }

    @Test
    fun syncAllDraftsToCloud_keepsFailedRecordsInTheRetryQueue() = runBlocking {
        val failingDraft = seedDraft(id = "draft_failing", title = "Unsyncable Courtyard")
        val succeedingDraft = seedDraft(id = "draft_ok", title = "Syncable Terrace")
        scoutDao.insertSpots(listOf(failingDraft, succeedingDraft))
        seedGem(failingDraft)
        seedGem(succeedingDraft)

        val repository = scriptedCloudRepository(
            scoutDao, discoveredGemDao, context,
            // First sweep: both uploads fail. Second sweep: both succeed.
            outcomes = listOf(false, false, true, true)
        )

        val firstSweep = repository.syncAllDraftsToCloud()
        assertEquals("A fully failed sweep must count zero synced drafts", 0, firstSweep)
        assertDraftUntouched(failingDraft)
        assertDraftUntouched(succeedingDraft)

        // The retry queue must still hold both failed drafts for the next sweep.
        val queuedAfterFailure = scoutDao.getOfflineDrafts().first().map { it.id }.sorted()
        assertEquals(listOf("draft_failing", "draft_ok"), queuedAfterFailure)

        val secondSweep = repository.syncAllDraftsToCloud()
        assertEquals("A fully successful sweep must count both drafts", 2, secondSweep)
        assertTrue(
            "After the successful retry the queue is drained",
            scoutDao.getOfflineDrafts().first().isEmpty()
        )
        assertTrue(discoveredGemDao.getGemById("draft_ok")?.isSynced == true)
        assertTrue(discoveredGemDao.getGemById("draft_failing")?.isSynced == true)
    }

    @Test
    fun realFirestoreSyncManager_withoutFirebaseConfig_returnsFalseAndKeepsDraftQueued() = runBlocking {
        // End-to-end exercise of the production delegation path (no seam substitution):
        // in the JVM test environment Firebase is not configured, so
        // FirestoreSyncManager.syncDraftSpotToCloud cannot authenticate and must
        // return false. The repository must propagate that instead of the old
        // "Synced Offline" success rewrite.
        val draft = seedDraft()
        scoutDao.insertSpot(draft)
        seedGem(draft)

        val repository = ScoutRepository(scoutDao, discoveredGemDao, null, context)
        val result = repository.syncDraft(draft)

        assertFalse(
            "The unauthenticated production path must surface a failed upload, never a false success",
            result
        )
        assertDraftUntouched(draft)
    }
}

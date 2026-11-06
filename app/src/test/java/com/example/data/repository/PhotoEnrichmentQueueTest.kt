package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.util.LocationPhotoResolver
import com.example.util.PhotoResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exercises the production photo-enrichment worker in ScoutGemRepository against
 * an in-memory Room database. The queue must drain every eligible candidate in
 * bounded batches (no 300-item cap), prioritize saved places, back off failed
 * lookups, recover after network returns, survive repository recreation, clean
 * up in-progress state on cancellation, and never resolve the same spot twice.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhotoEnrichmentQueueTest {

    private lateinit var db: AppDatabase
    private lateinit var scoutDao: ScoutDao

    private class FakePhotoResolver(
        var failingTitles: Set<String> = emptySet(),
        val blankUrlTitles: Set<String> = emptySet()
    ) : PhotoResolver {
        val invocations = mutableListOf<String>()
        var networkDown = false

        // Delegate to the real production predicate — no duplicated validation logic.
        override fun isGenericOrStockPhoto(url: String?): Boolean =
            LocationPhotoResolver.isGenericOrStockPhoto(url)

        override suspend fun resolveEnrichedPhoto(
            title: String,
            category: String?,
            districtOrLocation: String?,
            osmTags: Map<String, String>?,
            latitude: Double?,
            longitude: Double?,
            spotId: String?
        ): LocationPhotoResolver.EnrichedPhoto {
            invocations.add(title)
            when {
                networkDown -> throw IllegalStateException("network unavailable")
                title in failingTitles -> throw IllegalStateException("provider error for $title")
                title in blankUrlTitles ->
                    return LocationPhotoResolver.EnrichedPhoto(url = "", attribution = "", isVerifiedLandmark = false)
                else -> return LocationPhotoResolver.EnrichedPhoto(
                    url = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/$title.jpg",
                    attribution = "Wikimedia Commons",
                    isVerifiedLandmark = true
                )
            }
        }
    }

    /** Resolver that suspends until released, to hold the worker mid-batch deterministically. */
    private class GatedPhotoResolver : PhotoResolver {
        val entered = AtomicBoolean(false)
        val gate = CompletableDeferred<Unit>()
        var completedResolutions = 0

        override fun isGenericOrStockPhoto(url: String?): Boolean =
            LocationPhotoResolver.isGenericOrStockPhoto(url)

        override suspend fun resolveEnrichedPhoto(
            title: String,
            category: String?,
            districtOrLocation: String?,
            osmTags: Map<String, String>?,
            latitude: Double?,
            longitude: Double?,
            spotId: String?
        ): LocationPhotoResolver.EnrichedPhoto {
            entered.set(true)
            gate.await()
            completedResolutions++
            return LocationPhotoResolver.EnrichedPhoto(
                url = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/$title.jpg",
                attribution = "Wikimedia Commons",
                isVerifiedLandmark = true
            )
        }
    }

    private fun makeSpot(id: String, saved: Boolean = false, timestamp: Long = 0L): ScoutSpotEntity =
        ScoutSpotEntity(
            id = id,
            title = id,
            description = "Description for $id",
            category = "sights",
            distance = "Nearby",
            walkMinutes = 5,
            imageUrl = "", // generic placeholder → eligible for enrichment
            badgeLabel = "Spot",
            statusBadge = "Scout Spot",
            tagsRaw = "sights|||test",
            isSaved = saved,
            timestamp = timestamp
        )

    private fun makeRepo(resolver: FakePhotoResolver): ScoutGemRepository = ScoutGemRepository(
        scoutDao = scoutDao,
        photoResolver = resolver
    ).apply {
        // Deterministic, fast pacing for mass tests; backoff timing is driven by
        // the clock seam where needed.
        enrichmentPacingMs = { 0L }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scoutDao = db.scoutDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun enrichedCount(): Int = scoutDao.getAllSpotsList().count {
        !LocationPhotoResolver.isGenericOrStockPhoto(it.imageUrl)
    }

    @Test
    fun fiveHundredEligiblePlaces_allReachTerminalCompletedState() = runTest {
        val spots = (1..500).map { makeSpot("spot_$it") }
        scoutDao.insertSpots(spots)
        val resolver = FakePhotoResolver()
        val repo = makeRepo(resolver)

        repo.enrichSpotsPhotosInBackground(spots)

        assertEquals("All 500 spots must be enriched", 500, enrichedCount())
        assertEquals("Every spot must be resolved exactly once", 500, resolver.invocations.size)
        assertEquals("Every spot must be recorded completed", 500, repo.completedPhotoIds.size)
        assertTrue("No leftover in-progress markers", repo.inProgressPhotoIds.isEmpty())
    }

    @Test
    fun oneThousandEligiblePlaces_allReachTerminalCompletedState() = runTest {
        val spots = (1..1000).map { makeSpot("spot_$it") }
        scoutDao.insertSpots(spots)
        val resolver = FakePhotoResolver()
        val repo = makeRepo(resolver)

        repo.enrichSpotsPhotosInBackground(spots)

        assertEquals(1000, enrichedCount())
        assertEquals(1000, resolver.invocations.size)
        assertEquals(1000, repo.completedPhotoIds.size)
    }

    @Test
    fun partialBatchFailure_preservesSuccessfulResults() = runTest {
        val spots = listOf(
            makeSpot("ok_1"),
            makeSpot("ok_2"),
            makeSpot("broken_1")
        )
        scoutDao.insertSpots(spots)
        val resolver = FakePhotoResolver(failingTitles = setOf("broken_1"))
        val repo = makeRepo(resolver)

        repo.enrichSpotsPhotosInBackground(spots)

        assertEquals("The two healthy spots must still be enriched", 2, enrichedCount())
        val broken = scoutDao.getSpotById("broken_1")!!
        assertTrue("Failed spot keeps its generic URL", LocationPhotoResolver.isGenericOrStockPhoto(broken.imageUrl))
        assertEquals(
            "Failed spot must be recorded as retryable, not completed",
            1,
            repo.failedPhotoAttempts["broken_1"]?.failedCount
        )
        assertFalse(repo.completedPhotoIds.contains("broken_1"))
    }

    @Test
    fun networkRecovery_failedJobsAreRetriedSuccessfully() = runTest {
        val spots = (1..3).map { makeSpot("spot_$it") }
        scoutDao.insertSpots(spots)
        val resolver = FakePhotoResolver().apply { networkDown = true }
        val repo = makeRepo(resolver)

        repo.enrichSpotsPhotosInBackground(spots)
        assertEquals(0, enrichedCount())
        assertEquals(3, repo.failedPhotoAttempts.size)

        // Connectivity returns: the re-sync clears failure state and re-queues everything.
        resolver.networkDown = false
        repo.triggerPhotoMetadataReSync()

        assertEquals("All spots must recover after the network returns", 3, enrichedCount())
        assertTrue(repo.failedPhotoAttempts.isEmpty())
    }

    @Test
    fun savedPlacesAreEnrichedBeforeFeedSpots() = runTest {
        val saved = (1..40).map { makeSpot("saved_$it", saved = true, timestamp = 100L) }
        val feed = (1..40).map { makeSpot("feed_$it", saved = false, timestamp = 999L) }
        scoutDao.insertSpots(saved + feed)
        val resolver = FakePhotoResolver()
        val repo = makeRepo(resolver)

        repo.enrichSpotsPhotosInBackground(saved + feed)

        val firstBatch = resolver.invocations.take(ScoutGemRepository.ENRICHMENT_BATCH_SIZE)
        assertTrue(
            "The first batch must consist entirely of saved places",
            firstBatch.all { it.startsWith("saved_") }
        )
        assertEquals(80, resolver.invocations.size)
    }

    @Test
    fun concurrentInvocations_mergeIntoASingleDrainWithoutDuplicates() = runTest {
        val spots = (1..3).map { makeSpot("spot_$it") }
        scoutDao.insertSpots(spots)
        val resolver = GatedPhotoResolver()
        val repo = ScoutGemRepository(scoutDao = scoutDao, photoResolver = resolver).apply {
            enrichmentPacingMs = { 0L }
        }

        val scope = CoroutineScope(Dispatchers.IO)
        val job1 = scope.launch { repo.enrichSpotsPhotosInBackground(spots) }

        // Hold job1 mid-resolution of the first spot, then start a second drain.
        val deadline = System.currentTimeMillis() + 5000
        while (!resolver.entered.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("The worker must be inside the resolver", resolver.entered.get())

        val job2 = scope.launch { repo.enrichSpotsPhotosInBackground(spots) }
        job2.join() // Must return immediately: merged into the running drain.

        resolver.gate.complete(Unit)
        job1.join()
        scope.cancel()

        assertEquals(
            "Every spot must be resolved exactly once despite two concurrent drains",
            3,
            resolver.completedResolutions
        )
        assertEquals(3, enrichedCount())
    }

    @Test
    fun coroutineCancellation_releasesInProgressState() = runTest {
        val spot = makeSpot("spot_1")
        scoutDao.insertSpots(listOf(spot))
        val resolver = GatedPhotoResolver()
        val repo = ScoutGemRepository(scoutDao = scoutDao, photoResolver = resolver)

        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch { repo.enrichSpotsPhotosInBackground(listOf(spot)) }

        val deadline = System.currentTimeMillis() + 5000
        while (!resolver.entered.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(resolver.entered.get())

        job.cancelAndJoin()

        assertTrue(
            "Cancellation must clear the in-progress marker",
            repo.inProgressPhotoIds.isEmpty()
        )

        // The mutex must also be released: a subsequent drain proceeds and succeeds.
        resolver.gate.complete(Unit)
        repo.enrichSpotsPhotosInBackground(listOf(spot))
        assertEquals(1, enrichedCount())
        scope.cancel()
    }

    @Test
    fun duplicateScheduling_neverResolvesASpotTwice() = runTest {
        val spots = (1..3).map { makeSpot("spot_$it") }
        scoutDao.insertSpots(spots)
        val resolver = FakePhotoResolver()
        val repo = makeRepo(resolver)

        repo.enrichSpotsPhotosInBackground(spots)
        repo.enrichSpotsPhotosInBackground(spots) // Second scheduling of the same set.

        assertEquals(3, resolver.invocations.size)
        assertEquals(3, enrichedCount())
    }

    @Test
    fun appRestart_queueReDerivesFromRoom_andReprocessesUnfinishedWork() = runTest {
        val spots = listOf(makeSpot("ok_1"), makeSpot("ok_2"), makeSpot("failed_1"))
        scoutDao.insertSpots(spots)

        val resolver1 = FakePhotoResolver(blankUrlTitles = setOf("failed_1"))
        val repo1 = makeRepo(resolver1)
        repo1.enrichSpotsPhotosInBackground(spots)
        assertEquals(2, enrichedCount())

        // Simulate process death: a fresh repository shares only the Room database.
        val resolver2 = FakePhotoResolver()
        val repo2 = makeRepo(resolver2)
        repo2.enrichSpotsPhotosInBackground()

        assertEquals(
            "On restart the queue must re-derive from Room: completed stays completed, unfinished resumes",
            3,
            enrichedCount()
        )
        assertEquals("Only the previously unfinished spot may be re-queried", 1, resolver2.invocations.size)
        assertEquals("failed_1", resolver2.invocations.first())
    }

    @Test
    fun retryExhaustion_stopsRetryingUntilConnectivityReSync() = runTest {
        val spot = makeSpot("flaky_1")
        scoutDao.insertSpots(listOf(spot))
        val resolver = FakePhotoResolver(failingTitles = setOf("flaky_1"))
        val repo = makeRepo(resolver)

        // Deterministic clock so exponential backoff elapses instantly.
        var clock = 1_000_000L
        repo.currentTimeMs = { clock }

        val maxAttempts = ScoutGemRepository.MAX_PHOTO_ATTEMPTS
        var totalInvocations = 0
        for (attempt in 1..maxAttempts) {
            repo.enrichSpotsPhotosInBackground(listOf(spot))
            totalInvocations++
            assertEquals(
                "Attempt $attempt must be recorded",
                attempt,
                repo.failedPhotoAttempts[spot.id]?.failedCount
            )
            // Advance past the current backoff window (1 s * 2^failures).
            clock += 1000L * (1 shl attempt) + 1000L
        }

        // Retries exhausted: further drains must not invoke the resolver at all.
        repo.enrichSpotsPhotosInBackground(listOf(spot))
        repo.enrichSpotsPhotosInBackground(listOf(spot))
        assertEquals(totalInvocations, resolver.invocations.size)
        assertTrue(scoutDao.getSpotById(spot.id)!!.imageUrl.isEmpty())

        // A connectivity recovery re-arms the exhausted spot.
        resolver.failingTitles = emptySet()
        repo.triggerPhotoMetadataReSync()
        assertEquals("Re-sync must re-queue the exhausted spot", totalInvocations + 1, resolver.invocations.size)
        assertEquals(1, enrichedCount())
    }

    @Test
    fun emptyRepository_drainIsANoOp() = runTest {
        val resolver = FakePhotoResolver()
        val repo = makeRepo(resolver)
        repo.enrichSpotsPhotosInBackground()
        assertTrue(resolver.invocations.isEmpty())
        assertTrue(repo.inProgressPhotoIds.isEmpty())
    }
}

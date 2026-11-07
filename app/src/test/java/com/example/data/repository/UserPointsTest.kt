package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.UserPointsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Scout Points are backed by the persisted user_points ledger and derived from it
 * (never hardcoded): photo-verified spot submissions award the advertised +15 bonus,
 * the total is reactive, survives restarts, stays correct offline, awards are never
 * duplicated by retries or cloud sync, and users with no activity read zero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserPointsTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ScoutRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScoutRepository(
            db.scoutDao(),
            db.discoveredGemDao(),
            null,
            null,
            db.userPointsDao()
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun userWithoutAnyActivity_readsZeroPoints() = runTest {
        assertEquals(0, repository.userPoints.first())
    }

    @Test
    fun photoVerifiedSubmission_awardsTheAdvertisedBonus() = runTest {
        repository.submitSpotDraft(
            name = "Azulejo Courtyard",
            category = "sights",
            photoUri = "content://photo/1.jpg",
            notes = "Tiled courtyard",
            latitude = 38.7112,
            longitude = -9.1423
        )

        assertEquals(
            "a photo-verified submission must award the advertised bonus",
            ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT,
            repository.userPoints.first()
        )
    }

    @Test
    fun submissionWithoutAPhoto_awardsNoPoints() = runTest {
        repository.submitSpotDraft(
            name = "Quiet Stairwell",
            category = "shortcut",
            photoUri = null,
            notes = "No photo attached",
            latitude = 38.7112,
            longitude = -9.1423
        )

        assertEquals(0, repository.userPoints.first())
    }

    @Test
    fun pointsSurviveRestart() = runTest {
        // A file-backed database models a real installation: closing and reopening it
        // is the process-restart boundary the Room ledger must survive.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("user_points_restart_test.db")
        dbFile?.parentFile?.mkdirs()
        val fileDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "user_points_restart_test.db"
        ).build()
        val fileRepository = ScoutRepository(
            fileDb.scoutDao(), fileDb.discoveredGemDao(), null, null, fileDb.userPointsDao()
        )
        fileRepository.submitSpotDraft(
            name = "Azulejo Courtyard",
            category = "sights",
            photoUri = "content://photo/1.jpg",
            notes = "Tiled courtyard",
            latitude = 38.7112,
            longitude = -9.1423
        )
        fileDb.close()

        val reopenedDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "user_points_restart_test.db"
        ).build()
        val reopenedRepository = ScoutRepository(
            reopenedDb.scoutDao(), reopenedDb.discoveredGemDao(), null, null, reopenedDb.userPointsDao()
        )

        assertEquals(
            "the awarded points must survive a process restart",
            ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT,
            reopenedRepository.userPoints.first()
        )
        reopenedDb.close()
        context.deleteDatabase("user_points_restart_test.db")
    }

    @Test
    fun duplicateAwardForTheSameActivity_neverAwardsTwice() = runTest {
        val award = UserPointsEntity(
            awardId = "photo_verified_draft_retry",
            points = ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT,
            reason = "photo_verified_spot",
            referenceId = "draft_retry",
            timestamp = System.currentTimeMillis()
        )
        db.userPointsDao().awardPoints(award)
        // A retry, replay, or resync of the same activity references the same awardId.
        db.userPointsDao().awardPoints(award)

        assertEquals(
            "a retried activity must not double-award",
            ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT,
            repository.userPoints.first()
        )
    }

    @Test
    fun multipleSubmissions_accumulatePoints() = runTest {
        repeat(3) { i ->
            repository.submitSpotDraft(
                name = "Spot $i",
                category = "sights",
                photoUri = "content://photo/$i.jpg",
                notes = "Photo verified",
                latitude = 38.7112,
                longitude = -9.1423
            )
        }

        assertEquals(
            3 * ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT,
            repository.userPoints.first()
        )
    }

    @Test
    fun cloudSyncOfDrafts_doesNotConsumeOrRepeatAwards() = runTest {
        repository.submitSpotDraft(
            name = "Azulejo Courtyard",
            category = "sights",
            photoUri = "content://photo/1.jpg",
            notes = "Tiled courtyard",
            latitude = 38.7112,
            longitude = -9.1423
        )

        // Without an application context no cloud sync is attempted (0 synced); the
        // operation must leave the local points total untouched either way. Points are
        // a local ledger: cloud synchronization of drafts neither awards nor retracts.
        val synced = repository.syncAllDraftsToCloud()
        assertEquals(0, synced)
        assertEquals(
            "syncing drafts must not change the points total",
            ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT,
            repository.userPoints.first()
        )
    }

    @Test
    fun userPointsFlow_isReactiveToNewAwards() = runTest {
        assertEquals(0, repository.userPoints.first())

        repository.submitSpotDraft(
            name = "Azulejo Courtyard",
            category = "sights",
            photoUri = "content://photo/1.jpg",
            notes = "Tiled courtyard",
            latitude = 38.7112,
            longitude = -9.1423
        )
        assertEquals(ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT, repository.userPoints.first())

        repository.submitSpotDraft(
            name = "Second Spot",
            category = "eats",
            photoUri = "content://photo/2.jpg",
            notes = "Photo verified",
            latitude = 38.7112,
            longitude = -9.1423
        )
        assertEquals(2 * ScoutRepository.POINTS_PER_PHOTO_VERIFIED_SPOT, repository.userPoints.first())
    }
}

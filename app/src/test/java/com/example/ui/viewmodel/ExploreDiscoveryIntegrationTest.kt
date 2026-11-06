package com.example.ui.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.places.CachedPlaceDao
import com.example.data.places.PlaceCandidate
import com.example.data.places.PlaceRepository
import com.example.data.places.PlaceSource
import com.example.data.repository.ScoutGemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExploreDiscoveryIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var scoutDao: ScoutDao
    private lateinit var cachedPlaceDao: CachedPlaceDao
    private lateinit var placeRepository: PlaceRepository
    private lateinit var gemRepository: ScoutGemRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        scoutDao = db.scoutDao()
        cachedPlaceDao = db.cachedPlaceDao()

        val candidate = PlaceCandidate(
            sourceName = "OpenTripMap API",
            sourceId = "otm_W999",
            name = "Praca do Comercio Arch",
            latitude = 38.7084,
            longitude = -9.1367,
            category = "history",
            description = "Triumphal arch in Lisbon."
        )

        val fakeSource = object : PlaceSource {
            override val sourceId: String = "open_trip_map"
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.success(listOf(candidate))
        }

        placeRepository = PlaceRepository(cachedPlaceDao, listOf(fakeSource))
        gemRepository = ScoutGemRepository(
            scoutDao = scoutDao,
            discoveredGemDao = db.discoveredGemDao(),
            recentSearchDao = db.recentSearchDao(),
            placeRepository = placeRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun multiSourceDiscovery_flowsEndToEndFromSourceToRoomDatabase() = runTest {
        val userLat = 38.7112
        val userLng = -9.1423

        // 1. Execute multi-source discovery sync
        val syncResult = gemRepository.syncMultiSourceGems(userLat, userLng, sourceIds = listOf("open_trip_map"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Multi-source sync must succeed", syncResult.isSuccess)
        assertEquals(1, syncResult.getOrThrow())

        // 2. Verify place entity reaches Room database (ScoutDao)
        val roomSpots = scoutDao.getAllSpotsList()
        assertEquals(1, roomSpots.size)

        val spot = roomSpots.first()
        assertEquals("Praca do Comercio Arch", spot.title)
        assertEquals("OpenTripMap API", spot.badgeLabel)
        assertEquals(38.7084, spot.latitude, 0.0001)
        assertEquals(-9.1367, spot.longitude, 0.0001)

        // 3. Verify reactive Room Flow emits place to ViewModel / UI
        val observedGems = gemRepository.observeGems(userLat, userLng).first()
        assertEquals(1, observedGems.size)
        assertEquals("Praca do Comercio Arch", observedGems.first().title)
    }

    @Test
    fun multiSourceDiscovery_preservesExistingSavedSpotState() = runTest {
        val userLat = 38.7112
        val userLng = -9.1423

        // Pre-insert saved spot in Room
        val savedSpot = ScoutSpotEntity(
            id = "place_-1421453472", // matching hash code
            title = "Praca do Comercio Arch",
            description = "Bookmarked by user",
            category = "history",
            distance = "Nearby",
            walkMinutes = 5,
            imageUrl = "",
            badgeLabel = "OpenTripMap",
            statusBadge = "Aggregated Place",
            tagsRaw = "",
            isSaved = true, // Bookmarked!
            latitude = 38.7084,
            longitude = -9.1423
        )
        scoutDao.insertSpots(listOf(savedSpot))

        // Execute discovery
        gemRepository.syncMultiSourceGems(userLat, userLng, sourceIds = listOf("open_trip_map"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify bookmark is strictly preserved
        val updatedSpot = scoutDao.getSpotById("place_-1421453472")
        assertNotNull(updatedSpot)
        assertTrue("Bookmarked status must be strictly preserved during discovery sync", updatedSpot!!.isSaved)
    }
}

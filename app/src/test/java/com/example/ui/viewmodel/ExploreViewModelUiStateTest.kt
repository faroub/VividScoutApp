package com.example.ui.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AchievementDao
import com.example.data.local.AppDatabase
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.RecentSearchDao
import com.example.data.local.RecentSearchEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.repository.ScoutGemRepository
import com.example.data.repository.ScoutRepository
import com.example.engine.PreScoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

/**
 * UI-state coverage for ExploreViewModel: loading, success, empty, error and
 * filter-change transitions. Uses the established Robolectric + in-memory Room +
 * StandardTestDispatcher setup of ExploreSearchViewModelTest. The discovery result is
 * controlled through a fake ScoutGemRepository so no network provider is contacted.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExploreViewModelUiStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var scoutDao: ScoutDao
    private lateinit var gemRepo: FakeScoutGemRepository
    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        scoutDao = db.scoutDao()
        val gemDao = db.discoveredGemDao()
        val achievementDao = db.achievementDao()
        val recentSearchDao = db.recentSearchDao()

        val scoutRepo = FakeScoutRepository(scoutDao, gemDao, achievementDao, context)
        gemRepo = FakeScoutGemRepository(scoutDao, gemDao, recentSearchDao)
        val preScoutEngine = PreScoutEngine(scoutDao, recentSearchDao, gemRepo)

        viewModel = ExploreViewModel(
            repository = scoutRepo,
            gemRepository = gemRepo,
            preScoutEngine = preScoutEngine
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------- loading

    @Test
    fun initialState_isAwaitingLocationWithEmptyFeed() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val state = viewModel.uiState.value

        assertTrue("Feed must start in the awaiting-location state", state.isAwaitingLocation)
        assertTrue("Feed must start empty", state.filteredSpots.isEmpty())
        assertTrue("All-spots must start empty", state.allSpots.isEmpty())
    }

    @Test
    fun scrapeOsmGems_showsLoadingFlagWhileDiscoveryRuns() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        // Hold the discovery coroutine inside the provider call so the loading window
        // can be observed deterministically instead of racing the IO thread.
        gemRepo.holdSyncGate = true
        gemRepo.osmSyncResult = Result.success(1)

        viewModel.scrapeOsmGems(38.7112, -9.1423)
        awaitCondition { gemRepo.syncEntered }

        assertTrue("isScrapingOsm must be true while discovery is running", viewModel.isScrapingOsm.value)

        gemRepo.holdSyncGate = false
        awaitCondition { !viewModel.isScrapingOsm.value }
    }

    // ---------------------------------------------------------------- success

    @Test
    fun updateUserLocation_clearsAwaitingAndPopulatesFeedWithNearbySpot() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        scoutDao.insertSpots(listOf(testSpot("s1", "Cafe Central", "cafe", 38.7112, -9.1423)))

        viewModel.updateUserLocation(38.7112, -9.1423)
        awaitCondition { !viewModel.uiState.value.isAwaitingLocation }
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }
        awaitCondition { viewModel.uiState.value.filteredSpots.isNotEmpty() }

        val state = viewModel.uiState.value
        assertFalse("Awaiting-location must clear after the first fix", state.isAwaitingLocation)
        assertEquals(Pair(38.7112, -9.1423), viewModel.userCoordinates.value)
        assertEquals("Filtered feed must contain the nearby spot", 1, state.filteredSpots.size)
        assertEquals("s1", state.filteredSpots.first().id)
    }

    @Test
    fun nearbySpotsAreSortedByProximityToTheUser() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        scoutDao.insertSpots(
            listOf(
                testSpot("far", "Distant Cafe", "cafe", 38.7300, -9.1423), // ~2.1 km north
                testSpot("near", "Nearby Cafe", "cafe", 38.7120, -9.1423)  // ~90 m north
            )
        )

        viewModel.updateUserLocation(38.7112, -9.1423)
        awaitCondition { viewModel.uiState.value.filteredSpots.size == 2 }
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }

        val ids = viewModel.uiState.value.filteredSpots.map { it.id }
        assertEquals(listOf("near", "far"), ids)
    }

    @Test
    fun scrapeOsmGems_success_setsCountMessageAndClearsTheLoadingFlag() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        gemRepo.osmSyncResult = Result.success(7)

        viewModel.scrapeOsmGems(38.7112, -9.1423)
        awaitCondition { !viewModel.isScrapingOsm.value }

        assertFalse("Loading flag must clear after success", viewModel.isScrapingOsm.value)
        assertEquals(
            "7 gems discovered around your location",
            viewModel.osmScrapeMessage.value
        )
    }

    @Test
    fun discoveryAfterLocationFix_returnsToIdleWhenFinished() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        scoutDao.insertSpots(listOf(testSpot("s1", "Cafe Central", "cafe", 38.7112, -9.1423)))

        viewModel.updateUserLocation(38.7112, -9.1423)
        awaitCondition { !viewModel.uiState.value.isAwaitingLocation }
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }

        assertFalse(
            "isDiscoveringGems must return to false once the discovery coroutine finishes",
            viewModel.uiState.value.isDiscoveringGems
        )
    }

    // ---------------------------------------------------------------- empty

    @Test
    fun searchWithoutMatches_reportsSuccessfulEmptyResultRatherThanLoading() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        scoutDao.insertSpots(listOf(testSpot("s1", "Pastel de Nata Bakery", "bakery", 38.7112, -9.1423)))

        viewModel.updateUserLocation(38.7112, -9.1423)
        awaitCondition { !viewModel.uiState.value.isAwaitingLocation }
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }
        awaitCondition { viewModel.uiState.value.filteredSpots.isNotEmpty() }

        viewModel.setSearchQuery("zzzz")
        awaitCondition { viewModel.uiState.value.filteredSpots.isEmpty() }
        // The location-fix discovery coroutine must have finished so the empty result is
        // observed in idle state, not while discovery is still running.
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }

        val state = viewModel.uiState.value
        assertTrue("A successful empty search must yield an empty feed", state.filteredSpots.isEmpty())
        assertFalse("Empty result must be distinct from the awaiting-location state", state.isAwaitingLocation)
        assertFalse("Empty result must be distinct from the discovering state", state.isDiscoveringGems)
        assertFalse("Empty result must be distinct from the scraping state", state.isScrapingOsm)
    }

    // ---------------------------------------------------------------- error

    @Test
    fun scrapeOsmGems_failure_setsErrorMessageAndClearsTheLoadingFlag() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        gemRepo.osmSyncResult = Result.failure(IllegalStateException("provider rate limited"))

        viewModel.scrapeOsmGems(38.7112, -9.1423)
        awaitCondition { !viewModel.isScrapingOsm.value }

        assertFalse("Loading flag must clear after failure", viewModel.isScrapingOsm.value)
        assertNotNull(viewModel.osmScrapeMessage.value)
        assertEquals(
            "Discovery note: provider rate limited",
            viewModel.osmScrapeMessage.value
        )
    }

    // ---------------------------------------------------------------- filters

    @Test
    fun setCategory_filtersTheFeedToTheMatchingCategoryOnly() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        scoutDao.insertSpots(
            listOf(
                testSpot("bakery", "Pastel de Nata Bakery", "bakery", 38.7112, -9.1423),
                testSpot("viewpoint", "Miradouro da Senhora do Monte", "viewpoint", 38.7125, -9.1300)
            )
        )

        viewModel.updateUserLocation(38.7112, -9.1423)
        awaitCondition { viewModel.uiState.value.filteredSpots.size == 2 }
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }

        viewModel.setCategory("eats")
        awaitCondition { viewModel.uiState.value.filteredSpots.size == 1 }

        val state = viewModel.uiState.value
        assertEquals("Only the eats spot must remain", "bakery", state.filteredSpots.single().id)
        assertEquals("eats", state.selectedCategory)
    }

    @Test
    fun setSearchQuery_filtersTheFeedByTitle() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        scoutDao.insertSpots(
            listOf(
                testSpot("bakery", "Pastel de Nata Bakery", "bakery", 38.7112, -9.1423),
                testSpot("viewpoint", "Miradouro da Senhora do Monte", "viewpoint", 38.7125, -9.1300)
            )
        )

        viewModel.updateUserLocation(38.7112, -9.1423)
        awaitCondition { viewModel.uiState.value.filteredSpots.size == 2 }
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }

        viewModel.setSearchQuery("pastel")
        awaitCondition { viewModel.uiState.value.filteredSpots.size == 1 }

        val state = viewModel.uiState.value
        assertEquals("bakery", state.filteredSpots.single().id)
        assertEquals("pastel", state.searchQuery)
    }

    @Test
    fun spotsBeyondTheVicinityLimitAreExcludedFromTheFeed() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        scoutDao.insertSpots(
            listOf(
                testSpot("near", "Nearby Cafe", "cafe", 38.7112, -9.1423),
                // ~90 km north of the user: outside the 60 km vicinity filter
                testSpot("far", "Distant Cafe", "cafe", 39.52, -9.1423)
            )
        )

        viewModel.updateUserLocation(38.7112, -9.1423)
        awaitCondition { viewModel.uiState.value.filteredSpots.isNotEmpty() }
        awaitCondition { !viewModel.uiState.value.isDiscoveringGems }

        val state = viewModel.uiState.value
        assertEquals("Only the in-vicinity spot must be listed", 1, state.filteredSpots.size)
        assertEquals("near", state.filteredSpots.single().id)
    }

    @Test
    fun updateUserLocation_ignoresZeroCoordinates() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.updateUserLocation(0.0, 0.0)

        assertTrue("A (0,0) fix must never become the user location", viewModel.userCoordinates.value == null)
        assertTrue("Awaiting-location must remain", viewModel.uiState.value.isAwaitingLocation)
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Polls until [condition] holds. The ViewModel performs discovery on Dispatchers.IO
     * (a real thread pool, not the test scheduler), so a bounded real-time wait is the
     * deterministic way to observe transitions driven from those coroutines.
     */
    private suspend fun awaitCondition(
        timeoutMs: Long = 10_000L,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                org.junit.Assert.fail("Timed out waiting for the expected UI state")
            }
            delay(20)
        }
    }

    private fun testSpot(
        id: String,
        title: String,
        category: String,
        lat: Double,
        lng: Double
    ) = ScoutSpotEntity(
        id = id,
        title = title,
        description = "Test spot",
        category = category,
        distance = "Nearby",
        walkMinutes = 5,
        imageUrl = "",
        badgeLabel = "test",
        statusBadge = "test",
        tagsRaw = "",
        isSaved = false,
        latitude = lat,
        longitude = lng
    )

    class FakeScoutRepository(
        scoutDao: ScoutDao,
        gemDao: DiscoveredGemDao,
        achievementDao: AchievementDao,
        context: Context?
    ) : ScoutRepository(scoutDao, gemDao, achievementDao, context) {
        override suspend fun initializeIfEmpty(): Int = 0
    }

    class FakeScoutGemRepository(
        scoutDao: ScoutDao,
        gemDao: DiscoveredGemDao,
        recentSearchDao: RecentSearchDao
    ) : ScoutGemRepository(scoutDao, gemDao, recentSearchDao) {

        val recentSearchesFlow = MutableStateFlow<List<RecentSearchEntity>>(emptyList())
        override val recentSearches: Flow<List<RecentSearchEntity>> = recentSearchesFlow

        var osmSyncResult: Result<Int> = Result.success(0)

        // Gate used to hold the discovery coroutine deterministically mid-flight.
        var holdSyncGate = false
        var syncEntered = false

        override suspend fun enrichSpotsPhotosInBackground(spots: List<ScoutSpotEntity>) {}

        override suspend fun syncLiveOsmGems(
            userLat: Double,
            userLng: Double,
            radiusMeters: Int,
            category: String,
            customQuery: String
        ): Result<Int> {
            syncEntered = true
            while (holdSyncGate) {
                delay(20)
            }
            return osmSyncResult
        }

        override suspend fun recordRecentSearch(queryText: String, category: String) {
            recentSearchesFlow.value = recentSearchesFlow.value + RecentSearchEntity(queryText, queryText, category, System.currentTimeMillis())
        }

        override suspend fun removeRecentSearch(id: String): Int {
            recentSearchesFlow.value = recentSearchesFlow.value.filterNot { it.id == id }
            return 1
        }

        override suspend fun clearAllRecentSearches(): Int {
            recentSearchesFlow.value = emptyList()
            return 1
        }
    }
}

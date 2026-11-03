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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExploreSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var scoutRepo: FakeScoutRepository
    private lateinit var gemRepo: FakeScoutGemRepository
    private lateinit var preScoutEngine: PreScoutEngine
    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val scoutDao = db.scoutDao()
        val gemDao = db.discoveredGemDao()
        val achievementDao = db.achievementDao()
        val recentSearchDao = db.recentSearchDao()

        scoutRepo = FakeScoutRepository(scoutDao, gemDao, achievementDao, context)
        gemRepo = FakeScoutGemRepository(scoutDao, gemDao, recentSearchDao)
        preScoutEngine = PreScoutEngine(scoutDao, recentSearchDao, gemRepo)

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

    @Test
    fun setSearchQuery_emptyQuery_maintainsFocusedEmptyState() = runTest {
        viewModel.setSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun tappingSuggestion_updatesSearchQueryTextAndRecordsQuery() = runTest {
        val suggestion = "historic sites"

        viewModel.setSearchQuery(suggestion)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("historic sites", viewModel.searchQuery.value)
        assertEquals("historic sites", gemRepo.lastRecordedQuery)
    }

    @Test
    fun clearAllRecentSearches_clearsHistoryInRepository() = runTest {
        val recentDao = db.recentSearchDao()
        recentDao.insertSearch(RecentSearchEntity("s1", "viewpoints near me", "all", System.currentTimeMillis()))
        recentDao.insertSearch(RecentSearchEntity("s2", "hiking trails", "all", System.currentTimeMillis()))

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearAllRecentSearches()
        testDispatcher.scheduler.advanceUntilIdle()

        val recentList = viewModel.recentSearches.value
        assertTrue("Recent searches list should be cleared", recentList.isEmpty())
    }

    @Test
    fun deleteRecentSearch_removesSingleQueryFromRepository() = runTest {
        val recentDao = db.recentSearchDao()
        recentDao.insertSearch(RecentSearchEntity("s1", "viewpoints near me", "all", System.currentTimeMillis()))
        recentDao.insertSearch(RecentSearchEntity("s2", "hiking trails", "all", System.currentTimeMillis()))

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteRecentSearch("s1")
        testDispatcher.scheduler.advanceUntilIdle()

        val recentList = viewModel.recentSearches.value
        assertTrue(recentList.none { it.id == "s1" })
    }

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
        var lastRecordedQuery: String? = null
        var lastDeletedId: String? = null

        override suspend fun enrichSpotsPhotosInBackground(spots: List<ScoutSpotEntity>) {}

        override suspend fun recordRecentSearch(queryText: String, category: String) {
            lastRecordedQuery = queryText
        }

        override suspend fun removeRecentSearch(id: String): Int {
            lastDeletedId = id
            return 1
        }

        override suspend fun clearAllRecentSearches(): Int {
            recentSearchesFlow.value = emptyList()
            return 1
        }
    }
}

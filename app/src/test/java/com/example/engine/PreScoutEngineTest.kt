package com.example.engine

import com.example.data.local.RecentSearchDao
import com.example.data.local.RecentSearchEntity
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.repository.ScoutGemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreScoutEngineTest {

    private lateinit var fakeScoutDao: FakeScoutDao
    private lateinit var fakeRecentSearchDao: FakeRecentSearchDao
    private lateinit var repo: ScoutGemRepository
    private lateinit var preScoutEngine: PreScoutEngine

    @Before
    fun setUp() {
        fakeScoutDao = FakeScoutDao()
        fakeRecentSearchDao = FakeRecentSearchDao()
        repo = ScoutGemRepository(fakeScoutDao, null, fakeRecentSearchDao)
        preScoutEngine = PreScoutEngine(fakeScoutDao, fakeRecentSearchDao, repo)
    }

    @Test
    fun shouldTriggerPreScout_initialLocation_returnsTrue() {
        val triggered = preScoutEngine.shouldTriggerPreScout(
            lat = 38.7112,
            lng = -9.1423,
            cityName = "Lisbon"
        )
        assertTrue("Initial location check should trigger pre-scout", triggered)
    }

    @Test
    fun shouldTriggerPreScout_sameCitySmallMovement_returnsFalse() {
        // Initial anchor setup
        preScoutEngine.shouldTriggerPreScout(38.7112, -9.1423, "Lisbon")

        // Move 500m within Lisbon
        val triggered = preScoutEngine.shouldTriggerPreScout(
            lat = 38.7150,
            lng = -9.1400,
            cityName = "Lisbon"
        )
        assertFalse("Small movement in same city should not trigger pre-scout", triggered)
    }

    @Test
    fun shouldTriggerPreScout_newCityBoundaryEntry_returnsTrue() {
        // Initial anchor setup in Lisbon
        preScoutEngine.shouldTriggerPreScout(38.7112, -9.1423, "Lisbon")

        // Enter Sintra city boundary
        val triggered = preScoutEngine.shouldTriggerPreScout(
            lat = 38.7983,
            lng = -9.3880,
            cityName = "Sintra"
        )
        assertTrue("Entering new city boundary should trigger pre-scout", triggered)
    }

    @Test
    fun shouldTriggerPreScout_sameCityLargeDistanceBoundaryEntry_returnsTrue() {
        // Initial anchor setup in Lisbon
        preScoutEngine.shouldTriggerPreScout(38.7112, -9.1423, "Lisbon")

        // Move > 10,000m (e.g. 15km shift)
        val triggered = preScoutEngine.shouldTriggerPreScout(
            lat = 38.5800,
            lng = -9.1423,
            cityName = "Lisbon"
        )
        assertTrue("Moving > 10km within same city should trigger distance boundary pre-scout", triggered)
    }

    @Test
    fun calculateCategoryWeights_evaluatesSearchesAndBookmarksCorrectly() = runBlocking {
        fakeRecentSearchDao.searches = listOf(
            RecentSearchEntity(id = "1", queryText = "best cafe for eat", category = "eats"),
            RecentSearchEntity(id = "2", queryText = "gothic church", category = "history")
        )

        fakeScoutDao.savedSpots = listOf(
            ScoutSpotEntity(
                id = "spot1",
                title = "Tile Secret Spot",
                description = "Hidden shortcut",
                category = "shortcut",
                distance = "120m",
                walkMinutes = 2,
                imageUrl = "http://example.com/img.jpg",
                badgeLabel = "Secret",
                statusBadge = "Verified",
                tagsRaw = "shortcut,tile"
            )
        )

        val weights = preScoutEngine.calculateCategoryWeights()

        // Base weight = 1
        // "eats": base 1 + 2 (recent search) = 3
        // "history": base 1 + 2 (recent search) = 3
        // "shortcut": base 1 + 3 (saved spot) = 4
        // "sights": base 1 = 1
        assertEquals(3, weights["eats"])
        assertEquals(3, weights["history"])
        assertEquals(4, weights["shortcut"])
        assertEquals(1, weights["sights"])
    }

    @Test
    fun executePreScout_createsBannerStateWithTopCategories() = runBlocking {
        fakeRecentSearchDao.searches = listOf(
            RecentSearchEntity(id = "1", queryText = "gothic history", category = "history"),
            RecentSearchEntity(id = "2", queryText = "ancient ruins", category = "history")
        )

        val banner = preScoutEngine.executePreScout(38.7112, -9.1423, "Lisbon")

        assertTrue(banner.isVisible)
        assertEquals("Lisbon", banner.cityName)
        assertEquals("history", banner.topCategories.first())
    }

    // Fake DAOs for unit testing
    class FakeScoutDao : ScoutDao {
        var savedSpots = listOf<ScoutSpotEntity>()
        var allSpots = listOf<ScoutSpotEntity>()

        override fun getAllSpots(): Flow<List<ScoutSpotEntity>> = flowOf(allSpots)
        override fun getSavedSpots(): Flow<List<ScoutSpotEntity>> = flowOf(savedSpots)
        override suspend fun getSavedSpotsList(): List<ScoutSpotEntity> = savedSpots
        override fun getDiscoveredSpots(): Flow<List<ScoutSpotEntity>> = flowOf(emptyList())
        override suspend fun getDiscoveredSpotsSync(): List<ScoutSpotEntity> = emptyList()
        override suspend fun getAllSpotsSync(): List<ScoutSpotEntity> = allSpots
        override fun getOfflineDrafts(): Flow<List<ScoutSpotEntity>> = flowOf(emptyList())
        override suspend fun getSpotById(id: String): ScoutSpotEntity? = allSpots.find { it.id == id }
        override fun getSpotsByCategory(category: String): Flow<List<ScoutSpotEntity>> = flowOf(emptyList())
        override fun searchSpots(query: String): Flow<List<ScoutSpotEntity>> = flowOf(emptyList())
        override suspend fun insertSpots(spots: List<ScoutSpotEntity>) { allSpots = allSpots + spots }
        override suspend fun insertSpot(spot: ScoutSpotEntity) { allSpots = allSpots + spot }
        override suspend fun updateSpot(spot: ScoutSpotEntity) {}
        override suspend fun deleteSpot(spot: ScoutSpotEntity) {}
        override suspend fun setSavedStatus(id: String, isSaved: Boolean) {}
        override suspend fun setDiscoveredStatus(id: String, isDiscovered: Boolean, timestamp: Long) {}
        override suspend fun updateUserNotes(id: String, notes: String) {}
        override suspend fun updateImageUrl(id: String, imageUrl: String) {}
        override suspend fun incrementUpvote(id: String) {}
        override suspend fun getCount(): Int = allSpots.size
        override suspend fun getDiscoveredCount(): Int = 0
        override suspend fun getAllSpotsList(): List<ScoutSpotEntity> = allSpots
        override suspend fun deleteUnsavedDistantSpots(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) {}
        override suspend fun clearFallbackSpots() {}
        override suspend fun deleteExpiredSpots(cutoffTimestamp: Long): Int = 0
        override suspend fun deleteSpotsByIds(spotIds: List<String>): Int = 0
        override suspend fun getUnsavedTransientSpots(): List<ScoutSpotEntity> = emptyList()
        override suspend fun clearAllTransientSpots(): Int = 0
    }

    class FakeRecentSearchDao : RecentSearchDao {
        var searches = listOf<RecentSearchEntity>()

        override fun getRecentSearches(): Flow<List<RecentSearchEntity>> = flowOf(searches)
        override suspend fun getRecentSearchesList(): List<RecentSearchEntity> = searches
        override suspend fun insertSearch(search: RecentSearchEntity) { searches = searches + search }
        override suspend fun findByQueryText(queryText: String): RecentSearchEntity? = searches.find { it.queryText.equals(queryText, ignoreCase = true) }
        override suspend fun deleteSearchById(id: String): Int = 1
        override suspend fun clearAllSearches(): Int = 0
        override suspend fun trimExcessSearches(): Int = 0
        override suspend fun getCount(): Int = searches.size
    }
}

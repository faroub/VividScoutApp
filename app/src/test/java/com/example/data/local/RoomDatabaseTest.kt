package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var scoutDao: ScoutDao
    private lateinit var discoveredGemDao: DiscoveredGemDao
    private lateinit var recentSearchDao: RecentSearchDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scoutDao = db.scoutDao()
        discoveredGemDao = db.discoveredGemDao()
        recentSearchDao = db.recentSearchDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveSpot() = runTest {
        val spot = ScoutSpotEntity(
            id = "test_spot_1",
            title = "Miradouro de Santa Luzia",
            description = "Secret Pergola Nook with blue azulejo tiles",
            category = "sights",
            distance = "350m walk",
            walkMinutes = 5,
            rating = 4.9,
            reviewCount = "1.8k",
            imageUrl = "https://example.com/photo.jpg",
            badgeLabel = "SIGHTS",
            statusBadge = "Scraped 8m ago",
            tagsRaw = "Free Entry|||Quiet sunny nook",
            isSaved = false,
            isDiscovered = false
        )
        scoutDao.insertSpot(spot)

        val retrieved = scoutDao.getSpotById("test_spot_1")
        assertNotNull(retrieved)
        assertEquals("Miradouro de Santa Luzia", retrieved?.title)
        assertFalse(retrieved?.isSaved ?: true)
        assertFalse(retrieved?.isDiscovered ?: true)
    }

    @Test
    fun offlineSaveAndBookmarkToggle() = runTest {
        val spot = ScoutSpotEntity(
            id = "test_spot_2",
            title = "Pastelaria Santo António",
            description = "Warm Custard Batch",
            category = "eats",
            distance = "180m walk",
            walkMinutes = 3,
            rating = 4.8,
            reviewCount = "950",
            imageUrl = "https://example.com/photo2.jpg",
            badgeLabel = "WARM BAKERY ALERT",
            statusBadge = "Oven: 4m ago",
            tagsRaw = "Fresh Batch Pulled",
            isSaved = false
        )
        scoutDao.insertSpot(spot)

        // Bookmark spot for offline
        scoutDao.setSavedStatus("test_spot_2", true)

        val savedSpots = scoutDao.getSavedSpots().first()
        assertEquals(1, savedSpots.size)
        assertEquals("test_spot_2", savedSpots.first().id)
        assertTrue(savedSpots.first().isSaved)
    }

    @Test
    fun markLocationAsDiscoveredAndAddNotes() = runTest {
        val spot = ScoutSpotEntity(
            id = "test_spot_3",
            title = "Hidden Tile Tunnel",
            description = "Unmarked 18th-century covered tile tunnel",
            category = "shortcut",
            distance = "520m walk",
            walkMinutes = 7,
            rating = 4.9,
            reviewCount = "88",
            imageUrl = "https://example.com/photo3.jpg",
            badgeLabel = "COMMUNITY GEM",
            statusBadge = "94% Verified",
            tagsRaw = "Cuts 12 mins uphill",
            isSaved = false,
            isDiscovered = false
        )
        scoutDao.insertSpot(spot)

        // Mark as discovered with timestamp
        val discoveryTime = System.currentTimeMillis()
        scoutDao.setDiscoveredStatus("test_spot_3", true, discoveryTime)
        scoutDao.updateUserNotes("test_spot_3", "Incredible shortcut, paved in blue azulejos.")

        val discoveredSpots = scoutDao.getDiscoveredSpots().first()
        assertEquals(1, discoveredSpots.size)
        val discovered = discoveredSpots.first()
        assertTrue(discovered.isDiscovered)
        assertEquals(discoveryTime, discovered.discoveredTimestamp)
        assertEquals("Incredible shortcut, paved in blue azulejos.", discovered.userNotes)
        assertEquals(1, scoutDao.getDiscoveredCount())
    }

    @Test
    fun dedicatedDiscoveredGemDaoOperations() = runTest {
        val customGem = DiscoveredGemEntity(
            id = "gem_custom_1",
            title = "Beco do Forno Secret Terrace",
            description = "Small stone balustrade overlooking terracotta roofs",
            category = "sights",
            locationName = "Alfama Sector 4",
            latitude = 38.7115,
            longitude = -9.1310,
            photoUri = "file:///storage/emulated/0/DCIM/gem1.jpg",
            notes = "Quiet spot with bougainvillea shade",
            isSynced = false
        )

        discoveredGemDao.insertDiscoveredGem(customGem)
        assertEquals(1, discoveredGemDao.getCount())

        val allGems = discoveredGemDao.getAllDiscoveredGems().first()
        assertEquals(1, allGems.size)
        assertEquals("Beco do Forno Secret Terrace", allGems.first().title)
        assertFalse(allGems.first().isSynced)

        // Mark synced
        discoveredGemDao.markAsSynced("gem_custom_1")
        val updated = discoveredGemDao.getGemById("gem_custom_1")
        assertTrue(updated?.isSynced ?: false)
    }

    @Test
    fun autoCleanupDeletesExpiredUnsavedSpotsWhileProtectingSavedAndDiscovered() = runTest {
        val now = System.currentTimeMillis()
        val thirtyFiveDaysAgo = now - (35L * 24L * 60L * 60L * 1000L)
        val fiveDaysAgo = now - (5L * 24L * 60L * 60L * 1000L)

        // 1. Expired un-saved spot (should be deleted)
        val expiredUnsaved = ScoutSpotEntity(
            id = "spot_expired_unsaved",
            title = "Stale Viewpoint",
            description = "Old cached node",
            category = "sights",
            distance = "200m",
            walkMinutes = 2,
            rating = 4.5,
            reviewCount = "10",
            imageUrl = "https://example.com/stale.jpg",
            badgeLabel = "STALE",
            statusBadge = "Stale",
            tagsRaw = "old",
            timestamp = thirtyFiveDaysAgo,
            isSaved = false,
            isDiscovered = false,
            isOfflineDraft = false
        )

        // 2. Expired but SAVED spot (MUST be preserved)
        val expiredSaved = ScoutSpotEntity(
            id = "spot_expired_saved",
            title = "Favorite Historic Castle",
            description = "Bookmarked long ago",
            category = "history",
            distance = "500m",
            walkMinutes = 6,
            rating = 4.9,
            reviewCount = "500",
            imageUrl = "https://example.com/castle.jpg",
            badgeLabel = "FAVORITE",
            statusBadge = "Saved",
            tagsRaw = "castle",
            timestamp = thirtyFiveDaysAgo,
            isSaved = true,
            isDiscovered = false,
            isOfflineDraft = false
        )

        // 3. Expired but DISCOVERED spot (MUST be preserved)
        val expiredDiscovered = ScoutSpotEntity(
            id = "spot_expired_discovered",
            title = "Visited Secret Staircase",
            description = "Checked off checklist",
            category = "gems",
            distance = "300m",
            walkMinutes = 4,
            rating = 4.8,
            reviewCount = "50",
            imageUrl = "https://example.com/steps.jpg",
            badgeLabel = "VISITED",
            statusBadge = "Discovered",
            tagsRaw = "visited",
            timestamp = thirtyFiveDaysAgo,
            isSaved = false,
            isDiscovered = true,
            isOfflineDraft = false
        )

        // 4. Recent un-saved spot (MUST be preserved)
        val recentUnsaved = ScoutSpotEntity(
            id = "spot_recent_unsaved",
            title = "Fresh Bakery Batch",
            description = "Recently cached",
            category = "eats",
            distance = "100m",
            walkMinutes = 1,
            rating = 4.7,
            reviewCount = "80",
            imageUrl = "https://example.com/fresh.jpg",
            badgeLabel = "FRESH",
            statusBadge = "Recent",
            tagsRaw = "fresh",
            timestamp = fiveDaysAgo,
            isSaved = false,
            isDiscovered = false,
            isOfflineDraft = false
        )

        scoutDao.insertSpots(listOf(expiredUnsaved, expiredSaved, expiredDiscovered, recentUnsaved))
        assertEquals(4, scoutDao.getCount())

        val thirtyDaysCutoff = now - (30L * 24L * 60L * 60L * 1000L)
        val deletedCount = scoutDao.deleteExpiredSpots(thirtyDaysCutoff)
        assertEquals(1, deletedCount)

        // Verify remaining spots
        val remaining = scoutDao.getAllSpotsList()
        assertEquals(3, remaining.size)
        assertTrue(remaining.any { it.id == "spot_expired_saved" })
        assertTrue(remaining.any { it.id == "spot_expired_discovered" })
        assertTrue(remaining.any { it.id == "spot_recent_unsaved" })
        assertFalse(remaining.any { it.id == "spot_expired_unsaved" })
    }

    @Test
    fun repositoryPerformAutoCleanupPurgesExpiredAndDistantSpots() = runTest {
        val repository = com.example.data.repository.ScoutGemRepository(scoutDao, discoveredGemDao)

        val now = System.currentTimeMillis()
        val fortyDaysAgo = now - (40L * 24L * 60L * 60L * 1000L)
        val userLat = 38.7112
        val userLng = -9.1423

        // Spot 1: Expired (>30 days) nearby Lisbon (should be removed by age)
        val spotExpired = ScoutSpotEntity(
            id = "spot_auto_expired",
            title = "Old Lisbon Alley",
            description = "Expired un-saved",
            category = "gems",
            distance = "300m",
            walkMinutes = 3,
            rating = 4.5,
            reviewCount = "12",
            imageUrl = "",
            badgeLabel = "",
            statusBadge = "",
            tagsRaw = "",
            latitude = 38.7115,
            longitude = -9.1420,
            timestamp = fortyDaysAgo,
            isSaved = false,
            isDiscovered = false
        )

        // Spot 2: Recent, but in Porto (~275 km away, > 100km radius cutoff; should be removed by distance)
        val spotDistantPorto = ScoutSpotEntity(
            id = "spot_auto_porto",
            title = "Ponte Luís I Porto",
            description = "Far away un-saved spot",
            category = "sights",
            distance = "275km",
            walkMinutes = 999,
            rating = 4.9,
            reviewCount = "5k",
            imageUrl = "",
            badgeLabel = "",
            statusBadge = "",
            tagsRaw = "",
            latitude = 41.1400, // Porto latitude (~275km north of Lisbon)
            longitude = -8.6095,
            timestamp = now,
            isSaved = false,
            isDiscovered = false
        )

        // Spot 3: Far away in Porto, but SAVED (MUST be kept)
        val spotSavedPorto = ScoutSpotEntity(
            id = "spot_auto_porto_saved",
            title = "Livraria Lello Porto",
            description = "Saved distant spot",
            category = "sights",
            distance = "275km",
            walkMinutes = 999,
            rating = 4.8,
            reviewCount = "4k",
            imageUrl = "",
            badgeLabel = "",
            statusBadge = "",
            tagsRaw = "",
            latitude = 41.1469,
            longitude = -8.6148,
            timestamp = now,
            isSaved = true,
            isDiscovered = false
        )

        // Spot 4: Recent and within 2km of user (MUST be kept)
        val spotNearbyRecent = ScoutSpotEntity(
            id = "spot_auto_nearby",
            title = "Praca do Comercio",
            description = "Near and recent",
            category = "sights",
            distance = "500m",
            walkMinutes = 6,
            rating = 4.9,
            reviewCount = "3k",
            imageUrl = "",
            badgeLabel = "",
            statusBadge = "",
            tagsRaw = "",
            latitude = 38.7075,
            longitude = -9.1364,
            timestamp = now,
            isSaved = false,
            isDiscovered = false
        )

        scoutDao.insertSpots(listOf(spotExpired, spotDistantPorto, spotSavedPorto, spotNearbyRecent))
        assertEquals(4, scoutDao.getCount())

        val report = repository.performAutoCleanup(userLat, userLng, maxAgeDays = 30, maxRadiusKm = 100.0)

        assertEquals(1, report.expiredRemoved)
        assertEquals(1, report.distantRemoved)
        assertEquals(2, report.totalRemoved)

        val remaining = scoutDao.getAllSpotsList()
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.id == "spot_auto_porto_saved" })
        assertTrue(remaining.any { it.id == "spot_auto_nearby" })
        assertFalse(remaining.any { it.id == "spot_auto_expired" })
        assertFalse(remaining.any { it.id == "spot_auto_porto" })
    }

    @Test
    fun manualClearTransientCachePurgesOnlyUnsavedTransientSpots() = runTest {
        val repository = com.example.data.repository.ScoutGemRepository(scoutDao, discoveredGemDao)

        // 1. Transient cached spot 1
        val transientSpot1 = ScoutSpotEntity(
            id = "transient_1",
            title = "Unsaved Cached Cafe",
            description = "Ephemeral map discovery",
            category = "eats",
            distance = "150m",
            walkMinutes = 2,
            rating = 4.3,
            reviewCount = "12",
            imageUrl = "",
            badgeLabel = "",
            statusBadge = "",
            tagsRaw = "",
            isSaved = false,
            isDiscovered = false,
            isOfflineDraft = false
        )

        // 2. Transient cached spot 2
        val transientSpot2 = ScoutSpotEntity(
            id = "transient_2",
            title = "Unsaved Cached Viewpoint",
            description = "Ephemeral map discovery",
            category = "sights",
            distance = "300m",
            walkMinutes = 4,
            rating = 4.6,
            reviewCount = "45",
            imageUrl = "",
            badgeLabel = "",
            statusBadge = "",
            tagsRaw = "",
            isSaved = false,
            isDiscovered = false,
            isOfflineDraft = false
        )

        // 3. Saved bookmark (MUST BE PRESERVED)
        val savedSpot = ScoutSpotEntity(
            id = "saved_spot_1",
            title = "My Favorite Miradouro",
            description = "Bookmarked gem",
            category = "sights",
            distance = "500m",
            walkMinutes = 6,
            rating = 4.9,
            reviewCount = "800",
            imageUrl = "",
            badgeLabel = "SAVED",
            statusBadge = "Bookmarked",
            tagsRaw = "favorite",
            isSaved = true,
            isDiscovered = false,
            isOfflineDraft = false
        )

        // 4. Discovered badge (MUST BE PRESERVED)
        val discoveredSpot = ScoutSpotEntity(
            id = "discovered_spot_1",
            title = "Secret Tiled Archway",
            description = "Explored and stamped in passport",
            category = "gems",
            distance = "400m",
            walkMinutes = 5,
            rating = 4.8,
            reviewCount = "150",
            imageUrl = "",
            badgeLabel = "DISCOVERED",
            statusBadge = "Discovered",
            tagsRaw = "passport",
            isSaved = false,
            isDiscovered = true,
            isOfflineDraft = false
        )

        // 5. Offline Draft (MUST BE PRESERVED)
        val offlineDraftSpot = ScoutSpotEntity(
            id = "draft_spot_1",
            title = "Unsynced Local Discovery",
            description = "Created while offline",
            category = "shortcuts",
            distance = "50m",
            walkMinutes = 1,
            rating = 5.0,
            reviewCount = "1",
            imageUrl = "",
            badgeLabel = "DRAFT",
            statusBadge = "Offline Draft",
            tagsRaw = "local",
            isSaved = false,
            isDiscovered = false,
            isOfflineDraft = true
        )

        scoutDao.insertSpots(listOf(transientSpot1, transientSpot2, savedSpot, discoveredSpot, offlineDraftSpot))
        assertEquals(5, scoutDao.getCount())

        val purgedCount = repository.clearTransientCache()
        assertEquals(2, purgedCount)

        val remainingSpots = scoutDao.getAllSpotsList()
        assertEquals(3, remainingSpots.size)
        assertTrue(remainingSpots.any { it.id == "saved_spot_1" })
        assertTrue(remainingSpots.any { it.id == "discovered_spot_1" })
        assertTrue(remainingSpots.any { it.id == "draft_spot_1" })
        assertFalse(remainingSpots.any { it.id == "transient_1" })
        assertFalse(remainingSpots.any { it.id == "transient_2" })
    }

    @Test
    fun recentSearchDaoPersistsTop5QueriesInLruOrder() = runTest {
        val baseTime = 1000000L
        for (i in 1..7) {
            recentSearchDao.insertSearch(
                RecentSearchEntity(
                    id = "search_$i",
                    queryText = "Query #$i",
                    category = "sights",
                    timestamp = baseTime + (i * 1000L)
                )
            )
        }

        // DAO getRecentSearchesList returns top 5 ordered by timestamp DESC
        val recent = recentSearchDao.getRecentSearchesList()
        assertEquals(5, recent.size)
        assertEquals("Query #7", recent[0].queryText)
        assertEquals("Query #6", recent[1].queryText)
        assertEquals("Query #5", recent[2].queryText)
        assertEquals("Query #4", recent[3].queryText)
        assertEquals("Query #3", recent[4].queryText)

        // After trimming excess searches, total count in DB should be capped at 5
        recentSearchDao.trimExcessSearches()
        assertEquals(5, recentSearchDao.getCount())
    }

    @Test
    fun repositoryRecordRecentSearchReElevatesDuplicateQueries() = runTest {
        val repository = com.example.data.repository.ScoutGemRepository(
            scoutDao = scoutDao,
            discoveredGemDao = discoveredGemDao,
            recentSearchDao = recentSearchDao
        )

        // Insert 5 searches
        repository.recordRecentSearch("Azulejo Tiles", "sights")
        repository.recordRecentSearch("Fado Music", "history")
        repository.recordRecentSearch("Custard Tarts", "eats")
        repository.recordRecentSearch("Tram 28 Stops", "shortcuts")
        repository.recordRecentSearch("Riverfront Sunset", "sights")

        var list = recentSearchDao.getRecentSearchesList()
        assertEquals(5, list.size)
        assertEquals("Riverfront Sunset", list.first().queryText)

        // Now search "Azulejo Tiles" again - it should be re-elevated to index 0 without duplicating rows
        repository.recordRecentSearch("azulejo tiles", "sights")
        list = recentSearchDao.getRecentSearchesList()
        assertEquals(5, list.size)
        assertEquals("Azulejo Tiles", list.first().queryText)
    }

    @Test
    fun repositoryDeleteAndClearRecentSearches() = runTest {
        val repository = com.example.data.repository.ScoutGemRepository(
            scoutDao = scoutDao,
            discoveredGemDao = discoveredGemDao,
            recentSearchDao = recentSearchDao
        )

        repository.recordRecentSearch("Search A")
        repository.recordRecentSearch("Search B")
        repository.recordRecentSearch("Search C")

        var list = recentSearchDao.getRecentSearchesList()
        assertEquals(3, list.size)

        val toDelete = list.find { it.queryText == "Search B" }
        assertNotNull(toDelete)
        val deleted = repository.removeRecentSearch(toDelete!!.id)
        assertEquals(1, deleted)

        list = recentSearchDao.getRecentSearchesList()
        assertEquals(2, list.size)
        assertFalse(list.any { it.queryText == "Search B" })

        // Clear all
        val cleared = repository.clearAllRecentSearches()
        assertEquals(2, cleared)
        assertEquals(0, recentSearchDao.getCount())
    }
}


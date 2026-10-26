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

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scoutDao = db.scoutDao()
        discoveredGemDao = db.discoveredGemDao()
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
}

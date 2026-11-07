package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DiscoveredGemDao
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.isDemoFallbackSpot
import com.example.data.network.OverpassRetrofitClient
import com.example.data.sync.FirestoreSyncManager
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
 * Room-level behavior for illustrative "Offline Demo Spot" records generated when
 * every Overpass mirror fails or returns zero elements:
 *
 * - They never enter the offline-draft upload queue.
 * - The trusted Firestore upload boundary refuses them (defense in depth).
 * - Inserting them after a provider failure must never overwrite real cached places.
 * - Clearing fallback spots keeps bookmarked demo records and all real records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DemoFallbackSpotSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var scoutDao: ScoutDao
    private lateinit var discoveredGemDao: DiscoveredGemDao
    private lateinit var context: Context
    private val overpassClient = OverpassRetrofitClient()

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

    private fun realOsmSpot(id: String = "osm_node_501"): ScoutSpotEntity = ScoutSpotEntity(
        id = id,
        title = "Miradouro de Santa Luzia",
        description = "Real OpenStreetMap viewpoint node.",
        category = "sights",
        distance = "120m away",
        walkMinutes = 2,
        imageUrl = "https://upload.wikimedia.org/example.jpg",
        badgeLabel = "OSM VIEWPOINT",
        statusBadge = "Live OSM Scout",
        tagsRaw = "Retrofit|||OpenStreetMap|||Nodes Verified",
        contributor = "@osm_contributor",
        latitude = 38.7120,
        longitude = -9.1400,
        timestamp = 1_700_000_000_000L
    )

    @Test
    fun generatedDemoSpots_neverEnterTheOfflineDraftUploadQueue() = runBlocking {
        val demoSpots = overpassClient.generateNearbyFallbackSpots(38.7112, -9.1423, 6)
        scoutDao.insertSpots(demoSpots)

        assertTrue(
            "Demo records are created without the offline-draft flag and must stay out of the retry queue",
            scoutDao.getOfflineDrafts().first().isEmpty()
        )
        assertTrue(
            "A sweep of isOfflineDraft rows must not pick up demo records",
            scoutDao.getAllSpotsSync().filter { it.isOfflineDraft }.isEmpty()
        )
    }

    @Test
    fun syncDraftSpotToCloud_refusesDemoSpotsAndLeavesThemUntouched() = runBlocking {
        // Worst case: a demo record that somehow carries the offline-draft flag (for
        // example a future caller queuing the wrong row). The trusted boundary in
        // FirestoreSyncManager must still refuse to publish it as a community
        // submission and must not rewrite any local success state.
        val demo = overpassClient.generateNearbyFallbackSpots(38.7112, -9.1423, 1)
            .first()
            .copy(isOfflineDraft = true)
        scoutDao.insertSpot(demo)

        val result = FirestoreSyncManager.syncDraftSpotToCloud(context, demo, scoutDao, discoveredGemDao)

        assertFalse("The production uploader must refuse to sync an illustrative demo spot", result)
        val fromDb = scoutDao.getSpotById(demo.id)
        assertEquals("The demo record must keep its honest badge", "Offline Demo Spot", fromDb?.statusBadge)
        assertTrue("Nothing may flip the row into a synced-looking state", fromDb?.isOfflineDraft == true)
    }

    @Test
    fun providerFailureFallbackInsert_preservesRealCachedSpots() = runBlocking {
        val real = realOsmSpot()
        scoutDao.insertSpot(real)

        // The provider-failure path: fallback records are inserted next to the cache.
        val fallback = overpassClient.generateNearbyFallbackSpots(38.7112, -9.1423, 6)
        scoutDao.insertSpots(fallback)

        val realFromDb = scoutDao.getSpotById(real.id)
        assertEquals("The real cached spot must survive untouched", real.title, realFromDb?.title)
        assertEquals("Live OSM Scout", realFromDb?.statusBadge)
        assertEquals(
            "The real cached spot must not be duplicated",
            1,
            scoutDao.getAllSpotsSync().count { it.id == real.id }
        )
        assertTrue(
            "The demo fallback records keep the offline experience usable",
            scoutDao.getAllSpotsSync().count { it.isDemoFallbackSpot } == 6
        )
    }

    @Test
    fun clearFallbackSpots_removesOnlyUnsavedDemoRecords() = runBlocking {
        val transientDemo = overpassClient.generateNearbyFallbackSpots(38.7112, -9.1423, 1).first()
        val savedDemo = overpassClient.generateNearbyFallbackSpots(38.7112, -9.1423, 1).first()
            .copy(id = "osm_fallback_saved", isSaved = true)
        val real = realOsmSpot()
        scoutDao.insertSpots(listOf(transientDemo, savedDemo, real))

        // The real-data path clears stale demo records before inserting fresh results.
        scoutDao.clearFallbackSpots()

        assertNull("The transient demo record should have been removed", scoutDao.getSpotById(transientDemo.id))
        assertTrue("A bookmarked demo record is the user's data and must survive", scoutDao.getSpotById(savedDemo.id) != null)
        assertTrue("Real cached places are never cleared by fallback cleanup", scoutDao.getSpotById(real.id) != null)
    }
}

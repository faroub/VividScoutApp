package com.example.data.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ScoutSpotEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locally synthesized region packs must persist and reload their synthetic flag so
 * routing provenance survives an app restart: a plan built from a reloaded pack
 * stays approximate instead of being mistaken for real street geometry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineRegionStorageTest {

    private lateinit var context: Context
    private lateinit var storage: OfflineRegionStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = OfflineRegionStorage(context)
    }

    private fun spot(id: String): ScoutSpotEntity = ScoutSpotEntity(
        id = id,
        title = "Spot $id",
        description = "desc",
        category = "sights",
        distance = "Nearby",
        walkMinutes = 5,
        imageUrl = "",
        badgeLabel = "Spot",
        statusBadge = "Scout Spot",
        tagsRaw = "sights|||test",
        latitude = 38.7160,
        longitude = -9.1300
    )

    @Test
    fun synthesizedNetwork_marksEverySegmentSynthetic() {
        val bounds = GeoBoundingBox(38.7100, -9.1400, 38.7200, -9.1200)
        val (segments, _) = storage.synthesizeRoadNetworkForBounds(
            bounds = bounds,
            regionName = "Alfama Test",
            cachedSpots = listOf(spot("s1"))
        )

        assertTrue("The synthesized grid must produce segments", segments.isNotEmpty())
        assertTrue(
            "Every synthesized segment must carry the synthetic flag",
            segments.all { it.isSynthetic }
        )
    }

    @Test
    fun saveAndReload_preservesSyntheticFlagForSynthesizedPacks() {
        val bounds = GeoBoundingBox(38.7100, -9.1400, 38.7200, -9.1200)
        val (segments, elevAnchors) = storage.synthesizeRoadNetworkForBounds(
            bounds = bounds,
            regionName = "Synthetic Pack",
            cachedSpots = emptyList()
        )
        storage.saveRegion(
            DownloadedRegionPack(
                id = "region_synthetic_roundtrip",
                name = "Synthetic Pack",
                bounds = bounds,
                roadSegments = segments,
                elevationAnchors = elevAnchors,
                spotsCount = 0,
                totalSizeBytes = 500_000L
            )
        )

        val reloaded = storage.getAllRegions().single { it.id == "region_synthetic_roundtrip" }
        assertEquals(
            "The synthetic flag must survive save/load so reloaded packs stay approximate",
            true,
            reloaded.roadSegments.all { it.isSynthetic }
        )
    }

    @Test
    fun saveAndReload_keepsRealImportedPacksNonSynthetic() {
        val bounds = GeoBoundingBox(38.7100, -9.1400, 38.7200, -9.1200)
        val realSegments = listOf(
            DownloadedRoadSegment(
                id = "real_seg_1",
                fromId = "real_a",
                toId = "real_b",
                fromLat = 38.7110,
                fromLng = -9.1390,
                toLat = 38.7120,
                toLng = -9.1380,
                distanceMeters = 140.0,
                streetName = "Imported Pedestrian Street",
                isPedestrian = true,
                isStairs = false,
                isSynthetic = false
            )
        )
        storage.saveRegion(
            DownloadedRegionPack(
                id = "region_real_roundtrip",
                name = "Imported Pack",
                bounds = bounds,
                roadSegments = realSegments,
                elevationAnchors = emptyList(),
                spotsCount = 0,
                totalSizeBytes = 500_000L
            )
        )

        val reloaded = storage.getAllRegions().single { it.id == "region_real_roundtrip" }
        assertEquals(1, reloaded.roadSegments.size)
        assertFalse(
            "Genuinely imported packs must keep non-synthetic provenance after reload",
            reloaded.roadSegments.first().isSynthetic
        )
    }
}

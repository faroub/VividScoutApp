package com.example.data.places

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class PlaceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CachedPlaceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cachedPlaceDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun tileEdgeDiscovery_coveringTiles_retrievesPlacesAcrossTileBorders() = runBlocking {
        val lat = 38.7112
        val lon = -9.1423

        val coveringTiles = GeohashUtils.getCoveringTiles(lat, lon, radiusMeters = 3000)
        assertTrue("Covering tiles must contain center and surrounding neighbor tiles", coveringTiles.size >= 4)

        // Insert place on neighbor tile
        val neighborTile = coveringTiles.last()
        val borderEntity = CachedPlaceEntity(
            placeId = "border_1",
            primaryName = "Border Viewpoint",
            primaryDescription = "Viewpoint on neighbor tile",
            latitude = 38.7130,
            longitude = -9.1400,
            category = "sights",
            sourcesJson = "[]",
            tileKey = neighborTile,
            fetchedTimestamp = System.currentTimeMillis()
        )
        dao.insertPlaces(listOf(borderEntity))

        val repository = PlaceRepository(dao, emptyList())
        val result = repository.getNearbyPlaces(lat, lon, radiusMeters = 3000)

        assertEquals(1, result.places.size)
        assertEquals("Border Viewpoint", result.places.first().primaryName)
    }

    @Test
    fun unicodeAccentedNames_deduplicateAndMergeCorrectly() = runBlocking {
        val candidate1 = PlaceCandidate(
            sourceName = "OpenStreetMap",
            sourceId = "osm_101",
            name = "Pastelaria Santo António", // Accent on ó
            latitude = 38.7112,
            longitude = -9.1423,
            category = "eats"
        )
        val candidate2 = PlaceCandidate(
            sourceName = "Wikidata",
            sourceId = "wikidata_Q200",
            name = "Pastelaria Santo Antonio", // Unaccented o
            latitude = 38.7113, // 10 meters away
            longitude = -9.1422,
            category = "eats",
            description = "Traditional bakery in Lisbon."
        )

        val merged = PlaceMerger.mergeCandidates(listOf(candidate1, candidate2))

        assertEquals("Unicode accented and unaccented names at same location must merge", 1, merged.size)
        val place = merged.first()
        assertEquals(2, place.sources.size)
    }

    @Test
    fun multiSourceMerge_retainsAllProviderAttributions() = runBlocking {
        val candidate1 = PlaceCandidate(
            sourceName = "OpenStreetMap",
            sourceId = "osm_500",
            name = "Carmo Convent",
            latitude = 38.7120,
            longitude = -9.1402,
            license = "ODbL"
        )
        val candidate2 = PlaceCandidate(
            sourceName = "Wikidata",
            sourceId = "wikidata_Q83440",
            name = "Carmo Convent",
            latitude = 38.7120,
            longitude = -9.1402,
            license = "CC0 1.0 Universal"
        )

        val fakeSource1 = object : PlaceSource {
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.success(listOf(candidate1))
        }
        val fakeSource2 = object : PlaceSource {
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.success(listOf(candidate2))
        }

        val repository = PlaceRepository(dao, listOf(fakeSource1, fakeSource2))
        val result = repository.getNearbyPlaces(38.7112, -9.1423, 3000)

        assertEquals(1, result.places.size)
        val mergedPlace = result.places.first()
        assertEquals(2, mergedPlace.sources.size)

        val sourceNames = mergedPlace.sources.map { it.sourceName }
        assertTrue(sourceNames.contains("OpenStreetMap"))
        assertTrue(sourceNames.contains("Wikidata"))
    }

    @Test
    fun radiusFiltering_filtersOutPlacesBeyondSearchRadius() = runBlocking {
        val userLat = 38.7112
        val userLng = -9.1423

        // Candidate 1: 500m away (inside 1000m radius)
        val insideCandidate = PlaceCandidate(
            sourceName = "OSM",
            sourceId = "in_1",
            name = "Nearby Spot",
            latitude = 38.7150,
            longitude = -9.1423
        )
        // Candidate 2: ~15km away (outside 1000m radius)
        val distantCandidate = PlaceCandidate(
            sourceName = "OSM",
            sourceId = "out_1",
            name = "Distant Spot",
            latitude = 38.8500,
            longitude = -9.1423
        )

        val fakeSource = object : PlaceSource {
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.success(listOf(insideCandidate, distantCandidate))
        }

        val repository = PlaceRepository(dao, listOf(fakeSource))
        val result = repository.getNearbyPlaces(userLat, userLng, radiusMeters = 1000)

        assertEquals("Distant spot >1000m must be filtered out", 1, result.places.size)
        assertEquals("Nearby Spot", result.places.first().primaryName)
    }

    @Test
    fun getNearbyPlaces_freshCacheExists_returnsFreshCacheWithoutNetworkCall() = runBlocking {
        val now = System.currentTimeMillis()
        val lat = 38.7112
        val lon = -9.1423
        val tileKey = GeohashUtils.encode(lat, lon, precision = 5)

        val freshEntity = CachedPlaceEntity(
            placeId = "cached_1",
            primaryName = "Cached Monument",
            primaryDescription = "Cached desc",
            latitude = lat,
            longitude = lon,
            category = "sights",
            sourcesJson = "[]",
            tileKey = tileKey,
            fetchedTimestamp = now - (1000 * 3600 * 2) // 2 hours old
        )
        dao.insertPlaces(listOf(freshEntity))

        val fakeSource = object : PlaceSource {
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.success(emptyList())
        }

        val repository = PlaceRepository(dao, listOf(fakeSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertFalse("Fresh cache result should not be stale", result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Cached Monument", result.places.first().primaryName)
    }

    @Test
    fun getNearbyPlaces_networkFails_returnsStaleCachedData() = runBlocking {
        val now = System.currentTimeMillis()
        val lat = 38.7112
        val lon = -9.1423
        val tileKey = GeohashUtils.encode(lat, lon, precision = 5)

        val oldTimestamp = now - (10 * 24 * 3600 * 1000L) // 10 days old (stale)
        val staleEntity = CachedPlaceEntity(
            placeId = "stale_1",
            primaryName = "Stale Historical Bakery",
            primaryDescription = "Stale desc",
            latitude = lat,
            longitude = lon,
            category = "eats",
            sourcesJson = "[]",
            tileKey = tileKey,
            fetchedTimestamp = oldTimestamp
        )
        dao.insertPlaces(listOf(staleEntity))

        val failingSource = object : PlaceSource {
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.failure(RuntimeException("Network offline"))
        }

        val repository = PlaceRepository(dao, listOf(failingSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertTrue("Network failure with stale cache must set isStale = true", result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Stale Historical Bakery", result.places.first().primaryName)
    }
}

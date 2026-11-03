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

        var networkCalled = false
        val fakeSource = object : PlaceSource {
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> {
                networkCalled = true
                return Result.success(emptyList())
            }
        }

        val repository = PlaceRepository(dao, listOf(fakeSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertFalse("Fresh cache should not make network calls", networkCalled)
        assertFalse("Fresh cache result should not be stale", result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Cached Monument", result.places.first().primaryName)
    }

    @Test
    fun getNearbyPlaces_cacheMissing_fetchesNetworkMergesAndCachesResult() = runBlocking {
        val now = System.currentTimeMillis()
        val lat = 38.7112
        val lon = -9.1423
        val tileKey = GeohashUtils.encode(lat, lon, precision = 5)

        val candidate = PlaceCandidate(
            sourceName = "Wikidata",
            sourceId = "Q100",
            name = "Live Network Gem",
            latitude = lat,
            longitude = lon,
            category = "history",
            description = "Fetched from network"
        )

        val fakeSource = object : PlaceSource {
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> {
                return Result.success(listOf(candidate))
            }
        }

        val repository = PlaceRepository(dao, listOf(fakeSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertFalse("New network result should not be stale", result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Live Network Gem", result.places.first().primaryName)

        // Verify entity stored in Room database
        val cachedInDb = dao.getPlacesForTiles(listOf(tileKey))
        assertEquals(1, cachedInDb.size)
        assertEquals("Live Network Gem", cachedInDb.first().primaryName)
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
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> {
                return Result.failure(RuntimeException("Network offline"))
            }
        }

        val repository = PlaceRepository(dao, listOf(failingSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertTrue("Network failure with stale cache must set isStale = true", result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Stale Historical Bakery", result.places.first().primaryName)
    }
}

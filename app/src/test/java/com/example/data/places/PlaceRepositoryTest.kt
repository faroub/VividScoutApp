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

        // Insert place on neighbor tile, with provider attribution so the cached record
        // proves provider coverage for the request below.
        val neighborTile = coveringTiles.last()
        val borderEntity = CachedPlaceEntity(
            placeId = "border_1",
            primaryName = "Border Viewpoint",
            primaryDescription = "Viewpoint on neighbor tile",
            latitude = 38.7130,
            longitude = -9.1400,
            category = "sights",
            sourcesJson = """[{"sourceName":"Overpass OSM","sourceId":"osm_border_1","providerId":"overpass_osm","license":"ODbL"}]""",
            tileKey = neighborTile,
            fetchedTimestamp = System.currentTimeMillis()
        )
        dao.insertPlaces(listOf(borderEntity))

        val fakeSource = object : PlaceSource {
            override val sourceId: String = "overpass_osm"
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.success(emptyList())
        }

        val repository = PlaceRepository(dao, listOf(fakeSource))
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
            providerId = "open_trip_map",
            name = "Carmo Convent",
            latitude = 38.7120,
            longitude = -9.1402,
            license = "ODbL"
        )
        val candidate2 = PlaceCandidate(
            sourceName = "Wikidata",
            sourceId = "wikidata_Q83440",
            providerId = "geonames_gazetteer",
            name = "Carmo Convent",
            latitude = 38.7120,
            longitude = -9.1402,
            license = "CC0 1.0 Universal"
        )

        val fakeSource1 = object : PlaceSource {
            override val sourceId: String = "open_trip_map"
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.success(listOf(candidate1))
        }
        val fakeSource2 = object : PlaceSource {
            override val sourceId: String = "geonames_gazetteer"
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
            providerId = "open_trip_map",
            name = "Nearby Spot",
            latitude = 38.7150,
            longitude = -9.1423
        )
        // Candidate 2: ~15km away (outside 1000m radius)
        val distantCandidate = PlaceCandidate(
            sourceName = "OSM",
            sourceId = "out_1",
            providerId = "open_trip_map",
            name = "Distant Spot",
            latitude = 38.8500,
            longitude = -9.1423
        )

        val fakeSource = object : PlaceSource {
            override val sourceId: String = "open_trip_map"
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
            sourcesJson = """[{"sourceName":"OpenTripMap API","sourceId":"otm_cached_1","providerId":"open_trip_map","license":"ODbL"}]""",
            tileKey = tileKey,
            fetchedTimestamp = now - (1000 * 3600 * 2) // 2 hours old
        )
        dao.insertPlaces(listOf(freshEntity))

        val fetchCount = java.util.concurrent.atomic.AtomicInteger(0)
        val fakeSource = object : PlaceSource {
            override val sourceId: String = "open_trip_map"
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> {
                fetchCount.incrementAndGet()
                return Result.success(emptyList())
            }
        }

        val repository = PlaceRepository(dao, listOf(fakeSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertFalse("Fresh cache result should not be stale", result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Cached Monument", result.places.first().primaryName)
        assertEquals("A fully covered provider must not be fetched again", 0, fetchCount.get())
    }

    @Test
    fun getNearbyPlaces_cacheWithoutProviderProvenanceDoesNotCountAsCoverage() = runBlocking {
        // A cached record with no provider attribution cannot prove coverage of any
        // provider: the request must hit the network instead of silently serving the
        // unidentified cached record (the provider-selection bug this guards against).
        val now = System.currentTimeMillis()
        val lat = 38.7112
        val lon = -9.1423
        val tileKey = GeohashUtils.encode(lat, lon, precision = 5)

        val unattributedEntity = CachedPlaceEntity(
            placeId = "cached_legacy",
            primaryName = "Legacy Cached Monument",
            primaryDescription = "Legacy desc",
            latitude = lat,
            longitude = lon,
            category = "sights",
            sourcesJson = "[]", // legacy record without provider provenance
            tileKey = tileKey,
            fetchedTimestamp = now - (1000 * 3600 * 2)
        )
        dao.insertPlaces(listOf(unattributedEntity))

        val fetchCount = java.util.concurrent.atomic.AtomicInteger(0)
        val fakeSource = object : PlaceSource {
            override val sourceId: String = "open_trip_map"
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> {
                fetchCount.incrementAndGet()
                return Result.success(emptyList())
            }
        }

        val repository = PlaceRepository(dao, listOf(fakeSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertEquals("The unattributed cache record must not be served", 0, result.places.size)
        assertEquals("The provider must have been fetched despite a fresh cache", 1, fetchCount.get())
        // The live (here empty) fetch is an honest result, not a stale-cache fallback:
        assertFalse(result.isStale)
        // The cache write purges the unattributed legacy row from the tile.
        assertTrue(dao.getPlacesForTiles(listOf(tileKey)).isEmpty())
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
            sourcesJson = """[{"sourceName":"OpenTripMap API","sourceId":"otm_stale_1","providerId":"open_trip_map","license":"ODbL"}]""",
            tileKey = tileKey,
            fetchedTimestamp = oldTimestamp
        )
        dao.insertPlaces(listOf(staleEntity))

        val failingSource = object : PlaceSource {
            override val sourceId: String = "open_trip_map"
            override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
                Result.failure(RuntimeException("Network offline"))
        }

        val repository = PlaceRepository(dao, listOf(failingSource))
        val result = repository.getNearbyPlaces(lat, lon, currentTimeMs = now)

        assertTrue("Network failure with stale cache must set isStale = true", result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Stale Historical Bakery", result.places.first().primaryName)
    }

    // ------------------------------------------------------------------
    // Provider-selection cache coverage (P5)
    // ------------------------------------------------------------------

    private val testLat = 38.7112
    private val testLon = -9.1423

    private fun cachedEntity(
        placeId: String,
        name: String,
        providerId: String,
        timestamp: Long
    ): CachedPlaceEntity {
        val tileKey = GeohashUtils.encode(testLat, testLon, precision = 5)
        return CachedPlaceEntity(
            placeId = placeId,
            primaryName = name,
            primaryDescription = "desc",
            latitude = testLat,
            longitude = testLon,
            category = "sights",
            sourcesJson = """[{"sourceName":"$providerId","sourceId":"${providerId}_$placeId","providerId":"$providerId"}]""",
            tileKey = tileKey,
            fetchedTimestamp = timestamp
        )
    }

    private fun source(
        providerId: String,
        candidates: List<PlaceCandidate>,
        fetchCounter: java.util.concurrent.atomic.AtomicInteger? = null
    ): PlaceSource = object : PlaceSource {
        override val sourceId: String = providerId
        override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> {
            fetchCounter?.incrementAndGet()
            return Result.success(candidates)
        }
    }

    private fun failingSource(providerId: String): PlaceSource = object : PlaceSource {
        override val sourceId: String = providerId
        override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> =
            Result.failure(RuntimeException("$providerId offline"))
    }

    private fun candidate(providerId: String, name: String): PlaceCandidate = PlaceCandidate(
        sourceName = providerId,
        sourceId = "${providerId}_$name",
        providerId = providerId,
        name = name,
        latitude = testLat,
        longitude = testLon
    )

    @Test
    fun cacheCoverage_cachesOnlyA_userRequestsA_servesCacheWithoutNetwork() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertPlaces(listOf(cachedEntity("p_a1", "Alfama Overlook", "provider_a", now - 60_000)))

        val fetches = java.util.concurrent.atomic.AtomicInteger(0)
        val repository = PlaceRepository(
            dao,
            listOf(source("provider_a", emptyList(), fetches))
        )
        val result = repository.getNearbyPlaces(
            testLat, testLon, currentTimeMs = now, sourceIds = setOf("provider_a")
        )

        assertEquals(1, result.places.size)
        assertEquals("Alfama Overlook", result.places.first().primaryName)
        assertEquals(0, fetches.get())
    }

    @Test
    fun cacheCoverage_cachesOnlyA_userRequestsB_fetchesBandHidesA() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertPlaces(listOf(cachedEntity("p_a1", "Alfama Overlook", "provider_a", now - 60_000)))

        val repository = PlaceRepository(
            dao,
            listOf(
                source("provider_a", listOf(candidate("provider_a", "Alfama Overlook"))),
                source("provider_b", listOf(candidate("provider_b", "Graça Terrace")))
            )
        )
        val result = repository.getNearbyPlaces(
            testLat, testLon, currentTimeMs = now, sourceIds = setOf("provider_b")
        )

        assertEquals("Provider A's cached record must not be served for a provider_b request", 1, result.places.size)
        assertEquals("Graça Terrace", result.places.first().primaryName)
    }

    @Test
    fun cacheCoverage_cachesAandB_userRequestsA_only() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertPlaces(
            listOf(
                cachedEntity("p_a1", "Alfama Overlook", "provider_a", now - 60_000),
                cachedEntity("p_b1", "Graça Terrace", "provider_b", now - 60_000)
            )
        )

        val fetches = java.util.concurrent.atomic.AtomicInteger(0)
        val repository = PlaceRepository(
            dao,
            listOf(
                source("provider_a", emptyList(), fetches),
                source("provider_b", emptyList(), fetches)
            )
        )
        val result = repository.getNearbyPlaces(
            testLat, testLon, currentTimeMs = now, sourceIds = setOf("provider_a")
        )

        assertEquals(1, result.places.size)
        assertEquals("Alfama Overlook", result.places.first().primaryName)
        assertEquals(0, fetches.get())
    }

    @Test
    fun cacheCoverage_cachesAandB_userRequestsAandB() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertPlaces(
            listOf(
                cachedEntity("p_a1", "Alfama Overlook", "provider_a", now - 60_000),
                cachedEntity("p_b1", "Graça Terrace", "provider_b", now - 60_000)
            )
        )

        val fetches = java.util.concurrent.atomic.AtomicInteger(0)
        val repository = PlaceRepository(
            dao,
            listOf(
                source("provider_a", emptyList(), fetches),
                source("provider_b", emptyList(), fetches)
            )
        )
        val result = repository.getNearbyPlaces(
            testLat, testLon, currentTimeMs = now, sourceIds = setOf("provider_a", "provider_b")
        )

        val names = result.places.map { it.primaryName }.sorted()
        assertEquals(listOf("Alfama Overlook", "Graça Terrace"), names)
        assertEquals(0, fetches.get())
    }

    @Test
    fun cacheCoverage_providerBFailsWhileASucceeds_returnsAsResults() = runBlocking {
        val repository = PlaceRepository(
            dao,
            listOf(
                source("provider_a", listOf(candidate("provider_a", "Alfama Overlook"))),
                failingSource("provider_b")
            )
        )
        val result = repository.getNearbyPlaces(
            testLat, testLon, sourceIds = setOf("provider_a", "provider_b")
        )

        assertFalse(result.isStale)
        assertEquals("Provider B's failure must not discard provider A's results", 1, result.places.size)
        assertEquals("Alfama Overlook", result.places.first().primaryName)
    }

    @Test
    fun cacheCoverage_freshCacheMissingRequestedProvider_fetchesOnlyMissingProvider() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertPlaces(listOf(cachedEntity("p_a1", "Alfama Overlook", "provider_a", now - 60_000)))

        val aFetches = java.util.concurrent.atomic.AtomicInteger(0)
        val bFetches = java.util.concurrent.atomic.AtomicInteger(0)
        val repository = PlaceRepository(
            dao,
            listOf(
                source("provider_a", emptyList(), aFetches),
                source("provider_b", listOf(candidate("provider_b", "Graça Terrace")), bFetches)
            )
        )
        val result = repository.getNearbyPlaces(
            testLat, testLon, currentTimeMs = now, sourceIds = setOf("provider_a", "provider_b")
        )

        assertEquals("the missing provider must be fetched", 1, bFetches.get())
        assertEquals("the covered provider must not be refetched", 0, aFetches.get())

        val names = result.places.map { it.primaryName }.sorted()
        assertEquals(listOf("Alfama Overlook", "Graça Terrace"), names)

        // The cache write must preserve provider A's rows even though only B was fetched.
        val cachedAfter = dao.getPlacesForTiles(
            GeohashUtils.getCoveringTiles(testLat, testLon, radiusMeters = 3000)
        )
        assertTrue(cachedAfter.any { it.primaryName == "Alfama Overlook" })
        assertTrue(cachedAfter.any { it.primaryName == "Graça Terrace" })
    }

    @Test
    fun cacheCoverage_offlineWithStaleCache_servesOnlyRequestedProviders() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insertPlaces(
            listOf(
                cachedEntity("stale_a", "Stale A Spot", "provider_a", now - 10 * 24 * 3600 * 1000L),
                cachedEntity("stale_b", "Stale B Spot", "provider_b", now - 10 * 24 * 3600 * 1000L)
            )
        )

        val repository = PlaceRepository(
            dao,
            listOf(failingSource("provider_a"), failingSource("provider_b"))
        )
        val result = repository.getNearbyPlaces(
            testLat, testLon, currentTimeMs = now, sourceIds = setOf("provider_a")
        )

        assertTrue(result.isStale)
        assertEquals(1, result.places.size)
        assertEquals("Stale A Spot", result.places.first().primaryName)
    }
}

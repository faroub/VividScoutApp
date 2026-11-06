package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutDao
import com.example.data.local.ScoutSpotEntity
import com.example.data.network.EuropeanaCulturalService
import com.example.data.network.GeminiSpotService
import com.example.data.network.MunicipalOpenDataService
import com.example.data.network.UnescoHeritageService
import com.example.data.places.GeohashUtils
import com.example.data.places.CachedPlaceEntity
import com.example.data.places.PlaceCandidate
import com.example.data.places.PlaceRepository
import com.example.data.places.PlaceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * Verifies the multi-source discovery orchestration: every requested provider is
 * invoked, disabled providers never are, one provider's success or failure never
 * silences the others, duplicates merge, attribution survives, and the offline
 * tile cache keeps serving places when the network is unavailable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MultiSourceOrchestrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var scoutDao: ScoutDao

    private val userLat = 38.7112
    private val userLng = -9.1423

    private class RecordingPlaceSource(
        override val sourceId: String,
        private val result: Result<List<PlaceCandidate>>
    ) : PlaceSource {
        val invocations = mutableListOf<String>()
        override suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>> {
            invocations.add("fetch")
            return result
        }
    }

    private class FakeUnescoService(
        private val spots: List<ScoutSpotEntity> = emptyList(),
        private val error: Exception? = null
    ) : UnescoHeritageService() {
        val invocations = mutableListOf<String>()
        override suspend fun fetchNearbyUnescoSites(lat: Double, lon: Double, radiusKm: Double): List<ScoutSpotEntity> {
            invocations.add("fetch")
            error?.let { throw it }
            return spots
        }
    }

    private class FakeEuropeanaService(
        private val spots: List<ScoutSpotEntity> = emptyList(),
        private val error: Exception? = null
    ) : EuropeanaCulturalService() {
        val invocations = mutableListOf<String>()
        override suspend fun fetchEuropeanaCulturalSpots(
            locationName: String,
            lat: Double,
            lon: Double,
            radiusMeters: Int
        ): List<ScoutSpotEntity> {
            invocations.add("fetch")
            error?.let { throw it }
            return spots
        }
    }

    private class FakeMunicipalService(
        private val spots: List<ScoutSpotEntity> = emptyList(),
        private val error: Exception? = null
    ) : MunicipalOpenDataService() {
        val invocations = mutableListOf<String>()
        override suspend fun fetchMunicipalDataSpots(
            cityName: String,
            userLat: Double,
            userLng: Double,
            radiusMeters: Int
        ): List<ScoutSpotEntity> {
            invocations.add("fetch")
            error?.let { throw it }
            return spots
        }
    }

    private class FakeGeminiService(
        private val spots: List<ScoutSpotEntity> = emptyList(),
        private val error: Exception? = null
    ) : GeminiSpotService() {
        val invocations = mutableListOf<String>()
        override suspend fun discoverGems(
            userLat: Double,
            userLng: Double,
            category: String,
            radiusMeters: Int,
            customQuery: String
        ): Result<List<ScoutSpotEntity>> {
            invocations.add("fetch")
            error?.let { return Result.failure(it) }
            return Result.success(spots)
        }
    }

    private fun makeSpot(id: String, title: String): ScoutSpotEntity = ScoutSpotEntity(
        id = id,
        title = title,
        description = "Test spot $title",
        category = "sights",
        distance = "100m away",
        walkMinutes = 2,
        rating = null,
        imageUrl = "",
        badgeLabel = "TEST",
        statusBadge = "TEST",
        tagsRaw = "",
        proTip = null,
        upvotes = null,
        isSaved = false,
        isOfflineDraft = false,
        isDiscovered = false,
        locationName = "Lisbon",
        latitude = userLat + 0.001,
        longitude = userLng + 0.001,
        timestamp = System.currentTimeMillis()
    )

    private fun makeRepo(
        placeSources: List<PlaceSource> = emptyList(),
        unesco: FakeUnescoService = FakeUnescoService(),
        europeana: FakeEuropeanaService = FakeEuropeanaService(),
        municipal: FakeMunicipalService = FakeMunicipalService(),
        gemini: FakeGeminiService = FakeGeminiService()
    ): Pair<ScoutGemRepository, FakeProviders> {
        val cachedPlaceDao = db.cachedPlaceDao()
        val placeRepository = PlaceRepository(cachedPlaceDao, placeSources)
        val repo = ScoutGemRepository(
            scoutDao = scoutDao,
            discoveredGemDao = db.discoveredGemDao(),
            recentSearchDao = db.recentSearchDao(),
            placeRepository = placeRepository,
            unescoService = unesco,
            europeanaService = europeana,
            municipalService = municipal,
            geminiSpotService = gemini
        )
        return repo to FakeProviders(placeSources.mapNotNull { it as? RecordingPlaceSource }, unesco, europeana, municipal, gemini)
    }

    private data class FakeProviders(
        val placeSources: List<RecordingPlaceSource>,
        val unesco: FakeUnescoService,
        val europeana: FakeEuropeanaService,
        val municipal: FakeMunicipalService,
        val gemini: FakeGeminiService
    ) {
        fun assertAllInvoked() {
            placeSources.forEach { assertTrue("pipeline source ${it.sourceId} must be invoked", it.invocations.isNotEmpty()) }
            assertTrue("UNESCO provider must be invoked", unesco.invocations.isNotEmpty())
            assertTrue("Europeana provider must be invoked", europeana.invocations.isNotEmpty())
            assertTrue("Municipal provider must be invoked", municipal.invocations.isNotEmpty())
            assertTrue("Gemini provider must be invoked", gemini.invocations.isNotEmpty())
        }

        fun assertNoneInvoked() {
            placeSources.forEach { assertFalse("pipeline source ${it.sourceId} must NOT be invoked", it.invocations.isNotEmpty()) }
            assertFalse("UNESCO provider must NOT be invoked", unesco.invocations.isNotEmpty())
            assertFalse("Europeana provider must NOT be invoked", europeana.invocations.isNotEmpty())
            assertFalse("Municipal provider must NOT be invoked", municipal.invocations.isNotEmpty())
            assertFalse("Gemini provider must NOT be invoked", gemini.invocations.isNotEmpty())
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scoutDao = db.scoutDao()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun allRequestedProvidersAreInvoked() = runTest {
        val (repo, providers) = makeRepo(
            placeSources = listOf(RecordingPlaceSource("open_trip_map", Result.success(emptyList())))
        )

        val result = repo.syncMultiSourceGems(
            userLat, userLng,
            sourceIds = listOf("open_trip_map", "unesco_world_heritage", "europeana_cultural", "municipal_open_data", "firebase_gemini_ai")
        )

        assertTrue(result.isSuccess)
        providers.assertAllInvoked()
    }

    @Test
    fun disabledProvidersAreNeverInvoked() = runTest {
        val (repo, providers) = makeRepo(
            placeSources = listOf(
                RecordingPlaceSource("open_trip_map", Result.success(emptyList())),
                RecordingPlaceSource("geonames_gazetteer", Result.success(emptyList()))
            )
        )

        val result = repo.syncMultiSourceGems(
            userLat, userLng,
            sourceIds = listOf("unesco_world_heritage")
        )

        assertTrue(result.isSuccess)
        assertTrue("UNESCO provider must be invoked", providers.unesco.invocations.isNotEmpty())
        providers.placeSources.forEach {
            assertFalse("pipeline source ${it.sourceId} must NOT be invoked when not requested", it.invocations.isNotEmpty())
        }
        assertFalse("Europeana must NOT be invoked when not requested", providers.europeana.invocations.isNotEmpty())
        assertFalse("Gemini must NOT be invoked when not requested", providers.gemini.invocations.isNotEmpty())
    }

    @Test
    fun positivePipelineResultDoesNotStopOtherProviders() = runTest {
        val pipelineCandidate = PlaceCandidate(
            sourceName = "OpenTripMap API",
            sourceId = "otm_1",
            name = "Pipeline Landmark",
            latitude = userLat + 0.001,
            longitude = userLng + 0.001,
            category = "sights",
            description = "From the cached pipeline."
        )
        val unescoSpot = makeSpot("unesco_1", "UNESCO Landmark").copy(
            latitude = userLat + 0.002,
            longitude = userLng + 0.002
        )

        val (repo, providers) = makeRepo(
            placeSources = listOf(RecordingPlaceSource("open_trip_map", Result.success(listOf(pipelineCandidate)))),
            unesco = FakeUnescoService(listOf(unescoSpot))
        )

        val result = repo.syncMultiSourceGems(
            userLat, userLng,
            sourceIds = listOf("open_trip_map", "unesco_world_heritage", "europeana_cultural", "municipal_open_data", "firebase_gemini_ai")
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        providers.assertAllInvoked()
        val titles = scoutDao.getAllSpotsList().map { it.title }
        assertTrue("Pipeline result must reach Room", titles.contains("Pipeline Landmark"))
        assertTrue("UNESCO result must reach Room", titles.contains("UNESCO Landmark"))
    }

    @Test
    fun providerFailuresDoNotDiscardOtherResults() = runTest {
        val unescoSpot = makeSpot("unesco_1", "UNESCO Landmark")
        val (repo, _) = makeRepo(
            unesco = FakeUnescoService(listOf(unescoSpot)),
            europeana = FakeEuropeanaService(error = IllegalStateException("Europeana offline")),
            municipal = FakeMunicipalService(error = IllegalStateException("Municipal API down")),
            gemini = FakeGeminiService(error = IllegalStateException("Gemini unavailable"))
        )

        val result = repo.syncMultiSourceGems(
            userLat, userLng,
            sourceIds = listOf("unesco_world_heritage", "europeana_cultural", "municipal_open_data", "firebase_gemini_ai")
        )

        // The overall sync succeeds; only the failing providers contributed zero.
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
        assertEquals(listOf("UNESCO Landmark"), scoutDao.getAllSpotsList().map { it.title })
    }

    @Test
    fun duplicatePlacesMergeAcrossProviders() = runTest {
        val duplicateEntity = makeSpot("dupe_1", "Miradouro Duplo")
        val (repo, _) = makeRepo(
            placeSources = listOf(
                RecordingPlaceSource(
                    "open_trip_map",
                    Result.success(
                        listOf(
                            PlaceCandidate(
                                sourceName = "OpenTripMap API",
                                sourceId = "otm_2",
                                name = "Miradouro Duplo",
                                latitude = duplicateEntity.latitude,
                                longitude = duplicateEntity.longitude,
                                category = "sights",
                                description = "Same place, found via pipeline."
                            )
                        )
                    )
                )
            ),
            unesco = FakeUnescoService(listOf(duplicateEntity))
        )

        val result = repo.syncMultiSourceGems(
            userLat, userLng,
            sourceIds = listOf("open_trip_map", "unesco_world_heritage")
        )

        assertTrue(result.isSuccess)
        assertEquals(1, scoutDao.getAllSpotsList().size)
    }

    @Test
    fun sourceAttributionSurvivesMerging() = runTest {
        val (repo, _) = makeRepo(
            placeSources = listOf(
                RecordingPlaceSource(
                    "open_trip_map",
                    Result.success(
                        listOf(
                            PlaceCandidate(
                                sourceName = "OpenTripMap API",
                                sourceId = "otm_3",
                                name = "Attributed Landmark",
                                latitude = userLat,
                                longitude = userLng,
                                category = "sights",
                                description = "Attribution must survive."
                            )
                        )
                    )
                )
            )
        )

        repo.syncMultiSourceGems(userLat, userLng, sourceIds = listOf("open_trip_map"))

        val saved = scoutDao.getAllSpotsList().first { it.title == "Attributed Landmark" }
        assertTrue("Source name must survive merging", saved.tagsRaw.contains("OpenTripMap API"))
        assertTrue("Source id must survive merging", saved.tagsRaw.contains("otm_3"))
    }

    @Test
    fun offlineCachedPlacesRemainAvailableWhenNetworkFails() = runTest {
        val cachedPlaceDao = db.cachedPlaceDao()

        // Seed the geohash tile cache exactly like PlaceRepository does, then let it
        // go stale while every network source fails.
        val centerTile = GeohashUtils.encode(userLat, userLng, precision = 5)
        val cached = CachedPlaceEntity(
            placeId = "merged_cached_1",
            primaryName = "Cached Viewpoint",
            primaryDescription = "Persisted in the offline tile cache.",
            latitude = userLat + 0.001,
            longitude = userLng + 0.001,
            category = "sights",
            imageUrl = null,
            sourceUrl = null,
            license = "ODbL",
            languageCode = "en",
            sourcesJson = "[]",
            tileKey = centerTile,
            fetchedTimestamp = System.currentTimeMillis() - 8 * 24 * 3600 * 1000L // 8 days old
        )
        cachedPlaceDao.replaceTilePlaces(centerTile, listOf(cached))

        val (repo, providers) = makeRepo(
            placeSources = listOf(
                RecordingPlaceSource("open_trip_map", Result.failure(IllegalStateException("offline")))
            )
        )

        val result = repo.syncMultiSourceGems(userLat, userLng, sourceIds = listOf("open_trip_map"))

        assertTrue(result.isSuccess)
        assertEquals("Stale cache places must still be served and persisted", 1, result.getOrThrow())
        assertEquals("Cached Viewpoint", scoutDao.getAllSpotsList().first().title)
        assertTrue(providers.placeSources.first().invocations.isNotEmpty())
    }
}

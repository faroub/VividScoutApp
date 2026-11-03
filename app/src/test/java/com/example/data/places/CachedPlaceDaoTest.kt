package com.example.data.places

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CachedPlaceDaoTest {

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
    fun insertAndGetPlacesForTiles_storesAndRetrievesEntitiesCorrectly() = runBlocking {
        val tileKey = GeohashUtils.encode(38.7112, -9.1423, precision = 5)
        val entity = CachedPlaceEntity(
            placeId = "place_1",
            primaryName = "Castelo de São Jorge",
            primaryDescription = "Historic Moorish castle complex in Lisbon.",
            latitude = 38.7139,
            longitude = -9.1334,
            category = "history",
            sourcesJson = "[]",
            tileKey = tileKey,
            fetchedTimestamp = System.currentTimeMillis()
        )

        dao.insertPlaces(listOf(entity))

        val fetched = dao.getPlacesForTiles(listOf(tileKey))
        assertEquals(1, fetched.size)
        assertEquals("Castelo de São Jorge", fetched.first().primaryName)
        assertEquals(tileKey, fetched.first().tileKey)
    }

    @Test
    fun deleteExpiredPlaces_purgesPlacesOlderThanThreshold() = runBlocking {
        val tileKey = GeohashUtils.encode(38.7112, -9.1423, precision = 5)
        val now = System.currentTimeMillis()
        val oldTimestamp = now - (8 * 24 * 3600 * 1000L) // 8 days old

        val freshEntity = CachedPlaceEntity(
            placeId = "place_fresh",
            primaryName = "Fresh Spot",
            primaryDescription = "Fresh description",
            latitude = 38.7112,
            longitude = -9.1423,
            category = "sights",
            sourcesJson = "[]",
            tileKey = tileKey,
            fetchedTimestamp = now
        )

        val expiredEntity = CachedPlaceEntity(
            placeId = "place_expired",
            primaryName = "Expired Spot",
            primaryDescription = "Expired description",
            latitude = 38.7112,
            longitude = -9.1423,
            category = "sights",
            sourcesJson = "[]",
            tileKey = tileKey,
            fetchedTimestamp = oldTimestamp
        )

        dao.insertPlaces(listOf(freshEntity, expiredEntity))
        assertEquals(2, dao.getPlaceCount())

        val expirationThreshold = now - (7 * 24 * 3600 * 1000L)
        dao.deleteExpiredPlaces(expirationThreshold)

        assertEquals(1, dao.getPlaceCount())
        val remaining = dao.getPlacesForTiles(listOf(tileKey))
        assertEquals("Fresh Spot", remaining.first().primaryName)
    }
}

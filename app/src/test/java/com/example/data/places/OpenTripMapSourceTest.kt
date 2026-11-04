package com.example.data.places

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OpenTripMapSourceTest {

    @Test
    fun fetchNear_successfulNormalizedResults_returnsMappedPlaceCandidates() = runBlocking {
        val rawMap = listOf(
            mapOf(
                "sourceName" to "OpenTripMap API",
                "sourceId" to "otm_W12345",
                "name" to "Carmo Convent Ruins",
                "latitude" to 38.7120,
                "longitude" to -9.1402,
                "category" to "history",
                "description" to "Gothic church ruins in Lisbon.",
                "imageUrl" to "https://images.unsplash.com/photo-1",
                "sourceUrl" to "https://opentripmap.com/en/card/W12345",
                "license" to "ODbL",
                "languageCode" to "en"
            )
        )

        val source = OpenTripMapSource(callableProvider = { rawMap })
        val result = source.fetchNear(38.7112, -9.1423, 3000)

        assertTrue(result.isSuccess)
        val candidates = result.getOrThrow()
        assertEquals(1, candidates.size)

        val item = candidates.first()
        assertEquals("OpenTripMap API", item.sourceName)
        assertEquals("otm_W12345", item.sourceId)
        assertEquals("Carmo Convent Ruins", item.name)
        assertEquals(38.7120, item.latitude, 0.0001)
        assertEquals(-9.1402, item.longitude, 0.0001)
        assertEquals("history", item.category)
        assertEquals("ODbL", item.license)
    }

    @Test
    fun fetchNear_invalidCoordinates_returnsEmptyListWithoutCallingBackend() = runBlocking {
        var called = false
        val source = OpenTripMapSource(callableProvider = {
            called = true
            emptyList()
        })

        val resultNaN = source.fetchNear(Double.NaN, -9.1423, 3000)
        assertTrue(resultNaN.isSuccess)
        assertTrue(resultNaN.getOrThrow().isEmpty())
        assertFalse("Backend should not be called for NaN coordinates", called)

        val resultOutOfBounds = source.fetchNear(100.0, -9.1423, 3000)
        assertTrue(resultOutOfBounds.isSuccess)
        assertTrue(resultOutOfBounds.getOrThrow().isEmpty())
    }

    @Test
    fun fetchNear_invalidRadiusMeters_returnsEmptyListWithoutCallingBackend() = runBlocking {
        var called = false
        val source = OpenTripMapSource(callableProvider = {
            called = true
            emptyList()
        })

        val resultNegative = source.fetchNear(38.7112, -9.1423, -500)
        assertTrue(resultNegative.isSuccess)
        assertTrue(resultNegative.getOrThrow().isEmpty())

        val resultExcessive = source.fetchNear(38.7112, -9.1423, 100000)
        assertTrue(resultExcessive.isSuccess)
        assertTrue(resultExcessive.getOrThrow().isEmpty())
        assertFalse("Backend should not be called for invalid radii", called)
    }

    @Test
    fun fetchNear_backendError_handlesExceptionGracefully() = runBlocking {
        val source = OpenTripMapSource(callableProvider = {
            throw IllegalStateException("Simulated backend exception")
        })

        val result = source.fetchNear(38.7112, -9.1423, 3000)
        assertTrue("Backend exceptions must be handled gracefully returning success with empty list", result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}

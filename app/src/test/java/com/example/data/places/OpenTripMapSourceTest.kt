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

    private fun backendResponse(
        places: List<Map<String, Any?>> = emptyList(),
        providerStatus: String = "ok"
    ): Map<String, Any?> = mapOf(
        "places" to places,
        "providerStatus" to mapOf("opentripmap" to providerStatus)
    )

    @Test
    fun fetchNear_successfulNormalizedResults_returnsMappedPlaceCandidates() = runBlocking {
        val rawMap = backendResponse(
            listOf(
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
    fun fetchNear_successfulEmptyAnswer_staysASuccessfulEmptyResult() = runBlocking {
        // A provider that ANSWERED with zero places is a success: the absence of
        // places near the user is real data, not a provider failure.
        val source = OpenTripMapSource(callableProvider = { backendResponse() })
        val result = source.fetchNear(38.7112, -9.1423, 3000)

        assertTrue("An empty successful answer must not become a failure", result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun fetchNear_invalidCoordinates_returnsEmptyListWithoutCallingBackend() = runBlocking {
        var called = false
        val source = OpenTripMapSource(callableProvider = {
            called = true
            backendResponse()
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
            backendResponse()
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
    fun fetchNear_providerTransientFailure_isATypedTemporaryFailureNotAnEmptySuccess() = runBlocking {
        val source = OpenTripMapSource(callableProvider = { backendResponse(providerStatus = "transient") })
        val result = source.fetchNear(38.7112, -9.1423, 3000)

        assertTrue("A provider outage must surface as failure", result.isFailure)
        assertTrue(
            "The failure must be classified as temporary",
            result.exceptionOrNull() is PlaceSourceFailure.TemporaryFailure
        )
    }

    @Test
    fun fetchNear_providerAuthConfigFailure_isATypedAuthConfigurationFailure() = runBlocking {
        val source = OpenTripMapSource(callableProvider = { backendResponse(providerStatus = "auth-config") })
        val result = source.fetchNear(38.7112, -9.1423, 3000)

        assertTrue(result.isFailure)
        assertTrue(
            "Missing or rejected credentials are a configuration failure, not retryable noise",
            result.exceptionOrNull() is PlaceSourceFailure.AuthConfigFailure
        )
    }

    @Test
    fun fetchNear_providerRateLimit_isATypedRateLimitedFailure() = runBlocking {
        val source = OpenTripMapSource(callableProvider = { backendResponse(providerStatus = "rate-limit") })
        val result = source.fetchNear(38.7112, -9.1423, 3000)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PlaceSourceFailure.RateLimited)
    }

    @Test
    fun fetchNear_backendError_isATemporaryFailureNotAnEmptySuccess() = runBlocking {
        val source = OpenTripMapSource(callableProvider = {
            throw IllegalStateException("Simulated backend exception")
        })

        val result = source.fetchNear(38.7112, -9.1423, 3000)
        assertTrue("Backend exceptions must surface as failures, not empty successes", result.isFailure)
        assertTrue(
            "Unclassified network exceptions default to temporary",
            result.exceptionOrNull() is PlaceSourceFailure.TemporaryFailure
        )
    }
}

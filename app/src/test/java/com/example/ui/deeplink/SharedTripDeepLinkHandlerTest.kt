package com.example.ui.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.sync.SharedTrip
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedTripDeepLinkHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun sharedTripIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun handleIntent_fetchesAndShowsTheSharedTripOncePerIdenticalUri() = runTest {
        val fetchedTripIds = mutableListOf<String>()
        val handler = SharedTripDeepLinkHandler(
            context = context,
            coroutineScope = this,
            fetchSharedTrip = { tripId ->
                fetchedTripIds += tripId
                Result.success(SharedTrip(id = tripId, title = "Test Trip"))
            }
        )

        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/abc123"))
        advanceUntilIdle()

        assertEquals(listOf("abc123"), fetchedTripIds)
        assertEquals("abc123", handler.activeSharedTrip?.id)
        assertNull(handler.activeSharedTripError)
        assertTrue(handler.showSharedTripDialog)

        // Re-delivering the identical intent (recomposition, duplicate effect key, or a
        // repeated tap of the same link): no second fetch, no state churn.
        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/abc123"))
        advanceUntilIdle()

        assertEquals("The same URI must not be fetched twice", 1, fetchedTripIds.size)
    }

    @Test
    fun handleIntent_processesAGenuinelyDifferentUri() = runTest {
        val fetchedTripIds = mutableListOf<String>()
        val handler = SharedTripDeepLinkHandler(
            context = context,
            coroutineScope = this,
            fetchSharedTrip = { tripId ->
                fetchedTripIds += tripId
                Result.success(SharedTrip(id = tripId))
            }
        )

        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/one"))
        advanceUntilIdle()
        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/two"))
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), fetchedTripIds)
        assertEquals("two", handler.activeSharedTrip?.id)
    }

    @Test
    fun handleIntent_ignoresIntentsWithoutAParsableSharedTripUri() = runTest {
        val fetchedTripIds = mutableListOf<String>()
        val handler = SharedTripDeepLinkHandler(
            context = context,
            coroutineScope = this,
            fetchSharedTrip = { tripId ->
                fetchedTripIds += tripId
                Result.success(SharedTrip(id = tripId))
            }
        )

        handler.handleIntent(null)
        handler.handleIntent(Intent(Intent.ACTION_MAIN))
        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/"))
        advanceUntilIdle()

        assertTrue(
            "No fetch may run for null intents, intents without trip data, or blank trip ids",
            fetchedTripIds.isEmpty()
        )
        assertFalse(handler.showSharedTripDialog)
    }

    @Test
    fun handleIntent_failureShowsTheErrorStateWithoutATrip() = runTest {
        val handler = SharedTripDeepLinkHandler(
            context = context,
            coroutineScope = this,
            fetchSharedTrip = {
                Result.failure(IllegalStateException("Shared trip not found or expired."))
            }
        )

        handler.handleIntent(sharedTripIntent("vividscout://trip/missing"))
        advanceUntilIdle()

        assertNull(handler.activeSharedTrip)
        assertEquals("Shared trip not found or expired.", handler.activeSharedTripError)
        assertTrue(handler.showSharedTripDialog)
    }
}

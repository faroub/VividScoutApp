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

        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/abc12345"))
        advanceUntilIdle()

        assertEquals(listOf("abc12345"), fetchedTripIds)
        assertEquals("abc12345", handler.activeSharedTrip?.id)
        assertNull(handler.activeSharedTripError)
        assertTrue(handler.showSharedTripDialog)

        // Re-delivering the identical intent (recomposition, duplicate effect key, or a
        // repeated tap of the same link): no second fetch, no state churn.
        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/abc12345"))
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

        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/trip-id-one"))
        advanceUntilIdle()
        handler.handleIntent(sharedTripIntent("https://vividscout.app/trip/trip-id-two"))
        advanceUntilIdle()

        assertEquals(listOf("trip-id-one", "trip-id-two"), fetchedTripIds)
        assertEquals("trip-id-two", handler.activeSharedTrip?.id)
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

        handler.handleIntent(sharedTripIntent("vividscout://trip/abcdefgh"))
        advanceUntilIdle()

        assertNull(handler.activeSharedTrip)
        assertEquals(SHARED_TRIP_DEFAULT_ERROR, handler.activeSharedTripError)
        assertTrue(handler.showSharedTripDialog)
    }

    @Test
    fun handleIntent_failureNeverShowsTheRawExceptionMessage() = runTest {
        // The fetch may fail with implementation-specific messages (network error,
        // permissions error); the UI must only ever show the generic message.
        val handler = SharedTripDeepLinkHandler(
            context = context,
            coroutineScope = this,
            fetchSharedTrip = {
                Result.failure(com.google.firebase.firestore.FirebaseFirestoreException(
                    "firestore: PERMISSION_DENIED: missing or insufficient permissions",
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                ))
            }
        )

        handler.handleIntent(sharedTripIntent("vividscout://trip/abcdefgh"))
        advanceUntilIdle()

        assertEquals(SHARED_TRIP_DEFAULT_ERROR, handler.activeSharedTripError)
        assertFalse(handler.activeSharedTripError!!.contains("PERMISSION_DENIED"))
    }

    @Test
    fun handleIntent_neverFetchesSlashContainingOversizedOrBlankIds() = runTest {
        val fetchedTripIds = mutableListOf<String>()
        val handler = SharedTripDeepLinkHandler(
            context = context,
            coroutineScope = this,
            fetchSharedTrip = { tripId ->
                fetchedTripIds.add(tripId)
                Result.success(SharedTrip(id = tripId, title = "t", spots = emptyList()))
            }
        )

        // Slash-containing ids (collection/path probing) and oversized ids are rejected
        // by the parser, so no fetch may ever run for them.
        handler.handleIntent(sharedTripIntent("vividscout://trip/abcd/../../users/secret"))
        handler.handleIntent(sharedTripIntent("vividscout://trip/" + "a".repeat(65)))
        handler.handleIntent(sharedTripIntent("vividscout://trip/"))
        handler.handleIntent(sharedTripIntent("vividscout://trip?id="))
        advanceUntilIdle()

        assertTrue("No fetch may run for invalid ids: $fetchedTripIds", fetchedTripIds.isEmpty())
        assertFalse(handler.showSharedTripDialog)
    }
}

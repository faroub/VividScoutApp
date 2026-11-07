package com.example.ui.deeplink

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.sync.SharedTrip
import com.example.data.sync.SharedTripManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Error message shown when a deep-linked shared itinerary cannot be opened. */
const val SHARED_TRIP_DEFAULT_ERROR = "Shared itinerary not found or expired."

/**
 * Owns the state and fetching of a deep-linked shared itinerary so the activity does
 * not mix deep-link plumbing into its own composition.
 *
 * [handleIntent] is safe to call repeatedly: it only acts on intents carrying a
 * shared-trip URI, and [SharedTripManager] treats re-fetches idempotently.
 */
class SharedTripDeepLinkHandler(private val context: Context, private val coroutineScope: CoroutineScope) {

    var activeSharedTrip: SharedTrip? by mutableStateOf(null)
        private set

    var activeSharedTripError: String? by mutableStateOf(null)
        private set

    var showSharedTripDialog: Boolean by mutableStateOf(false)
        private set

    fun handleIntent(deepIntent: Intent?) {
        val dataUri = deepIntent?.dataString
        val tripId = SharedTripManager.parseTripIdFromUri(dataUri)
        if (tripId.isNullOrBlank()) return

        coroutineScope.launch {
            val fetchResult = SharedTripManager.fetchSharedTripFromFirestore(context, tripId)
            if (fetchResult.isSuccess) {
                activeSharedTrip = fetchResult.getOrNull()
                activeSharedTripError = null
            } else {
                activeSharedTrip = null
                activeSharedTripError = fetchResult.exceptionOrNull()?.message ?: SHARED_TRIP_DEFAULT_ERROR
            }
            showSharedTripDialog = true
        }
    }

    fun dismiss() {
        showSharedTripDialog = false
    }
}

@Composable
fun rememberSharedTripDeepLinkHandler(
    context: Context,
    coroutineScope: CoroutineScope
): SharedTripDeepLinkHandler = remember(context, coroutineScope) {
    SharedTripDeepLinkHandler(context, coroutineScope)
}

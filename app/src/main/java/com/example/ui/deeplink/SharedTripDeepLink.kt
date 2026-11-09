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
 * shared-trip URI, and it handles each distinct URI exactly once — a re-delivered
 * identical intent (recomposition, duplicate LaunchedEffect key) does not re-fetch
 * or re-show the same itinerary. Activity-level rememberSaveable in VividScoutApp
 * covers the additional case where the launching intent is re-delivered to a fresh
 * composition after rotation or process recreation.
 *
 * [fetchSharedTrip] is injectable so the dedup behavior is unit-testable without
 * Firestore; production callers use the default, which goes through
 * [SharedTripManager].
 */
class SharedTripDeepLinkHandler(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val fetchSharedTrip: suspend (String) -> Result<SharedTrip> = { tripId ->
        SharedTripManager.fetchSharedTripFromFirestore(context, tripId)
    }
) {

    var activeSharedTrip: SharedTrip? by mutableStateOf(null)
        private set

    var activeSharedTripError: String? by mutableStateOf(null)
        private set

    var showSharedTripDialog: Boolean by mutableStateOf(false)
        private set

    // The last shared-trip URI this handler acted on. Without it, every re-delivery
    // of the same intent would re-fetch the trip and re-open the dialog.
    private var lastHandledUri: String? = null

    fun handleIntent(deepIntent: Intent?) {
        val dataUri = deepIntent?.dataString
        val tripId = SharedTripManager.parseTripIdFromUri(dataUri)
        if (tripId.isNullOrBlank()) return

        // Only a genuinely new URI starts a fetch: rotation and recomposition re-deliver
        // the same launching intent, and a second tap of the same link while its
        // itinerary is already shown must not re-open it.
        if (dataUri == lastHandledUri) return
        lastHandledUri = dataUri

        coroutineScope.launch {
            val fetchResult = fetchSharedTrip(tripId)
            if (fetchResult.isSuccess) {
                activeSharedTrip = fetchResult.getOrNull()
                activeSharedTripError = null
            } else {
                // Generic failure message only: the raw exception message can leak
                // implementation detail (network vs. expired vs. missing document) and
                // must never reach the UI.
                activeSharedTrip = null
                activeSharedTripError = SHARED_TRIP_DEFAULT_ERROR
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

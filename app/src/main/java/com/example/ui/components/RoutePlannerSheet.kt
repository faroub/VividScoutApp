package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ScoutSpotEntity
import com.example.data.offline.GuidedWalkMood
import com.example.data.offline.GuidedWalkPlannerEngine
import com.example.data.sync.SharedTripManager
import com.example.engine.OfflineWalkingTrail
import com.example.ui.theme.BrandCoral
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    origin: ScoutSpotEntity?,
    destination: ScoutSpotEntity?,
    allSpots: List<ScoutSpotEntity>,
    plannedRoute: OfflineWalkingTrail?,
    isCalculating: Boolean,
    onSelectOrigin: (ScoutSpotEntity?) -> Unit,
    onSelectDestination: (ScoutSpotEntity) -> Unit,
    onSwapEndpoints: () -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isSelectingOrigin by remember { mutableStateOf(false) }
    var isSelectingDest by remember { mutableStateOf(false) }
    var areStepsExpanded by remember { mutableStateOf(true) }
    var isPublishingSharedTrip by remember { mutableStateOf(false) }
    var sharePublishError by remember { mutableStateOf<String?>(null) }
    // Idempotency key for publishing: stable across retries of one share intent so a
    // retry after a lost response replays the already-published trip server-side instead
    // of publishing a second one. Regenerated only after a successful publish.
    var shareRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF101726),
        contentColor = Color.White,
        modifier = modifier.testTag("route_planner_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar
            RoutePlannerHeader(onDismiss = onDismiss)

            Spacer(modifier = Modifier.height(16.dp))

            // Origin & Destination Selector Card
            RouteEndpointSelectorCard(
                origin = origin,
                destination = destination,
                onPickOrigin = { isSelectingOrigin = true },
                onPickDestination = { isSelectingDest = true },
                onSwapEndpoints = onSwapEndpoints
            )

            Spacer(modifier = Modifier.height(12.dp))

            var selectedDurationMinutes by remember { mutableStateOf(60) } // 30, 60, 120
            var selectedMood by remember { mutableStateOf(GuidedWalkMood.SCENIC) } // SCENIC, HISTORIC, QUIET

            // Step 2: Duration & Mood Selection Card
            GuidedWalkOptionsCard(
                selectedDurationMinutes = selectedDurationMinutes,
                selectedMood = selectedMood,
                onDurationSelected = { selectedDurationMinutes = it },
                onMoodSelected = { selectedMood = it }
            )

            val startLat = origin?.latitude ?: 38.7112
            val startLng = origin?.longitude ?: -9.1423
            val startTitle = origin?.name ?: "Current Location"

            val guidedWalkPlan = remember(startLat, startLng, startTitle, selectedDurationMinutes, selectedMood, allSpots) {
                GuidedWalkPlannerEngine.planGuidedWalk(
                    originLat = startLat,
                    originLng = startLng,
                    startTitle = startTitle,
                    targetDurationMinutes = selectedDurationMinutes,
                    mood = selectedMood,
                    availableSpots = allSpots,
                    cityName = origin?.locationName
                )
            }

            if (guidedWalkPlan.waypoints.isNotEmpty() && plannedRoute == null) {
                Spacer(modifier = Modifier.height(12.dp))
                GuidedWalkPlanCard(
                    guidedWalkPlan = guidedWalkPlan,
                    isPublishing = isPublishingSharedTrip,
                    publishError = sharePublishError,
                    onPublish = {
                        coroutineScope.launch {
                            isPublishingSharedTrip = true
                            sharePublishError = null
                            val createdTrip = SharedTripManager.createSharedTrip(
                                title = "${guidedWalkPlan.startTitle} ${guidedWalkPlan.mood.title} Walk",
                                spots = guidedWalkPlan.waypoints
                            )
                            val publishResult = SharedTripManager.publishSharedTripToFirestore(
                                context = context,
                                trip = createdTrip,
                                requestId = shareRequestId
                            )
                            isPublishingSharedTrip = false
                            if (publishResult.isSuccess) {
                                // Only the server-generated link resolves to the
                                // real Firestore document; never the local placeholder.
                                val published = publishResult.getOrThrow()
                                shareRequestId = UUID.randomUUID().toString()
                                SharedTripManager.shareTripNativeIntent(
                                    context = context,
                                    shareableLink = published.shareableLink,
                                    tripTitle = createdTrip.title
                                )
                            } else {
                                sharePublishError = "Failed to publish trip: ${publishResult.exceptionOrNull()?.message ?: "Check network connection."}"
                            }
                        }
                    }
                )
            }

            // Step 3: Show Empty State if guidedWalkPlan is empty and no custom destination selected
            if (guidedWalkPlan.waypoints.isEmpty() && destination == null && plannedRoute == null) {
                Spacer(modifier = Modifier.height(12.dp))
                GuidedWalkEmptyStateCard(
                    moodTitle = selectedMood.title,
                    durationMinutes = selectedDurationMinutes
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calculating State
            if (isCalculating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BrandCoral,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Calculating offline A* walking path...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                }
            } else if (plannedRoute != null) {
                RouteSummarySection(
                    plannedRoute = plannedRoute,
                    areStepsExpanded = areStepsExpanded,
                    onToggleStepsExpanded = { areStepsExpanded = !areStepsExpanded },
                    onClearRoute = onClearRoute,
                    onDismiss = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    // Spot Selector Dialogs
    if (isSelectingOrigin) {
        SpotPickerModal(
            title = "Choose Starting Point",
            includeCurrentGps = true,
            spots = allSpots,
            onSelect = { selected ->
                onSelectOrigin(selected)
                isSelectingOrigin = false
            },
            onDismiss = { isSelectingOrigin = false }
        )
    }

    if (isSelectingDest) {
        SpotPickerModal(
            title = "Choose Destination Gem",
            includeCurrentGps = false,
            spots = allSpots,
            onSelect = { selected ->
                if (selected != null) {
                    onSelectDestination(selected)
                }
                isSelectingDest = false
            },
            onDismiss = { isSelectingDest = false }
        )
    }
}

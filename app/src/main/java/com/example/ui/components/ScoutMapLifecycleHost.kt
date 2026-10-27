package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.ScoutSpotEntity
import com.example.engine.ScoutVectorEngine
import com.example.ui.viewmodel.ScoutMapViewModel

/**
 * Dedicated Lifecycle-Aware Host for the Scout Vector Map Canvas.
 * Binds lifecycle events (ON_START, ON_STOP, ON_DESTROY) to ScoutVectorEngine
 * and manages unidirectional state updates via ScoutMapViewModel.
 */
@Composable
fun ScoutMapLifecycleHost(
    spots: List<ScoutSpotEntity>,
    userLat: Double,
    userLng: Double,
    userAccuracy: Float,
    selectedSpot: ScoutSpotEntity?,
    onSpotSelect: (ScoutSpotEntity) -> Unit,
    mapViewModel: ScoutMapViewModel,
    activeTrail: com.example.engine.OfflineWalkingTrail? = null,
    onPlanTrailClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapUiState by mapViewModel.uiState.collectAsState()

    // Ensure engine is initialized and state is updated
    LaunchedEffect(Unit) {
        ScoutVectorEngine.initialize(context.applicationContext)
        mapViewModel.onEngineReady()
    }

    // Keep user telemetry updated in ViewModel
    LaunchedEffect(userLat, userLng, userAccuracy) {
        mapViewModel.updateUserLocation(userLat, userLng, userAccuracy)
    }

    // Bind lifecycle events to ScoutVectorEngine
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> ScoutVectorEngine.onStart()
                Lifecycle.Event.ON_STOP -> ScoutVectorEngine.onStop()
                Lifecycle.Event.ON_DESTROY -> ScoutVectorEngine.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapCanvasView(
            spots = spots,
            userLat = userLat,
            userLng = userLng,
            userAccuracy = userAccuracy,
            selectedSpot = selectedSpot ?: mapUiState.selectedSpot,
            onSpotSelect = { spot ->
                mapViewModel.selectSpot(spot)
                onSpotSelect(spot)
            },
            zoomScale = mapUiState.zoomScale,
            zoomPreset = mapUiState.zoomPreset,
            onZoomIn = { mapViewModel.zoomIn() },
            onZoomOut = { mapViewModel.zoomOut() },
            onSetZoomPreset = { preset -> mapViewModel.setZoomPreset(preset) },
            onResetZoom = { mapViewModel.resetZoom() },
            activeTrail = activeTrail,
            onPlanTrailClick = onPlanTrailClick,
            modifier = Modifier.fillMaxSize()
        )
    }
}

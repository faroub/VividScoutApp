package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.toEntity
import com.example.data.model.toScoutGem
import com.example.engine.ScoutVectorEngine
import com.example.ui.theme.LocalAppStrings
import com.example.ui.viewmodel.ScoutMapViewModel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.dialogs.SpotDetailBottomSheet

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
    onClearTrailClick: (() -> Unit)? = null,
    cacheHealth: com.example.data.model.VicinityCacheHealth? = null,
    onOpenCacheHealth: (() -> Unit)? = null,
    isRegionDownloadMode: Boolean = false,
    selectionBounds: com.example.data.offline.GeoBoundingBox? = null,
    onUpdateSelectionBounds: ((com.example.data.offline.GeoBoundingBox) -> Unit)? = null,
    onStartRegionDownload: (() -> Unit)? = null,
    onCancelRegionDownload: (() -> Unit)? = null,
    onConfirmRegionDownload: (() -> Unit)? = null,
    achievements: List<com.example.data.local.AchievementEntity> = emptyList(),
    onSpotDetailClick: ((ScoutSpotEntity) -> Unit)? = null,
    onDismissSpotSelect: (() -> Unit)? = null,
    // True while the app runs on the built-in default (Lisbon) location because device
    // location is off. Surfaces the same fallback banner as ExploreScreen over the map.
    isUsingDefaultFallbackLocation: Boolean = false,
    onSetFallbackCity: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapUiState by mapViewModel.uiState.collectAsState()
    var internalSpotDetail by remember { mutableStateOf<ScoutSpotEntity?>(null) }

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
            gems = spots.map { it.toScoutGem() },
            userLat = userLat,
            userLng = userLng,
            userAccuracy = userAccuracy,
            selectedGem = (selectedSpot ?: mapUiState.selectedSpot)?.toScoutGem(),
            onGemSelect = { gem: com.example.data.model.ScoutGem ->
                val spot = gem.toEntity()
                mapViewModel.selectSpot(spot)
                onSpotSelect(spot)
            },
            zoomScale = mapUiState.zoomScale,
            zoomPreset = mapUiState.zoomPreset,
            onZoomIn = { mapViewModel.zoomIn() },
            onZoomOut = { mapViewModel.zoomOut() },
            onSetZoomPreset = { preset: com.example.ui.viewmodel.ZoomPreset -> mapViewModel.setZoomPreset(preset) },
            onResetZoom = { mapViewModel.resetZoom() },
            activeTrail = activeTrail,
            onPlanTrailClick = onPlanTrailClick,
            onClearTrailClick = onClearTrailClick,
            cacheHealth = cacheHealth,
            onOpenCacheHealth = onOpenCacheHealth,
            isRegionDownloadMode = isRegionDownloadMode,
            selectionBounds = selectionBounds,
            onUpdateSelectionBounds = onUpdateSelectionBounds,
            onStartRegionDownload = onStartRegionDownload,
            onCancelRegionDownload = onCancelRegionDownload,
            onConfirmRegionDownload = onConfirmRegionDownload,
            achievements = achievements,
            onSpotDetailClick = { spot ->
                if (onSpotDetailClick != null) {
                    onSpotDetailClick(spot)
                } else {
                    internalSpotDetail = spot
                }
            },
            onDismissSpotSelect = {
                mapViewModel.selectSpot(null)
                onDismissSpotSelect?.invoke()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Fallback-location banner shown over the map while discovery runs on the built-in
        // default (Lisbon) location because device location is off.
        if (isUsingDefaultFallbackLocation) {
            val strings = LocalAppStrings.current
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clickable { onSetFallbackCity?.invoke() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍",
                        fontSize = 18.sp,
                        color = Color(0xFF92400E)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.fallbackLocationBannerText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF92400E),
                        modifier = Modifier.weight(1f)
                    )
                    if (onSetFallbackCity != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFD97706)
                        ) {
                            Text(
                                text = strings.fallbackLocationBannerAction,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        internalSpotDetail?.let { spot ->
            SpotDetailBottomSheet(
                spot = spot,
                onDismiss = { internalSpotDetail = null },
                onToggleSave = { },
                onUpvote = { },
                onToggleAudio = { },
                isPlayingAudio = false
            )
        }
    }
}

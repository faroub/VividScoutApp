package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AchievementEntity
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutGem
import com.example.data.model.VicinityCacheHealth
import com.example.data.model.toEntity
import com.example.data.model.toScoutGem
import com.example.data.offline.GeoBoundingBox
import com.example.engine.OfflineWalkingTrail
import com.example.engine.ScoutVectorEngine
import com.example.sensor.CompassSensorManager
import com.example.ui.dialogs.SpotDetailBottomSheet
import com.example.ui.viewmodel.ZoomPreset
import com.example.util.CompassMathUtils
import kotlin.math.hypot

/**
 * Interactive Offline Vector Map Canvas for ScoutGem locations.
 * Renders fluid vector geometry of Lisbon with:
 * - Real GPS user beacon with pulsing accuracy halo
 * - Tagus River (Rio Tejo) water contour
 * - Street network grid
 * - Interactive ScoutGem pins with category badges, saved hearts, and tap gesture selection
 * - Manual zoom controls, preset zoom level chips, live zoom badge, and smooth pinch/pan gestures
 */
@Composable
fun MapCanvasView(
    gems: List<ScoutGem>,
    userLat: Double,
    userLng: Double,
    userAccuracy: Float,
    selectedGem: ScoutGem?,
    onGemSelect: (ScoutGem) -> Unit,
    zoomScale: Float = 1.0f,
    zoomPreset: ZoomPreset? = null,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onSetZoomPreset: (ZoomPreset) -> Unit = {},
    onResetZoom: () -> Unit = {},
    activeTrail: OfflineWalkingTrail? = null,
    onPlanTrailClick: (() -> Unit)? = null,
    onClearTrailClick: (() -> Unit)? = null,
    cacheHealth: VicinityCacheHealth? = null,
    onOpenCacheHealth: (() -> Unit)? = null,
    isRegionDownloadMode: Boolean = false,
    selectionBounds: GeoBoundingBox? = null,
    onUpdateSelectionBounds: ((GeoBoundingBox) -> Unit)? = null,
    onStartRegionDownload: (() -> Unit)? = null,
    onCancelRegionDownload: (() -> Unit)? = null,
    onConfirmRegionDownload: (() -> Unit)? = null,
    achievements: List<AchievementEntity> = emptyList(),
    onSpotDetailClick: ((ScoutSpotEntity) -> Unit)? = null,
    onDismissSpotSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var internalDetailSpot by remember { mutableStateOf<ScoutSpotEntity?>(null) }
    val context = LocalContext.current
    val compassSensorManager = remember { CompassSensorManager(context) }
    val compassState by compassSensorManager.compassState.collectAsState()

    DisposableEffect(userLat, userLng) {
        compassSensorManager.startListening(userLat, userLng)
        onDispose {
            compassSensorManager.stopListening()
        }
    }

    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var internalZoomScale by remember { mutableFloatStateOf(zoomScale) }

    // Sync internal zoom scale with external parameter when provided
    val activeZoomScale = if (zoomScale != 1.0f || internalZoomScale == 1.0f) zoomScale else internalZoomScale

    // Pulsing beacon animation for live GPS signal
    val infiniteTransition = rememberInfiniteTransition(label = "vectorPulse")
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconPulse"
    )

    // Marching phase for walking path dashed line and directional pulse markers
    val directionalPulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "directionalPulsePhase"
    )

    val baseScale = ScoutVectorEngine.DEFAULT_BASE_SCALE

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("scout_vector_map_canvas")
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    panOffset += pan
                    internalZoomScale = (internalZoomScale * zoomChange).coerceIn(
                        ScoutVectorEngine.MIN_ZOOM_SCALE,
                        ScoutVectorEngine.MAX_ZOOM_SCALE
                    )
                }
            }
            .pointerInput(gems, panOffset, activeZoomScale, userLat, userLng) {
                detectTapGestures { tapPos ->
                    // Calculate pin screen positions and find tapped gem
                    val currentCenter = Offset(
                        size.width / 2f + panOffset.x,
                        size.height / 2f + panOffset.y
                    )
                    var closestGem: ScoutGem? = null
                    var minDistance = Float.MAX_VALUE
                    val pinScale = activeZoomScale.coerceIn(0.85f, 1.4f)

                    gems.forEach { gem ->
                        val pinPos = ScoutVectorEngine.projectGpsToCanvasOffset(
                            spotLat = gem.latitude,
                            spotLng = gem.longitude,
                            userLat = userLat,
                            userLng = userLng,
                            canvasCenter = currentCenter,
                            zoomScale = activeZoomScale,
                            baseScale = baseScale
                        )

                        // Pin head center is elevated above the ground anchor tip
                        val pinHeadY = pinPos.y - 20f * pinScale
                        val dist = hypot(tapPos.x - pinPos.x, tapPos.y - pinHeadY)
                        val touchRadius = 52f * pinScale.coerceAtLeast(1f)
                        if (dist < touchRadius && dist < minDistance) {
                            minDistance = dist
                            closestGem = gem
                        }
                    }

                    if (closestGem != null) {
                        onGemSelect(closestGem)
                    } else {
                        onDismissSpotSelect?.invoke()
                    }
                }
            }
    ) {
        MapCanvasDrawing(
            gems = gems,
            userLat = userLat,
            userLng = userLng,
            userAccuracy = userAccuracy,
            selectedGem = selectedGem,
            activeZoomScale = activeZoomScale,
            panOffset = panOffset,
            activeTrail = activeTrail,
            beaconPulse = beaconPulse,
            directionalPulsePhase = directionalPulsePhase,
            achievements = achievements,
            isRegionDownloadMode = isRegionDownloadMode,
            selectionBounds = selectionBounds,
            baseScale = baseScale
        )

        // Live Zoom Percentage & Preset Badge (Top Floating on Canvas)
        MapCanvasZoomBadge(
            activeZoomScale = activeZoomScale,
            zoomPreset = zoomPreset,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
                .testTag("vector_zoom_scale_badge")
        )

        // Cache Health Score Badge (Top End Floating on Canvas)
        if (cacheHealth != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 12.dp)
            ) {
                CacheHealthBadge(
                    health = cacheHealth,
                    onClick = { onOpenCacheHealth?.invoke() },
                    isCompact = false
                )
            }
        }

        // True North Digital Compass Overlay (Floating on Map Canvas)
        val targetSpot = selectedGem?.toEntity() ?: CompassMathUtils.findNearestSavedGem(
            userLat,
            userLng,
            gems.map { it.toEntity() }
        )

        CompassOverlay(
            compassState = compassState,
            userLat = userLat,
            userLng = userLng,
            targetGem = targetSpot,
            onToggleTrueNorth = { compassSensorManager.toggleTrueNorthMode() },
            onSelectTargetGem = { spot ->
                gems.find { it.id == spot.id }?.let { onGemSelect(it) }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = if (cacheHealth != null) 52.dp else 12.dp)
        )

        // Preset Zoom Level Selector Chips (Bottom Floating above controls)
        MapZoomPresetSelector(
            zoomPreset = zoomPreset,
            activeZoomScale = activeZoomScale,
            onSetZoomPreset = onSetZoomPreset,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
        )

        // Floating Active Route HUD Card
        if (activeTrail != null) {
            MapRouteHud(
                activeTrail = activeTrail,
                onPlanTrailClick = onPlanTrailClick,
                onClearTrailClick = onClearTrailClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 44.dp)
            )
        }

        // Region Downloader Floating Controller Banner
        if (isRegionDownloadMode && selectionBounds != null) {
            MapRegionDownloaderBar(
                selectionBounds = selectionBounds,
                userLat = userLat,
                userLng = userLng,
                panOffset = panOffset,
                activeZoomScale = activeZoomScale,
                baseScale = baseScale,
                onUpdateSelectionBounds = onUpdateSelectionBounds,
                onCancelRegionDownload = onCancelRegionDownload,
                onConfirmRegionDownload = onConfirmRegionDownload,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 12.dp, end = 12.dp, top = 44.dp)
            )
        }

        // Floating On-Canvas Controls for Step Zoom (+/-), GPS Pan Reset, Region Downloader, and Route Planner
        MapCanvasControls(
            isRegionDownloadMode = isRegionDownloadMode,
            onStartRegionDownload = onStartRegionDownload,
            onCancelRegionDownload = onCancelRegionDownload,
            onPlanTrailClick = onPlanTrailClick,
            onResetPan = { panOffset = Offset.Zero },
            onZoomIn = {
                onZoomIn()
                internalZoomScale = (internalZoomScale + 0.35f).coerceAtMost(ScoutVectorEngine.MAX_ZOOM_SCALE)
            },
            onZoomOut = {
                onZoomOut()
                internalZoomScale = (internalZoomScale - 0.35f).coerceAtLeast(ScoutVectorEngine.MIN_ZOOM_SCALE)
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )

        // Floating Spot Preview Card (Bottom Floating upon Marker Tap)
        AnimatedVisibility(
            visible = selectedGem != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = if (activeTrail != null) 92.dp else 16.dp
                )
        ) {
            if (selectedGem != null) {
                val spot = selectedGem.toEntity()
                SpotPreviewPopupCard(
                    spot = spot,
                    onCardClick = {
                        if (onSpotDetailClick != null) {
                            onSpotDetailClick(spot)
                        } else {
                            internalDetailSpot = spot
                        }
                    },
                    onDismiss = {
                        onDismissSpotSelect?.invoke()
                    }
                )
            }
        }

        // Internal Spot Detail Bottom Sheet Fallback
        internalDetailSpot?.let { spot ->
            SpotDetailBottomSheet(
                spot = spot,
                onDismiss = { internalDetailSpot = null },
                onToggleSave = { },
                onUpvote = { },
                onToggleAudio = { },
                isPlayingAudio = false
            )
        }
    }
}


/**
 * Backward-compatible overload for ScoutSpotEntity list with manual zoom integration.
 */
@Composable
fun MapCanvasView(
    spots: List<ScoutSpotEntity>,
    userLat: Double,
    userLng: Double,
    userAccuracy: Float,
    selectedSpot: ScoutSpotEntity?,
    onSpotSelect: (ScoutSpotEntity) -> Unit,
    zoomScale: Float = 1.0f,
    zoomPreset: ZoomPreset? = null,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onSetZoomPreset: (ZoomPreset) -> Unit = {},
    onResetZoom: () -> Unit = {},
    activeTrail: OfflineWalkingTrail? = null,
    onPlanTrailClick: (() -> Unit)? = null,
    onClearTrailClick: (() -> Unit)? = null,
    cacheHealth: VicinityCacheHealth? = null,
    onOpenCacheHealth: (() -> Unit)? = null,
    isRegionDownloadMode: Boolean = false,
    selectionBounds: GeoBoundingBox? = null,
    onUpdateSelectionBounds: ((GeoBoundingBox) -> Unit)? = null,
    onStartRegionDownload: (() -> Unit)? = null,
    onCancelRegionDownload: (() -> Unit)? = null,
    onConfirmRegionDownload: (() -> Unit)? = null,
    onSpotDetailClick: ((ScoutSpotEntity) -> Unit)? = null,
    onDismissSpotSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val gems = remember(spots, userLat, userLng) {
        spots.map { it.toScoutGem(userLat, userLng) }
    }
    val selectedGem = remember(selectedSpot, userLat, userLng) {
        selectedSpot?.toScoutGem(userLat, userLng)
    }

    MapCanvasView(
        gems = gems,
        userLat = userLat,
        userLng = userLng,
        userAccuracy = userAccuracy,
        selectedGem = selectedGem,
        onGemSelect = { gem ->
            onSpotSelect(gem.toEntity())
        },
        zoomScale = zoomScale,
        zoomPreset = zoomPreset,
        onZoomIn = onZoomIn,
        onZoomOut = onZoomOut,
        onSetZoomPreset = onSetZoomPreset,
        onResetZoom = onResetZoom,
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
        onSpotDetailClick = onSpotDetailClick,
        onDismissSpotSelect = onDismissSpotSelect,
        modifier = modifier
    )
}

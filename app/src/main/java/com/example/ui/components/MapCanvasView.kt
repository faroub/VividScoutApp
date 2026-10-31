package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutGem
import com.example.data.model.toEntity
import com.example.data.model.toScoutGem
import com.example.data.offline.GeoBoundingBox
import com.example.engine.ScoutVectorEngine
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import kotlin.math.hypot

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ZoomPreset

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
    activeTrail: com.example.engine.OfflineWalkingTrail? = null,
    onPlanTrailClick: (() -> Unit)? = null,
    onClearTrailClick: (() -> Unit)? = null,
    cacheHealth: com.example.data.model.VicinityCacheHealth? = null,
    onOpenCacheHealth: (() -> Unit)? = null,
    isRegionDownloadMode: Boolean = false,
    selectionBounds: GeoBoundingBox? = null,
    onUpdateSelectionBounds: ((GeoBoundingBox) -> Unit)? = null,
    onStartRegionDownload: (() -> Unit)? = null,
    onCancelRegionDownload: (() -> Unit)? = null,
    onConfirmRegionDownload: (() -> Unit)? = null,
    achievements: List<com.example.data.local.AchievementEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val compassSensorManager = remember { com.example.sensor.CompassSensorManager(context) }
    val compassState by compassSensorManager.compassState.collectAsState()

    androidx.compose.runtime.DisposableEffect(userLat, userLng) {
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

                        val dist = hypot(tapPos.x - pinPos.x, tapPos.y - pinPos.y)
                        if (dist < 48f && dist < minDistance) {
                            minDistance = dist
                            closestGem = gem
                        }
                    }

                    closestGem?.let { onGemSelect(it) }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(
                size.width / 2f + panOffset.x,
                size.height / 2f + panOffset.y
            )

            // 1. Dark Lisbon Map Canvas Foundation
            drawRect(color = Color(0xFF080C1E))

            // 2. City Grid & Street Lines
            val gridStep = 45f * activeZoomScale
            var gx = (panOffset.x % gridStep)
            while (gx < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1f
                )
                gx += gridStep
            }
            var gy = (panOffset.y % gridStep)
            while (gy < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1f
                )
                gy += gridStep
            }

            // 3. Tagus River (Rio Tejo) Water Ribbon (South edge of Lisbon)
            val riverY = size.height * 0.80f + (panOffset.y * 0.35f)
            val riverPath = Path().apply {
                moveTo(-200f, riverY)
                cubicTo(
                    size.width * 0.28f, riverY - 35f * activeZoomScale,
                    size.width * 0.65f, riverY + 25f * activeZoomScale,
                    size.width + 200f, riverY - 15f * activeZoomScale
                )
                lineTo(size.width + 200f, size.height + 300f)
                lineTo(-200f, size.height + 300f)
                close()
            }
            drawPath(path = riverPath, color = Color(0xFF0D2845))
            drawPath(
                path = riverPath,
                color = BrandTeal.copy(alpha = 0.3f),
                style = Stroke(width = 2.5f)
            )

            // 4. Major Lisbon Arteries (Avenida da Liberdade & Rua Augusta corridors)
            val avenuePath = Path().apply {
                moveTo(center.x - 30f * activeZoomScale, center.y - 300f * activeZoomScale)
                lineTo(center.x - 10f * activeZoomScale, center.y + 120f * activeZoomScale)
                lineTo(center.x + 40f * activeZoomScale, center.y + 240f * activeZoomScale)
            }
            drawPath(
                path = avenuePath,
                color = Color(0xFF2A3655),
                style = Stroke(width = 4f * activeZoomScale)
            )

            val eastWestPath = Path().apply {
                moveTo(center.x - 320f * activeZoomScale, center.y + 40f * activeZoomScale)
                lineTo(center.x + 320f * activeZoomScale, center.y + 10f * activeZoomScale)
            }
            drawPath(
                path = eastWestPath,
                color = Color(0xFF1E2842),
                style = Stroke(width = 3f * activeZoomScale)
            )

            // 4b. Active Offline Walking Trail Polyline (Glowing dashed path with pulsing directional markers)
            activeTrail?.let { trail ->
                if (trail.pathPoints.size >= 2) {
                    val projectedPoints = trail.pathPoints.map { pt ->
                        ScoutVectorEngine.projectGpsToCanvasOffset(
                            spotLat = pt.first,
                            spotLng = pt.second,
                            userLat = userLat,
                            userLng = userLng,
                            canvasCenter = center,
                            zoomScale = activeZoomScale,
                            baseScale = baseScale
                        )
                    }

                    val trailPath = Path()
                    projectedPoints.forEachIndexed { index, pos ->
                        if (index == 0) trailPath.moveTo(pos.x, pos.y)
                        else trailPath.lineTo(pos.x, pos.y)
                    }

                    // 1. Wide ambient glowing path halo
                    drawPath(
                        path = trailPath,
                        color = BrandCoral.copy(alpha = 0.20f),
                        style = Stroke(width = 16f * activeZoomScale.coerceIn(0.8f, 1.5f))
                    )
                    // 2. Focused glowing corridor
                    drawPath(
                        path = trailPath,
                        color = BrandCoral.copy(alpha = 0.55f),
                        style = Stroke(width = 7f * activeZoomScale.coerceIn(0.8f, 1.5f))
                    )
                    // 3. Inner high-contrast marching dashed line
                    val dashLength = 16f
                    val gapLength = 10f
                    val totalCycle = dashLength + gapLength
                    drawPath(
                        path = trailPath,
                        color = Color.White,
                        style = Stroke(
                            width = 3f * activeZoomScale.coerceIn(0.8f, 1.5f),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                intervals = floatArrayOf(dashLength, gapLength),
                                phase = -directionalPulsePhase * totalCycle
                            )
                        )
                    )

                    // 4. Pulsing Directional Markers along the path
                    val numPoints = projectedPoints.size
                    if (numPoints >= 2) {
                        val markerSpacing = (numPoints / 6).coerceAtLeast(1)
                        for (i in 0 until numPoints - 1 step markerSpacing) {
                            val p1 = projectedPoints[i]
                            val p2 = projectedPoints[i + 1]
                            val dx = p2.x - p1.x
                            val dy = p2.y - p1.y
                            val segLength = hypot(dx, dy)
                            if (segLength > 6f) {
                                val offsetFraction = ((i.toFloat() / numPoints) + directionalPulsePhase) % 1f
                                val markerX = p1.x + dx * offsetFraction
                                val markerY = p1.y + dy * offsetFraction
                                val markerSize = 6f * activeZoomScale.coerceIn(0.8f, 1.4f)

                                drawCircle(
                                    color = BrandCoral.copy(alpha = 0.35f),
                                    radius = markerSize * 1.8f,
                                    center = Offset(markerX, markerY)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = markerSize * 0.7f,
                                    center = Offset(markerX, markerY)
                                )
                            }
                        }
                    }

                    // 5. Origin Beacon Node
                    projectedPoints.firstOrNull()?.let { startPt ->
                        drawCircle(
                            color = BrandTeal.copy(alpha = 0.35f),
                            radius = 12f * activeZoomScale.coerceIn(0.8f, 1.5f),
                            center = startPt
                        )
                        drawCircle(
                            color = BrandTeal,
                            radius = 6f * activeZoomScale.coerceIn(0.8f, 1.5f),
                            center = startPt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5f * activeZoomScale.coerceIn(0.8f, 1.5f),
                            center = startPt
                        )
                    }

                    // 6. Destination Target Pin Node
                    projectedPoints.lastOrNull()?.let { endPt ->
                        drawCircle(
                            color = BrandCoral.copy(alpha = 0.35f),
                            radius = 14f * activeZoomScale.coerceIn(0.8f, 1.5f),
                            center = endPt
                        )
                        drawCircle(
                            color = BrandCoral,
                            radius = 8f * activeZoomScale.coerceIn(0.8f, 1.5f),
                            center = endPt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5f * activeZoomScale.coerceIn(0.8f, 1.5f),
                            center = endPt
                        )
                    }
                }
            }

            // 5. User GPS Accuracy Ring & Beacon
            val accuracyRadius = (userAccuracy * 2.2f * activeZoomScale).coerceIn(18f, 100f)
            drawCircle(
                color = BrandTeal.copy(alpha = 0.12f),
                radius = accuracyRadius * beaconPulse,
                center = center
            )
            drawCircle(
                color = BrandTeal.copy(alpha = 0.35f),
                radius = accuracyRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // GPS Beacon Core
            drawCircle(
                color = Color.White,
                radius = 8f * activeZoomScale.coerceIn(0.8f, 1.4f),
                center = center
            )
            drawCircle(
                color = BrandTeal,
                radius = 6f * activeZoomScale.coerceIn(0.8f, 1.4f),
                center = center
            )

            // Directional Heading Indicator
            val headingPath = Path().apply {
                moveTo(center.x, center.y - 18f * activeZoomScale.coerceIn(0.8f, 1.4f))
                lineTo(center.x - 5f, center.y - 7f)
                lineTo(center.x + 5f, center.y - 7f)
                close()
            }
            drawPath(path = headingPath, color = BrandTeal)

            // 6. Draw Hidden Gem Pins
            gems.forEach { gem ->
                val pinPos = ScoutVectorEngine.projectGpsToCanvasOffset(
                    spotLat = gem.latitude,
                    spotLng = gem.longitude,
                    userLat = userLat,
                    userLng = userLng,
                    canvasCenter = center,
                    zoomScale = activeZoomScale,
                    baseScale = baseScale
                )
                val pinX = pinPos.x
                val pinY = pinPos.y

                val isSelected = selectedGem?.id == gem.id
                val tierStyle = com.example.util.MarkerTierResolver.resolveTierForCategory(gem.category, achievements)

                // Achievement Tier Pulsing Aura Ring (Bronze / Silver / Gold)
                if (tierStyle.isPulsing) {
                    val auraRadius = (20f + (beaconPulse * 8f)) * activeZoomScale.coerceIn(0.8f, 1.3f)
                    drawCircle(
                        color = tierStyle.auraColor.copy(alpha = 0.35f),
                        radius = auraRadius,
                        center = Offset(pinX, pinY)
                    )
                    drawCircle(
                        color = tierStyle.auraColor,
                        radius = auraRadius,
                        center = Offset(pinX, pinY),
                        style = Stroke(width = 1.5f)
                    )
                }

                val pinColor = when {
                    gem.isSaved -> BrandCoral
                    gem.isDiscovered -> Color(0xFFEAB308) // Discovered gold
                    gem.category == "eats" -> Color(0xFFFB7185)
                    gem.category == "history" -> Color(0xFFA855F7)
                    gem.category == "sights" -> Color(0xFF2DD4BF)
                    else -> Color(0xFF38BDF8)
                }

                // Selection Halo
                if (isSelected) {
                    drawCircle(
                        color = pinColor.copy(alpha = 0.35f),
                        radius = 26f * activeZoomScale.coerceIn(0.8f, 1.3f),
                        center = Offset(pinX, pinY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 26f * activeZoomScale.coerceIn(0.8f, 1.3f),
                        center = Offset(pinX, pinY),
                        style = Stroke(width = 2f)
                    )
                }

                // Pin Stem
                drawLine(
                    color = pinColor,
                    start = Offset(pinX, pinY),
                    end = Offset(pinX, pinY + 12f * activeZoomScale.coerceIn(0.8f, 1.2f)),
                    strokeWidth = 2.5f
                )

                // Outer Pin White Rim
                drawCircle(
                    color = Color.White,
                    radius = 12f * activeZoomScale.coerceIn(0.8f, 1.2f),
                    center = Offset(pinX, pinY)
                )

                // Inner Pin Category Color
                drawCircle(
                    color = pinColor,
                    radius = 9f * activeZoomScale.coerceIn(0.8f, 1.2f),
                    center = Offset(pinX, pinY)
                )

                // Center Dot (White for saved/discovered)
                if (gem.isSaved || gem.isDiscovered) {
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f * activeZoomScale.coerceIn(0.8f, 1.2f),
                        center = Offset(pinX, pinY)
                    )
                }
            }

            // 7. Interactive Bounding Box for Offline Region Download
            if (isRegionDownloadMode && selectionBounds != null) {
                val p1 = ScoutVectorEngine.projectGpsToCanvasOffset(
                    spotLat = selectionBounds.maxLat,
                    spotLng = selectionBounds.minLng,
                    userLat = userLat,
                    userLng = userLng,
                    canvasCenter = center,
                    zoomScale = activeZoomScale,
                    baseScale = baseScale
                )
                val p2 = ScoutVectorEngine.projectGpsToCanvasOffset(
                    spotLat = selectionBounds.minLat,
                    spotLng = selectionBounds.maxLng,
                    userLat = userLat,
                    userLng = userLng,
                    canvasCenter = center,
                    zoomScale = activeZoomScale,
                    baseScale = baseScale
                )

                val boxLeft = minOf(p1.x, p2.x)
                val boxTop = minOf(p1.y, p2.y)
                val boxWidth = kotlin.math.abs(p2.x - p1.x)
                val boxHeight = kotlin.math.abs(p2.y - p1.y)

                // Translucent fill
                drawRect(
                    color = BrandTeal.copy(alpha = 0.16f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight)
                )

                // Internal synthesis grid lines preview
                val subSteps = 4
                val subW = boxWidth / subSteps
                val subH = boxHeight / subSteps
                for (i in 1 until subSteps) {
                    drawLine(
                        color = BrandTeal.copy(alpha = 0.25f),
                        start = Offset(boxLeft + i * subW, boxTop),
                        end = Offset(boxLeft + i * subW, boxTop + boxHeight),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawLine(
                        color = BrandTeal.copy(alpha = 0.25f),
                        start = Offset(boxLeft, boxTop + i * subH),
                        end = Offset(boxLeft + boxWidth, boxTop + i * subH),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }

                // Dashed boundary stroke
                drawRect(
                    color = BrandTeal,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                )

                // Corner Handles
                val handleLen = 22f
                val handleThick = 4f
                val cornerColor = BrandCoral

                // Top-Left
                drawLine(cornerColor, Offset(boxLeft - 2f, boxTop), Offset(boxLeft + handleLen, boxTop), strokeWidth = handleThick)
                drawLine(cornerColor, Offset(boxLeft, boxTop - 2f), Offset(boxLeft, boxTop + handleLen), strokeWidth = handleThick)
                // Top-Right
                drawLine(cornerColor, Offset(boxLeft + boxWidth - handleLen, boxTop), Offset(boxLeft + boxWidth + 2f, boxTop), strokeWidth = handleThick)
                drawLine(cornerColor, Offset(boxLeft + boxWidth, boxTop - 2f), Offset(boxLeft + boxWidth, boxTop + handleLen), strokeWidth = handleThick)
                // Bottom-Left
                drawLine(cornerColor, Offset(boxLeft - 2f, boxTop + boxHeight), Offset(boxLeft + handleLen, boxTop + boxHeight), strokeWidth = handleThick)
                drawLine(cornerColor, Offset(boxLeft, boxTop + boxHeight - handleLen), Offset(boxLeft, boxTop + boxHeight + 2f), strokeWidth = handleThick)
                // Bottom-Right
                drawLine(cornerColor, Offset(boxLeft + boxWidth - handleLen, boxTop + boxHeight), Offset(boxLeft + boxWidth + 2f, boxTop + boxHeight), strokeWidth = handleThick)
                drawLine(cornerColor, Offset(boxLeft + boxWidth, boxTop + boxHeight - handleLen), Offset(boxLeft + boxWidth, boxTop + boxHeight + 2f), strokeWidth = handleThick)
            }
        }

        // Live Zoom Percentage & Preset Badge (Top Floating on Canvas)
        val zoomPct = (activeZoomScale * 100).toInt()
        val presetText = zoomPreset?.label ?: when {
            activeZoomScale <= 0.8f -> "City View"
            activeZoomScale <= 1.8f -> "Neighborhood"
            activeZoomScale <= 3.0f -> "Street Level"
            else -> "Close-Up Gem"
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xF2080B1E),
            border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
                .testTag("vector_zoom_scale_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$zoomPct% • $presetText",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

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
        val targetSpot = selectedGem?.toEntity() ?: com.example.util.CompassMathUtils.findNearestSavedGem(
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
    activeTrail: com.example.engine.OfflineWalkingTrail? = null,
    onPlanTrailClick: (() -> Unit)? = null,
    onClearTrailClick: (() -> Unit)? = null,
    cacheHealth: com.example.data.model.VicinityCacheHealth? = null,
    onOpenCacheHealth: (() -> Unit)? = null,
    isRegionDownloadMode: Boolean = false,
    selectionBounds: GeoBoundingBox? = null,
    onUpdateSelectionBounds: ((GeoBoundingBox) -> Unit)? = null,
    onStartRegionDownload: (() -> Unit)? = null,
    onCancelRegionDownload: (() -> Unit)? = null,
    onConfirmRegionDownload: (() -> Unit)? = null,
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
        modifier = modifier
    )
}

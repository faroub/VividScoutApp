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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutGem
import com.example.data.model.toEntity
import com.example.data.model.toScoutGem
import com.example.engine.ScoutVectorEngine
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import kotlin.math.hypot

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
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
    modifier: Modifier = Modifier
) {
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

        // Preset Zoom Level Selector Chips (Bottom Floating above controls)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomPreset.entries.forEach { preset ->
                val isSelected = (zoomPreset == preset) || Math.abs(preset.scale - activeZoomScale) < 0.1f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) BrandTeal else Color(0xF2080B1E))
                        .border(
                            1.dp,
                            if (isSelected) BrandTeal else Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(999.dp)
                        )
                        .clickable { onSetZoomPreset(preset) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("zoom_preset_chip_${preset.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Floating On-Canvas Controls for Step Zoom (+/-) and GPS Pan Reset
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Center on GPS Beacon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, BrandTeal.copy(alpha = 0.5f), CircleShape)
                    .clickable { panOffset = Offset.Zero }
                    .testTag("vector_center_on_me"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center on GPS",
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Step Zoom In (+)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable {
                        onZoomIn()
                        internalZoomScale = (internalZoomScale + 0.35f).coerceAtMost(ScoutVectorEngine.MAX_ZOOM_SCALE)
                    }
                    .testTag("vector_zoom_in"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Step Zoom Out (-)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable {
                        onZoomOut()
                        internalZoomScale = (internalZoomScale - 0.35f).coerceAtLeast(ScoutVectorEngine.MIN_ZOOM_SCALE)
                    }
                    .testTag("vector_zoom_out"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
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
        modifier = modifier
    )
}

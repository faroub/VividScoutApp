package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.example.engine.ScoutVectorEngine
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import kotlin.math.hypot

/**
 * Interactive Offline Vector Map Canvas.
 * Provides a fluid, zero-dependency stylized vector map of Lisbon with:
 * - Real GPS user beacon with pulsing accuracy halo
 * - Tagus River (Rio Tejo) water contour
 * - Street network grid
 * - Interactive gem pins with category coloring, saved hearts, and tap detection
 * - Smooth pan, pinch-zoom, and floating zoom/center controls
 */
@Composable
fun MapCanvasView(
    spots: List<ScoutSpotEntity>,
    userLat: Double,
    userLng: Double,
    userAccuracy: Float,
    selectedSpot: ScoutSpotEntity?,
    onSpotSelect: (ScoutSpotEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

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
                    zoomScale = (zoomScale * zoomChange).coerceIn(0.4f, 4.0f)
                }
            }
            .pointerInput(spots, panOffset, zoomScale, userLat, userLng) {
                detectTapGestures { tapPos ->
                    // Calculate pin screen positions and find tapped gem
                    val currentCenter = Offset(
                        size.width / 2f + panOffset.x,
                        size.height / 2f + panOffset.y
                    )
                    var closestSpot: ScoutSpotEntity? = null
                    var minDistance = Float.MAX_VALUE

                    spots.forEach { spot ->
                        val pinPos = ScoutVectorEngine.projectGpsToCanvasOffset(
                            spotLat = spot.latitude,
                            spotLng = spot.longitude,
                            userLat = userLat,
                            userLng = userLng,
                            canvasCenter = currentCenter,
                            zoomScale = zoomScale,
                            baseScale = baseScale
                        )

                        val dist = hypot(tapPos.x - pinPos.x, tapPos.y - pinPos.y)
                        if (dist < 48f && dist < minDistance) {
                            minDistance = dist
                            closestSpot = spot
                        }
                    }

                    closestSpot?.let { onSpotSelect(it) }
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
            val gridStep = 45f * zoomScale
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
                    size.width * 0.28f, riverY - 35f * zoomScale,
                    size.width * 0.65f, riverY + 25f * zoomScale,
                    size.width + 200f, riverY - 15f * zoomScale
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
                moveTo(center.x - 30f * zoomScale, center.y - 300f * zoomScale)
                lineTo(center.x - 10f * zoomScale, center.y + 120f * zoomScale)
                lineTo(center.x + 40f * zoomScale, center.y + 240f * zoomScale)
            }
            drawPath(
                path = avenuePath,
                color = Color(0xFF2A3655),
                style = Stroke(width = 4f * zoomScale)
            )

            val eastWestPath = Path().apply {
                moveTo(center.x - 320f * zoomScale, center.y + 40f * zoomScale)
                lineTo(center.x + 320f * zoomScale, center.y + 10f * zoomScale)
            }
            drawPath(
                path = eastWestPath,
                color = Color(0xFF1E2842),
                style = Stroke(width = 3f * zoomScale)
            )

            // 5. User GPS Accuracy Ring & Beacon
            val accuracyRadius = (userAccuracy * 2.2f * zoomScale).coerceIn(18f, 100f)
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
                radius = 8f * zoomScale.coerceIn(0.8f, 1.4f),
                center = center
            )
            drawCircle(
                color = BrandTeal,
                radius = 6f * zoomScale.coerceIn(0.8f, 1.4f),
                center = center
            )

            // Directional Heading Indicator
            val headingPath = Path().apply {
                moveTo(center.x, center.y - 18f * zoomScale.coerceIn(0.8f, 1.4f))
                lineTo(center.x - 5f, center.y - 7f)
                lineTo(center.x + 5f, center.y - 7f)
                close()
            }
            drawPath(path = headingPath, color = BrandTeal)

            // 6. Draw Hidden Gem Pins
            spots.forEach { spot ->
                val pinPos = ScoutVectorEngine.projectGpsToCanvasOffset(
                    spotLat = spot.latitude,
                    spotLng = spot.longitude,
                    userLat = userLat,
                    userLng = userLng,
                    canvasCenter = center,
                    zoomScale = zoomScale,
                    baseScale = baseScale
                )
                val pinX = pinPos.x
                val pinY = pinPos.y

                val isSelected = selectedSpot?.id == spot.id
                val pinColor = when {
                    spot.isSaved -> BrandCoral
                    spot.isDiscovered -> Color(0xFFEAB308) // Discovered gold
                    spot.category == "eats" -> Color(0xFFFB7185)
                    spot.category == "history" -> Color(0xFFA855F7)
                    spot.category == "sights" -> Color(0xFF2DD4BF)
                    else -> Color(0xFF38BDF8)
                }

                // Selection Halo
                if (isSelected) {
                    drawCircle(
                        color = pinColor.copy(alpha = 0.35f),
                        radius = 26f * zoomScale.coerceIn(0.8f, 1.3f),
                        center = Offset(pinX, pinY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 26f * zoomScale.coerceIn(0.8f, 1.3f),
                        center = Offset(pinX, pinY),
                        style = Stroke(width = 2f)
                    )
                }

                // Pin Stem
                drawLine(
                    color = pinColor,
                    start = Offset(pinX, pinY),
                    end = Offset(pinX, pinY + 12f * zoomScale.coerceIn(0.8f, 1.2f)),
                    strokeWidth = 2.5f
                )

                // Outer Pin White Rim
                drawCircle(
                    color = Color.White,
                    radius = 12f * zoomScale.coerceIn(0.8f, 1.2f),
                    center = Offset(pinX, pinY)
                )

                // Inner Pin Category Color
                drawCircle(
                    color = pinColor,
                    radius = 9f * zoomScale.coerceIn(0.8f, 1.2f),
                    center = Offset(pinX, pinY)
                )

                // Center Dot (White for saved/discovered)
                if (spot.isSaved || spot.isDiscovered) {
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f * zoomScale.coerceIn(0.8f, 1.2f),
                        center = Offset(pinX, pinY)
                    )
                }
            }
        }

        // Floating On-Canvas Controls for Pan/Zoom Reset
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

            // Zoom In
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable { zoomScale = (zoomScale * 1.3f).coerceAtMost(4.0f) }
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

            // Zoom Out
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.4f) }
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

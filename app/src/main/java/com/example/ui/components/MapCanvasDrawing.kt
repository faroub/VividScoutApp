package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.local.AchievementEntity
import com.example.data.model.ScoutGem
import com.example.data.offline.GeoBoundingBox
import com.example.engine.OfflineWalkingTrail
import com.example.engine.ScoutVectorEngine
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import kotlin.math.hypot

@Composable
internal fun MapCanvasDrawing(
    gems: List<ScoutGem>,
    userLat: Double,
    userLng: Double,
    userAccuracy: Float,
    selectedGem: ScoutGem?,
    activeZoomScale: Float,
    panOffset: Offset,
    activeTrail: OfflineWalkingTrail?,
    beaconPulse: Float,
    directionalPulsePhase: Float,
    achievements: List<AchievementEntity>,
    isRegionDownloadMode: Boolean,
    selectionBounds: GeoBoundingBox?,
    baseScale: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
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

        // 6. Draw Hidden Gem Pins (Distinct teardrop pins with category colors, white rim, and selection halo)
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

            val pinScale = activeZoomScale.coerceIn(0.85f, 1.35f)
            val pinHeadRadius = 12f * pinScale
            val pinHeadY = pinY - 22f * pinScale

            // Achievement Tier Pulsing Aura Ring (Bronze / Silver / Gold)
            if (tierStyle.isPulsing) {
                val auraRadius = (22f + (beaconPulse * 8f)) * pinScale
                drawCircle(
                    color = tierStyle.auraColor.copy(alpha = 0.35f),
                    radius = auraRadius,
                    center = Offset(pinX, pinHeadY)
                )
                drawCircle(
                    color = tierStyle.auraColor,
                    radius = auraRadius,
                    center = Offset(pinX, pinHeadY),
                    style = Stroke(width = 1.5f)
                )
            }

            val pinColor = when {
                gem.isSaved -> BrandCoral
                gem.isDiscovered -> Color(0xFFF59E0B) // Discovered gold
                gem.category.equals("sights", ignoreCase = true) -> Color(0xFF0D9488) // Vivid Teal
                gem.category.equals("eats", ignoreCase = true) -> Color(0xFFF43F5E) // Vibrant Coral/Rose
                gem.category.equals("history", ignoreCase = true) -> Color(0xFF8B5CF6) // Rich Purple
                gem.category.equals("shortcut", ignoreCase = true) -> Color(0xFFF59E0B) // Amber
                else -> Color(0xFF0284C7) // Sky Blue
            }

            // 1. Ground Contact Shadow Ellipse
            drawOval(
                color = Color.Black.copy(alpha = 0.40f),
                topLeft = Offset(pinX - 7f * pinScale, pinY - 2.5f * pinScale),
                size = Size(14f * pinScale, 5f * pinScale)
            )

            // 2. Pulsing Selection Halo
            if (isSelected) {
                val haloRadius = (pinHeadRadius + 14f) * (0.9f + beaconPulse * 0.25f)
                drawCircle(
                    color = pinColor.copy(alpha = 0.30f),
                    radius = haloRadius,
                    center = Offset(pinX, pinHeadY)
                )
                drawCircle(
                    color = Color.White,
                    radius = (pinHeadRadius + 7f) * pinScale,
                    center = Offset(pinX, pinHeadY),
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = pinColor,
                    radius = (pinHeadRadius + 4f) * pinScale,
                    center = Offset(pinX, pinHeadY),
                    style = Stroke(width = 1.5f)
                )
            }

            // 3. Outer Teardrop Pin Path (Crisp White Rim)
            val outerR = pinHeadRadius + 2.5f * pinScale
            val outerPath = Path().apply {
                moveTo(pinX, pinY)
                cubicTo(
                    pinX - outerR * 0.9f, pinY - 8f * pinScale,
                    pinX - outerR, pinHeadY + 5f * pinScale,
                    pinX - outerR, pinHeadY
                )
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        pinX - outerR, pinHeadY - outerR,
                        pinX + outerR, pinHeadY + outerR
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                cubicTo(
                    pinX + outerR, pinHeadY + 5f * pinScale,
                    pinX + outerR * 0.9f, pinY - 8f * pinScale,
                    pinX, pinY
                )
                close()
            }
            drawPath(path = outerPath, color = Color.White)

            // 4. Inner Teardrop Pin Path (Category-Specific Color Fill)
            val innerR = pinHeadRadius
            val innerPath = Path().apply {
                moveTo(pinX, pinY - 2.5f * pinScale)
                cubicTo(
                    pinX - innerR * 0.85f, pinY - 8f * pinScale,
                    pinX - innerR, pinHeadY + 4f * pinScale,
                    pinX - innerR, pinHeadY
                )
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        pinX - innerR, pinHeadY - innerR,
                        pinX + innerR, pinHeadY + innerR
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                cubicTo(
                    pinX + innerR, pinHeadY + 4f * pinScale,
                    pinX + innerR * 0.85f, pinY - 8f * pinScale,
                    pinX, pinY - 2.5f * pinScale
                )
                close()
            }
            drawPath(path = innerPath, color = pinColor)

            // 5. Center Head Indicator Dot
            drawCircle(
                color = Color.White,
                radius = 4.5f * pinScale,
                center = Offset(pinX, pinHeadY)
            )
            if (gem.isSaved || gem.isDiscovered) {
                drawCircle(
                    color = pinColor,
                    radius = 2.2f * pinScale,
                    center = Offset(pinX, pinHeadY)
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
}

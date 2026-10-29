package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScoutSpotEntity
import com.example.sensor.CompassAccuracy
import com.example.sensor.CompassState
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.Secondary
import com.example.util.CompassMathUtils
import java.util.Locale

/**
 * 'True North' Digital Compass Overlay for map canvas.
 * Functions offline using device orientation sensors to guide travelers toward saved gems.
 */
@Composable
fun CompassOverlay(
    compassState: CompassState,
    userLat: Double,
    userLng: Double,
    targetGem: ScoutSpotEntity? = null,
    onToggleTrueNorth: () -> Unit = {},
    onSelectTargetGem: (ScoutSpotEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    val heading = compassState.effectiveHeading
    val cardinal = CompassMathUtils.formatCardinalDirection(heading)

    val targetBearing = if (targetGem != null) {
        CompassMathUtils.calculateBearing(userLat, userLng, targetGem.latitude, targetGem.longitude)
    } else null

    val targetDistance = if (targetGem != null) {
        CompassMathUtils.calculateDistanceMeters(userLat, userLng, targetGem.latitude, targetGem.longitude)
    } else null

    Box(
        modifier = modifier.testTag("compass_overlay_root")
    ) {
        if (!isExpanded) {
            // Minimized Floating Quick Button
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = BrandNight.copy(alpha = 0.90f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandTeal.copy(alpha = 0.7f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { isExpanded = true }
                    .testTag("compass_minimized_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BrandTeal.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Compass",
                            tint = BrandTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${heading.toInt()}° $cardinal",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    if (targetGem != null && targetDistance != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${CompassMathUtils.formatDistance(targetDistance)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandCoral
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand compass",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // Expanded Digital Compass HUD Widget
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = BrandNight.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandTeal.copy(alpha = 0.5f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .width(220.dp)
                    .testTag("compass_expanded_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row: True North Toggle & Accuracy Badge & Minimize Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // True North / Mag North Toggle Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (compassState.isTrueNorthMode) BrandTeal.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f))
                                .border(
                                    1.dp,
                                    if (compassState.isTrueNorthMode) BrandTeal else Color.White.copy(alpha = 0.3f),
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable { onToggleTrueNorth() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("compass_true_north_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (compassState.isTrueNorthMode) "TRUE N" else "MAG N",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (compassState.isTrueNorthMode) BrandTeal else Color.White,
                                fontSize = 10.sp
                            )
                        }

                        // Sensor Accuracy Badge Pill
                        val (accLabel, accColor) = when (compassState.accuracyStatus) {
                            CompassAccuracy.HIGH -> "High Acc" to Color(0xFF10B981)
                            CompassAccuracy.MEDIUM -> "Med Acc" to Color(0xFFF59E0B)
                            CompassAccuracy.LOW -> "Low Acc" to Color(0xFFEF4444)
                            CompassAccuracy.UNCALIBRATED -> "Calibrate" to Color(0xFFF97316)
                            CompassAccuracy.UNKNOWN -> "Sensor" to Color(0xFF94A3B8)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(accColor.copy(alpha = 0.18f))
                                .border(1.dp, accColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .testTag("compass_accuracy_badge"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = accLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accColor,
                                fontSize = 10.sp
                            )
                        }

                        // Collapse Arrow
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { isExpanded = false }
                                .testTag("compass_minimize_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Minimize",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Rotating Compass Ring & Target Needle Canvas Widget
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .testTag("compass_canvas_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f

                            // Outer static housing ring
                            drawCircle(
                                color = BrandTeal.copy(alpha = 0.15f),
                                radius = radius,
                                style = Stroke(width = 3f)
                            )

                            // Inner rotating ring reflecting phone orientation
                            rotate(degrees = -heading, pivot = center) {
                                // Draw tick marks every 30 degrees
                                for (deg in 0 until 360 step 30) {
                                    val rad = Math.toRadians(deg.toDouble())
                                    val isCardinal = deg % 90 == 0
                                    val tickLen = if (isCardinal) 12f else 6f
                                    val startR = radius - 8f
                                    val endR = startR - tickLen

                                    val startX = center.x + (startR * Math.sin(rad)).toFloat()
                                    val startY = center.y - (startR * Math.cos(rad)).toFloat()
                                    val endX = center.x + (endR * Math.sin(rad)).toFloat()
                                    val endY = center.y - (endR * Math.cos(rad)).toFloat()

                                    drawLine(
                                        color = if (deg == 0) BrandCoral else Color.White.copy(alpha = 0.5f),
                                        start = Offset(startX, startY),
                                        end = Offset(endX, endY),
                                        strokeWidth = if (isCardinal) 2.5f else 1.2f
                                    )
                                }

                                // True North Indicator Arrow at 0° (North)
                                val northPath = Path().apply {
                                    moveTo(center.x, center.y - radius + 12f)
                                    lineTo(center.x - 6f, center.y - radius + 26f)
                                    lineTo(center.x + 6f, center.y - radius + 26f)
                                    close()
                                }
                                drawPath(path = northPath, color = BrandCoral)
                            }

                            // Rotating Target Needle pointing directly toward target gem
                            if (targetBearing != null) {
                                val pointerAngle = CompassMathUtils.calculatePointerAngle(heading, targetBearing)
                                rotate(degrees = pointerAngle, pivot = center) {
                                    // Glowing target needle pointing up
                                    val needleLength = radius * 0.72f
                                    val needlePath = Path().apply {
                                        moveTo(center.x, center.y - needleLength)
                                        lineTo(center.x - 7f, center.y - 12f)
                                        lineTo(center.x + 7f, center.y - 12f)
                                        close()
                                    }
                                    drawPath(path = needlePath, color = BrandTeal)

                                    // Needle base tail
                                    drawLine(
                                        color = BrandTeal.copy(alpha = 0.4f),
                                        start = center,
                                        end = Offset(center.x, center.y + 18f),
                                        strokeWidth = 2.5f
                                    )
                                }
                            }

                            // Center Hub
                            drawCircle(
                                color = BrandNight,
                                radius = 18f,
                                center = center
                            )
                            drawCircle(
                                color = BrandTeal,
                                radius = 18f,
                                center = center,
                                style = Stroke(width = 2f)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4f,
                                center = center
                            )
                        }

                        // Heading Readout Overlay at center
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${heading.toInt()}°",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = cardinal,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = BrandTeal,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Target Gem Distance & Orientation Banner
                    if (targetGem != null && targetDistance != null && targetBearing != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTargetGem(targetGem) }
                                .testTag("compass_target_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(BrandCoral),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = targetGem.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${CompassMathUtils.formatDistance(targetDistance)} • ${targetBearing.toInt()}° ${CompassMathUtils.formatCardinalDirection(targetBearing)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandTeal,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Prompt if no gem selected
                        Text(
                            text = "Save or select a gem to navigate offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

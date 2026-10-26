package com.example.ui.screens

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScoutSpotEntity
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScreen(
    spots: List<ScoutSpotEntity>,
    onSpotClick: (ScoutSpotEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRadius by remember { mutableStateOf("500m") }

    val infiniteTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val strings = com.example.ui.theme.LocalAppStrings.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("radar_screen_view"),
        contentPadding = PaddingValues(bottom = 120.dp, top = 12.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Radar Header Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BrandNight,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(BrandTeal.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.realtimeRadarTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "GPS Lock: Lisbon • Alfama 38.7112° N",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(SecondaryContainer.copy(alpha = 0.2f))
                                .border(1.dp, BrandTeal.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(BrandTeal)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "4 Detected",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrandTeal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Radar Canvas Drawing
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .testTag("radar_canvas_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(240.dp)) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val maxRadius = size.width / 2

                            // Rings
                            drawCircle(
                                color = BrandTeal.copy(alpha = 0.2f),
                                radius = maxRadius * 0.95f,
                                style = Stroke(width = 1.5f)
                            )
                            drawCircle(
                                color = BrandTeal.copy(alpha = 0.3f),
                                radius = maxRadius * 0.65f,
                                style = Stroke(width = 1.2f)
                            )
                            drawCircle(
                                color = BrandTeal.copy(alpha = 0.45f),
                                radius = maxRadius * 0.35f,
                                style = Stroke(width = 1f)
                            )

                            // Crosshairs
                            drawLine(
                                color = BrandTeal.copy(alpha = 0.2f),
                                start = Offset(center.x, 0f),
                                end = Offset(center.x, size.height),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = BrandTeal.copy(alpha = 0.2f),
                                start = Offset(0f, center.y),
                                end = Offset(size.width, center.y),
                                strokeWidth = 1f
                            )

                            // Rotating Sweep Beam
                            rotate(degrees = sweepAngle, pivot = center) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            BrandTeal.copy(alpha = 0.4f)
                                        ),
                                        center = center
                                    ),
                                    startAngle = -45f,
                                    sweepAngle = 45f,
                                    useCenter = true
                                )
                                drawLine(
                                    color = BrandTeal,
                                    start = center,
                                    end = Offset(center.x + maxRadius * 0.95f, center.y),
                                    strokeWidth = 2f
                                )
                            }

                            // Center device blip
                            drawCircle(
                                color = Color.White,
                                radius = 5f,
                                center = center
                            )
                            drawCircle(
                                color = BrandTeal,
                                radius = 10f,
                                center = center,
                                style = Stroke(width = 2f)
                            )

                            // Gem Blip 1: Nata Fresh (180m - northeast)
                            drawCircle(
                                color = BrandCoral,
                                radius = 6f,
                                center = Offset(center.x + 45f, center.y - 35f)
                            )
                            // Gem Blip 2: Pergola Nook (350m - southeast)
                            drawCircle(
                                color = BrandTeal,
                                radius = 6f,
                                center = Offset(center.x + 75f, center.y + 40f)
                            )
                            // Gem Blip 3: Tile Shortcut (520m - northwest)
                            drawCircle(
                                color = Color(0xFFC084FC),
                                radius = 6f,
                                center = Offset(center.x - 70f, center.y - 60f)
                            )
                            // Gem Blip 4: Carmo Ruins (650m - southwest)
                            drawCircle(
                                color = Color(0xFF38BDF8),
                                radius = 6f,
                                center = Offset(center.x - 85f, center.y + 55f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Radius Selector Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        listOf("100m", "300m", "500m", "1km").forEach { radius ->
                            val isSelected = selectedRadius == radius
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSelected) BrandTeal else Color.White.copy(alpha = 0.1f))
                                    .clickable { selectedRadius = radius }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("radius_pill_$radius")
                            ) {
                                Text(
                                    text = radius,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) Color(0xFF080B1E) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Radar Tracked Points
        item {
            Text(
                text = "${strings.nearbyPings} $selectedRadius",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // List of Detections
        items(spots) { spot ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSpotClick(spot) }
                    .testTag("radar_detected_spot_${spot.id}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when (spot.category) {
                                        "eats" -> BrandCoral.copy(alpha = 0.15f)
                                        "history" -> Color(0xFFC084FC).copy(alpha = 0.15f)
                                        else -> BrandTeal.copy(alpha = 0.15f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = when (spot.category) {
                                    "eats" -> BrandCoral
                                    "history" -> Color(0xFF7C3AED)
                                    else -> Secondary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = spot.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = spot.distance,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "• ${spot.statusBadge}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SecondaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = strings.track,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

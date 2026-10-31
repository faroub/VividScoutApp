package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceMeters
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SurfaceContainerLowest
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-Tech Radar Sweep View
 */
@Composable
fun RadarSweepView(
    spots: List<ScoutSpotEntity>,
    userLat: Double,
    userLng: Double,
    sweepAngle: Float,
    selectedRadius: String,
    onSpotSelect: (ScoutSpotEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Radar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
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
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Real-Time On-Foot Radar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Live Compass Scanning Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SecondaryContainer.copy(alpha = 0.25f))
                    .border(1.dp, BrandTeal.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${spots.size} in range",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = BrandTeal
                )
            }
        }

        // Concentric Radar Scope Canvas
        Box(
            modifier = Modifier
                .size(240.dp)
                .testTag("radar_canvas_box"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxRadius = size.width / 2

                // Concentric Range Rings
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
                                BrandTeal.copy(alpha = 0.35f)
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

                // User Center Device Blip
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

                // Plot detected gems on radar scope based on distance & bearing
                val maxRangeMeters = when (selectedRadius) {
                    "200m" -> 200f
                    "500m" -> 500f
                    "1km" -> 1000f
                    "3km" -> 3000f
                    else -> 1000f
                }

                spots.take(8).forEach { spot ->
                    val dist = spot.computedDistanceMeters(userLat, userLng)
                    val normDist = (dist / maxRangeMeters).coerceIn(0.15f, 0.92f) * maxRadius

                    val dLat = spot.latitude - userLat
                    val dLng = spot.longitude - userLng
                    val bearingRad = atan2(dLng, dLat)

                    val blipX = center.x + (normDist * sin(bearingRad)).toFloat()
                    val blipY = center.y - (normDist * cos(bearingRad)).toFloat()

                    val blipColor = when (spot.category) {
                        "eats" -> BrandCoral
                        "history" -> Color(0xFFC084FC)
                        "sights" -> Color(0xFF10B981)
                        else -> BrandTeal
                    }

                    drawCircle(
                        color = blipColor,
                        radius = 6.5f,
                        center = Offset(blipX, blipY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5f,
                        center = Offset(blipX, blipY)
                    )
                }
            }
        }

        Text(
            text = "Concentric Range: 100m • 300m • 500m",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
fun RadarCategoryFilterRibbon(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf(
        Triple("all", "All", Icons.Default.NearMe),
        Triple("touristic", "Touristic", Icons.Default.LocationOn),
        Triple("dining", "Dining", Icons.Default.Restaurant),
        Triple("historical", "Historical", Icons.Default.Explore),
        Triple("events", "Events", Icons.Default.Star),
        Triple("gems", "Gems", Icons.Default.Bookmark)
    )

    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("radar_category_filter_ribbon")
    ) {
        items(categories) { (id, label, icon) ->
            val isSelected = selectedCategory.equals(id, ignoreCase = true) ||
                    (id == "touristic" && selectedCategory.equals("sights", ignoreCase = true)) ||
                    (id == "dining" && selectedCategory.equals("eats", ignoreCase = true)) ||
                    (id == "historical" && selectedCategory.equals("history", ignoreCase = true)) ||
                    (id == "gems" && selectedCategory.equals("shortcut", ignoreCase = true))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) PrimaryContainer else SurfaceContainerLowest,
                shadowElevation = if (isSelected) 6.dp else 2.dp,
                modifier = Modifier
                    .clickable { onCategorySelected(id) }
                    .testTag("radar_category_chip_$id")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

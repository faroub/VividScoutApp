package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceMeters
import com.example.data.local.computedDistanceString
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.Primary
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryFixedDim
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary

@Composable
fun OnFootRadarCard(
    spots: List<ScoutSpotEntity> = emptyList(),
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    onRadarClick: () -> Unit
) {
    val strings = LocalAppStrings.current

    val infiniteTransition = rememberInfiniteTransition(label = "radarRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotation"
    )

    val top3 = remember(spots, userCoordinates) {
        if (spots.isNotEmpty()) {
            spots.sortedBy { it.computedDistanceMeters(userCoordinates.first, userCoordinates.second) }.take(3)
        } else {
            emptyList()
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRadarClick() }
            .testTag("on_foot_radar_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animated Radar Compass Icon Box
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLow),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient radar ring
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(1.dp, SecondaryFixedDim.copy(alpha = 0.6f), CircleShape)
                        )
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Radar",
                            tint = Secondary,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.onFootRadar,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryFixedDim)
                            )
                        }
                        Text(
                            text = strings.walkingRadiusInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Low queue pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Secondary)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.lowQueue,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Quick Mini Scanned Radar Waypoints dynamically calculated from real GPS coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (top3.isNotEmpty()) {
                    for (spot in top3) {
                        val dist = spot.computedDistanceString(userCoordinates.first, userCoordinates.second)
                        val status = when (spot.category) {
                            "eats" -> "Warm now"
                            "history" -> "Gothic lore"
                            "shortcut" -> "94% verified"
                            else -> "Sunlit free"
                        }
                        val color = when (spot.category) {
                            "eats" -> Primary
                            "history" -> Tertiary
                            else -> Secondary
                        }
                        val shortName = spot.title.split("—", "-").first().trim()
                        WaypointCard(
                            distance = dist,
                            name = shortName,
                            status = status,
                            statusColor = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    WaypointCard(
                        distance = "180m",
                        name = "Nata Fresh",
                        status = "Warm now",
                        statusColor = Primary,
                        modifier = Modifier.weight(1f)
                    )
                    WaypointCard(
                        distance = "350m",
                        name = "Pergola Nook",
                        status = "Sunlit free",
                        statusColor = Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    WaypointCard(
                        distance = "520m",
                        name = "Tile Shortcut",
                        status = "94% verified",
                        statusColor = Tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

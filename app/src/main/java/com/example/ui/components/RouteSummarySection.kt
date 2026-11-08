package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.OfflineWalkingTrail
import com.example.engine.RouteStep
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal

@Composable
fun RouteSummarySection(
    plannedRoute: OfflineWalkingTrail,
    areStepsExpanded: Boolean,
    onToggleStepsExpanded: () -> Unit,
    onClearRoute: () -> Unit,
    onDismiss: () -> Unit
) {
    // Route Statistics & Summary Badges
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Distance Badge
        Surface(
            color = BrandCoral.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BrandCoral.copy(alpha = 0.4f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = BrandCoral,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "DISTANCE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCoral
                    )
                    Text(
                        text = plannedRoute.formattedDistance,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Duration Badge
        Surface(
            color = BrandTeal.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.4f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "WALK TIME",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTeal
                    )
                    Text(
                        text = plannedRoute.formattedDuration,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // A* Mode Badge
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "APPROX. ROUTING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "A* Walking Graph",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Secondary Route Metrics: Pedestrian % & Lisbon Calçada Incline
    val totalDist = plannedRoute.totalDistanceMeters.coerceAtLeast(1.0)
    val pedDist = plannedRoute.steps.filter { it.isPedestrianOnly }.sumOf { it.distanceMeters }
    val pedPct = ((pedDist / totalDist) * 100).toInt().coerceIn(0, 100)
    val stairsCount = plannedRoute.steps.count { it.isStairsOrCalçada }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$pedPct% Car-Free Paths",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE2E8F0)
                )
            }
        }

        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terrain,
                    contentDescription = null,
                    tint = if (stairsCount > 0) Color(0xFFF59E0B) else BrandTeal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (stairsCount > 0) "$stairsCount Stone Stairways" else "Gentle Grade",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stairsCount > 0) Color(0xFFFBBF24) else Color(0xFFE2E8F0)
                )
            }
        }
    }

    // Dedicated Elevation Profile Sparkline Card (Incline vs Decline)
    if (plannedRoute.elevationProfile != null) {
        Spacer(modifier = Modifier.height(10.dp))
        ElevationProfileSparkline(
            elevationProfile = plannedRoute.elevationProfile,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Turn-by-Turn Steps Header & List
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleStepsExpanded() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Turn-by-Turn Directions · approximate graph (${plannedRoute.steps.size} steps)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE2E8F0)
        )
        Icon(
            imageVector = if (areStepsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "Toggle Directions",
            tint = Color(0xFF94A3B8)
        )
    }

    AnimatedVisibility(
        visible = areStepsExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(plannedRoute.steps) { step ->
                StepItemView(step = step)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Actions: Show On Map and Clear Route
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onClearRoute,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("clear_route_btn")
        ) {
            Text("Clear Route")
        }

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = BrandCoral),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1.5f)
                .testTag("show_on_map_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show On Map", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepItemView(step: RouteStep) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, iconTint, bgTint) = when {
                step.isStairsOrCalçada -> Triple(Icons.Default.Terrain, Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.2f))
                step.isPedestrianOnly -> Triple(Icons.AutoMirrored.Filled.DirectionsWalk, BrandTeal, BrandTeal.copy(alpha = 0.2f))
                step.streetName.contains("Destination", ignoreCase = true) -> Triple(Icons.Default.Place, BrandCoral, BrandCoral.copy(alpha = 0.2f))
                else -> Triple(Icons.Default.NearMe, Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.2f))
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(bgTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                if (step.distanceMeters > 0) {
                    Text(
                        text = "${step.formattedDistance} • ${step.streetName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

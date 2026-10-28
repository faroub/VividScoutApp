package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.ScoutSpotEntity
import com.example.engine.OfflineWalkingTrail
import com.example.engine.RouteStep
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    origin: ScoutSpotEntity?,
    destination: ScoutSpotEntity?,
    allSpots: List<ScoutSpotEntity>,
    plannedRoute: OfflineWalkingTrail?,
    isCalculating: Boolean,
    onSelectOrigin: (ScoutSpotEntity?) -> Unit,
    onSelectDestination: (ScoutSpotEntity) -> Unit,
    onSwapEndpoints: () -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isSelectingOrigin by remember { mutableStateOf(false) }
    var isSelectingDest by remember { mutableStateOf(false) }
    var areStepsExpanded by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF101726),
        contentColor = Color.White,
        modifier = modifier.testTag("route_planner_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandTeal.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = "Offline Walking Route Planner",
                            tint = BrandTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Offline Route Planner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "A* shortest path on vector road graph",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_route_planner_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Route Planner",
                        tint = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Origin & Destination Selector Card
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left route spine (dots & line)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(BrandTeal)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(36.dp)
                                .background(Color(0xFF475569))
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(BrandCoral)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Origin & Destination Pickers
                    Column(modifier = Modifier.weight(1f)) {
                        // Origin Field
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSelectingOrigin = true }
                                .testTag("origin_selector")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (origin == null) Icons.Default.MyLocation else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = origin?.name ?: "My Current GPS Location",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Destination Field
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSelectingDest = true }
                                .testTag("destination_selector")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = BrandCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = destination?.name ?: "Select Destination Gem",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (destination != null) Color.White else Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Swap Button
                    IconButton(
                        onClick = onSwapEndpoints,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155))
                            .testTag("swap_endpoints_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap Origin and Destination",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calculating State
            if (isCalculating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BrandCoral,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Calculating offline A* walking path...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1)
                    )
                }
            } else if (plannedRoute != null) {
                // Route Statistics & Summary Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Distance Badge
                    Surface(
                        color = BrandCoral.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandCoral.copy(alpha = 0.4f)),
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.4f)),
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
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
                                    text = "ROUTING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "A* Snapped",
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

                Spacer(modifier = Modifier.height(14.dp))

                // Turn-by-Turn Steps Header & List
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { areStepsExpanded = !areStepsExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Turn-by-Turn Walking Directions (${plannedRoute.steps.size} steps)",
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
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

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    // Spot Selector Dialogs
    if (isSelectingOrigin) {
        SpotPickerModal(
            title = "Choose Starting Point",
            includeCurrentGps = true,
            spots = allSpots,
            onSelect = { selected ->
                onSelectOrigin(selected)
                isSelectingOrigin = false
            },
            onDismiss = { isSelectingOrigin = false }
        )
    }

    if (isSelectingDest) {
        SpotPickerModal(
            title = "Choose Destination Gem",
            includeCurrentGps = false,
            spots = allSpots,
            onSelect = { selected ->
                if (selected != null) {
                    onSelectDestination(selected)
                }
                isSelectingDest = false
            },
            onDismiss = { isSelectingDest = false }
        )
    }
}

@Composable
private fun StepItemView(step: RouteStep) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
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

@Composable
private fun SpotPickerModal(
    title: String,
    includeCurrentGps: Boolean,
    spots: List<ScoutSpotEntity>,
    onSelect: (ScoutSpotEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSpots = remember(searchQuery, spots) {
        if (searchQuery.isBlank()) spots else {
            spots.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true) ||
                        it.neighborhood.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF101726),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search cached gems...", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (includeCurrentGps) {
                        item {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(null) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = BrandTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "My Current GPS Location",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Use live device coordinates",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(filteredSpots) { spot ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(spot) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = if (spot.isSaved) BrandCoral else Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = spot.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${spot.category.replaceFirstChar { it.uppercase() }} • ${spot.neighborhood}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

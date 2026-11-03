package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.settings.WalkingNavigationMode
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.util.Geo
import com.example.util.MapWalkingEngine

/**
 * 2D Map-Based Walking Navigation Banner overlay for devices without camera/AR sensors
 * or when the user selects 2D map navigation mode.
 */
@Composable
fun Map2DWalkingNavOverlay(
    userLat: Double,
    userLng: Double,
    waypointLat: Double,
    waypointLng: Double,
    waypointTitle: String,
    currentMode: WalkingNavigationMode,
    modifier: Modifier = Modifier,
    isArSupported: Boolean = true,
    onToggleNavigationMode: (WalkingNavigationMode) -> Unit = {},
    onDismissNavigation: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var hasTriggeredArrivalHaptic by remember { mutableStateOf(false) }

    val navState = remember(userLat, userLng, waypointLat, waypointLng, waypointTitle) {
        MapWalkingEngine.evaluateWaypointNavigation(
            userLat = userLat,
            userLng = userLng,
            waypointLat = waypointLat,
            waypointLng = waypointLng,
            waypointTitle = waypointTitle
        )
    }

    // Trigger haptic feedback when user enters 20m arrival radius
    LaunchedEffect(navState.isArrived) {
        if (navState.isArrived && !hasTriggeredArrivalHaptic) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            hasTriggeredArrivalHaptic = true
        } else if (!navState.isArrived) {
            hasTriggeredArrivalHaptic = false
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF2080B1E),
        border = BorderStroke(1.5.dp, if (navState.isArrived) Color(0xFF10B981) else BrandTeal),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("2d_map_walking_nav_overlay")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Waypoint & Arrival Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (navState.isArrived) Color(0xFF10B981).copy(alpha = 0.2f) else BrandTeal.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Target Bearing Pointer",
                            tint = if (navState.isArrived) Color(0xFF10B981) else BrandTeal,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(navState.bearingDegrees)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (navState.isArrived) "ARRIVED AT WAYPOINT 🎯" else "NEXT WAYPOINT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                fontSize = 9.5.sp
                            ),
                            color = if (navState.isArrived) Color(0xFF10B981) else BrandTeal
                        )
                        Text(
                            text = navState.waypointTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Mode Switcher: AR 3D vs Map 2D
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isArSupported) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (currentMode == WalkingNavigationMode.AR_3D) BrandTeal else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    val nextMode = if (currentMode == WalkingNavigationMode.AR_3D) WalkingNavigationMode.MAP_2D else WalkingNavigationMode.AR_3D
                                    onToggleNavigationMode(nextMode)
                                }
                                .testTag("toggle_nav_mode_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (currentMode == WalkingNavigationMode.AR_3D) Icons.Default.ViewInAr else Icons.Default.Map,
                                    contentDescription = "Switch Navigation Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (currentMode == WalkingNavigationMode.AR_3D) "3D AR" else "2D Map",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = onDismissNavigation,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .testTag("dismiss_2d_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Navigation",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Distance & Bearing Telemetry Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = BrandCoral,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val distText = Geo.formatDistanceAndWalkTime(userLat, userLng, waypointLat, waypointLng)
                    Text(
                        text = distText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bearing: ${navState.bearingDegrees.toInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import java.util.Locale

@Composable
fun ProfilePrefetchCard(
    isPrefetchActive: Boolean,
    isPrefetching: Boolean,
    prefetchVector: com.example.util.MovementVector?,
    prefetchedGemsCount: Int,
    prefetchStatusMessage: String,
    activePowerMode: com.example.util.PrefetchPowerMode,
    batteryStatus: com.example.util.BatteryStatus,
    prefetchSpeedKmh: Float,
    onTogglePrefetch: (Boolean) -> Unit,
    onTriggerManualPrefetch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPrefetchActive) Color(0xFF8B5CF6).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_proximity_prefetch_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PROXIMITY PREFETCH",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFF8B5CF6)
                        )
                        Text(
                            text = "5km Movement Vector AI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Switch(
                    checked = isPrefetchActive,
                    onCheckedChange = { onTogglePrefetch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF8B5CF6)
                    ),
                    modifier = Modifier.testTag("prefetch_service_switch")
                )
            }

            Text(
                text = "Automatically pre-caches authentic viewpoint, historical, and culinary POIs for the next 5km along your predicted travel trajectory using Gemini AI and stores them in the local Room database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Movement Vector Telemetry Readout
            if (prefetchVector != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Predicted Trajectory",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${prefetchVector.bearingDegrees.toInt()}° (${prefetchVector.cardinalDirection})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF8B5CF6)
                            )
                        }
                        Text(
                            text = "Target 5km Ahead: %.4f°, %.4f°".format(Locale.US, prefetchVector.targetLat, prefetchVector.targetLng),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Corridor Radius: 5000m (±1.5km width)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Adaptive Battery & Speed Policy Telemetry Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (activePowerMode) {
                        com.example.util.PrefetchPowerMode.VEHICLE_SPEED_PAUSED -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                        com.example.util.PrefetchPowerMode.LOW_BATTERY_ECO -> Color(0xFFEF4444).copy(alpha = 0.5f)
                        com.example.util.PrefetchPowerMode.CHARGING_UNRESTRICTED -> Color(0xFF10B981).copy(alpha = 0.5f)
                        com.example.util.PrefetchPowerMode.OPTIMAL_WALKING -> Color(0xFF8B5CF6).copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prefetch_adaptive_policy_section")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADAPTIVE BATTERY POLICY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = when (activePowerMode) {
                                com.example.util.PrefetchPowerMode.VEHICLE_SPEED_PAUSED -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                com.example.util.PrefetchPowerMode.LOW_BATTERY_ECO -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                com.example.util.PrefetchPowerMode.CHARGING_UNRESTRICTED -> Color(0xFF10B981).copy(alpha = 0.2f)
                                com.example.util.PrefetchPowerMode.OPTIMAL_WALKING -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = activePowerMode.badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when (activePowerMode) {
                                    com.example.util.PrefetchPowerMode.VEHICLE_SPEED_PAUSED -> Color(0xFFD97706)
                                    com.example.util.PrefetchPowerMode.LOW_BATTERY_ECO -> Color(0xFFDC2626)
                                    com.example.util.PrefetchPowerMode.CHARGING_UNRESTRICTED -> Color(0xFF059669)
                                    com.example.util.PrefetchPowerMode.OPTIMAL_WALKING -> Color(0xFF8B5CF6)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Device Battery",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${batteryStatus.levelPercent}%" + if (batteryStatus.isPlugged) " ⚡ Charging" else if (batteryStatus.isPowerSaveMode) " (Power Saver)" else "",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (batteryStatus.levelPercent <= 20) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Travel Speed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${prefetchSpeedKmh.toInt()} km/h " + if (prefetchSpeedKmh > 25f) "(Transit)" else "(Walking)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (prefetchSpeedKmh > 25f) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "GPS Polling: ${activePowerMode.locationIntervalMs / 1000}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (activePowerMode.isPrefetchAllowed) "Trigger: ${activePowerMode.triggerDisplacementMeters.toInt()}m shift" else "Prefetch: Paused",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (activePowerMode.isPrefetchAllowed) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFF59E0B)
                        )
                    }

                    if (activePowerMode == com.example.util.PrefetchPowerMode.VEHICLE_SPEED_PAUSED) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Vehicle speed (>25 km/h) detected. Granular walking gem prefetch is paused until walking resumes to prevent redundant caching.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else if (activePowerMode == com.example.util.PrefetchPowerMode.LOW_BATTERY_ECO) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🔋 Low battery (≤20%) or Power Saver active. Polling throttled to 3 minutes and displacement threshold raised to 1km.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            // Status and Cache Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isPrefetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF8B5CF6)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (prefetchStatusMessage.isNotBlank()) prefetchStatusMessage else if (isPrefetchActive) "Monitoring trajectory..." else "Prefetch standby",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPrefetchActive) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$prefetchedGemsCount In Room DB",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Manual Action Button
            Button(
                onClick = onTriggerManualPrefetch,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trigger_manual_prefetch_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPrefetching) "Prefetching 5km Corridor..." else "Prefetch 5km Corridor Now",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

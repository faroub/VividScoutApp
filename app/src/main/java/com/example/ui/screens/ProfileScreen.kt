package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import java.util.Locale
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.components.DataStorageDashboardCard
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary

@Composable
fun ProfileScreen(
    userPoints: Int,
    discoveredCount: Int = 0,
    offlineDraftCount: Int,
    isOfflineBannerVisible: Boolean,
    onToggleOfflineBanner: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddSpotClick: () -> Unit,
    onReturnToOnboarding: () -> Unit,
    onOpenLanguageDialog: () -> Unit,
    hasLocationPermission: Boolean = true,
    isPreciseLocation: Boolean = true,
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    onRequestLocationPermission: () -> Unit = {},
    offlineMapPacks: List<com.example.data.offline.OfflineMapPack> = emptyList(),
    totalOfflineStorageBytes: Long = 0L,
    onDownloadOfflinePack: (String) -> Unit = {},
    onDeleteOfflinePack: (String) -> Unit = {},
    onDownloadCurrentGpsPack: () -> Unit = {},
    customRegions: List<com.example.data.offline.DownloadedRegionPack> = emptyList(),
    onDeleteCustomRegion: (String) -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    totalSpotsCount: Int = 0,
    savedCount: Int = 0,
    databaseSizeBytes: Long = 0L,
    onClearCacheClick: () -> Unit = {},
    achievements: List<com.example.data.local.AchievementEntity> = emptyList(),
    unlockedAchievementsCount: Int = 0,
    isPrefetchActive: Boolean = false,
    isPrefetching: Boolean = false,
    prefetchVector: com.example.util.MovementVector? = null,
    prefetchedGemsCount: Int = 0,
    prefetchStatusMessage: String = "",
    activePowerMode: com.example.util.PrefetchPowerMode = com.example.util.PrefetchPowerMode.OPTIMAL_WALKING,
    batteryStatus: com.example.util.BatteryStatus = com.example.util.BatteryStatus(),
    prefetchSpeedKmh: Float = 0f,
    onTogglePrefetch: (Boolean) -> Unit = {},
    onTriggerManualPrefetch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val currentLang = LocalAppLanguage.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen_view"),
        contentPadding = PaddingValues(bottom = 120.dp, top = 12.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Traveler Profile Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(3.dp, PrimaryContainer, CircleShape)
                    ) {
                        AsyncImage(
                            model = "https://lh3.googleusercontent.com/aida-public/AB6AXuAs6vcDGG_z1rRQ0dZDwD-EyHaTOuW0LL_SjnChMzi9GQMjxBw0tunAYfTpCN8QILwZKh9-c7yMiwZWmEDmuSRz4e2Dw8DiwjGcvM6P1SEcmM9mE8sQvMkwqfLaWKKjJ-jy0DxycCtIvV6boGu1WI0mWlQUpYsB5ZW9SIcvgM1Z8400SklNsRhcjIRAgZBISZdPaEj10snj7R4T1tS2lkLc5UXILauYCvecoh7wm_NMKJFItk5uEP6-",
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "@ana_traveler",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(SecondaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.masterScout,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3 Big Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatItem(title = strings.scoutPoints, value = "$userPoints", highlightColor = PrimaryContainer)
                        ProfileStatItem(title = strings.discoveredTab, value = "$discoveredCount", highlightColor = Secondary)
                        ProfileStatItem(title = strings.upvotes, value = "88", highlightColor = Tertiary)
                    }
                }
            }
        }

        // Scout Badges
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.unlockedBadges,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BadgePill(name = "Alley Explorer", icon = Icons.Default.Explore, color = Secondary, modifier = Modifier.weight(1f))
                        BadgePill(name = "Tart Hunter", icon = Icons.Default.EmojiEvents, color = BrandCoral, modifier = Modifier.weight(1f))
                        BadgePill(name = "94% Accuracy", icon = Icons.Default.AutoAwesome, color = Tertiary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Scout Achievements Showcase Section
        item {
            com.example.ui.components.AchievementsShowcaseSection(
                achievements = achievements,
                unlockedCount = unlockedAchievementsCount,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Proximity Prefetch 5km Vector AI Service Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPrefetchActive) Color(0xFF8B5CF6).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
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

                            // Active Policy Warnings
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

        // Offline Protocol & Queue Tile
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenQueue() }
                    .testTag("profile_offline_queue_tile")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
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
                                .clip(CircleShape)
                                .background(SecondaryContainer.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.offlineSubmissionQueue,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$offlineDraftCount ${strings.draftsInDb}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // GPS Telemetry & Permissions Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_gps_telemetry_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.locationSettingsCardTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    when {
                                        hasLocationPermission && isPreciseLocation -> SecondaryContainer.copy(alpha = 0.6f)
                                        hasLocationPermission -> Color(0xFFE0F2FE)
                                        else -> BrandCoral.copy(alpha = 0.15f)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = when {
                                    hasLocationPermission && isPreciseLocation -> strings.preciseGpsActive
                                    hasLocationPermission -> strings.approximateGpsActive
                                    else -> strings.gpsDisabled
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    hasLocationPermission && isPreciseLocation -> Secondary
                                    hasLocationPermission -> Color(0xFF0369A1)
                                    else -> BrandCoral
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .padding(12.dp),
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
                                    .background(
                                        if (hasLocationPermission) Secondary.copy(alpha = 0.15f)
                                        else BrandCoral.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hasLocationPermission) Icons.Default.GpsFixed else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (hasLocationPermission) Secondary else BrandCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasLocationPermission) {
                                        String.format(java.util.Locale.US, "%.4f° N, %.4f° W", userCoordinates.first, userCoordinates.second)
                                    } else {
                                        "38.7112° N, -9.1423° W (Default)"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.locationSettingsCardDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onRequestLocationPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasLocationPermission) Secondary else BrandCoral,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("profile_configure_gps_button")
                        ) {
                            Text(
                                text = if (hasLocationPermission) "Settings" else "Enable",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // Offline Vector Map Packs Section
        item {
            com.example.ui.components.OfflineMapPacksSection(
                packs = offlineMapPacks,
                totalStorageBytes = totalOfflineStorageBytes,
                onDownloadPack = onDownloadOfflinePack,
                onDeletePack = onDeleteOfflinePack,
                onDownloadCurrentGpsPack = onDownloadCurrentGpsPack,
                customRegions = customRegions,
                onDeleteCustomRegion = onDeleteCustomRegion
            )
        }

        // Data Usage & Local Storage Dashboard
        item {
            DataStorageDashboardCard(
                totalSpotsCount = totalSpotsCount,
                savedCount = savedCount,
                discoveredCount = discoveredCount,
                draftCount = offlineDraftCount,
                databaseSizeBytes = databaseSizeBytes,
                onClearCacheClick = onClearCacheClick
            )
        }

        // Settings / Mode Toggle
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.changeLanguage,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Language Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenLanguageDialog() }
                            .padding(vertical = 10.dp)
                            .testTag("profile_language_setting_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.language,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${currentLang.nativeName} (${currentLang.displayName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Secondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOfflineBannerVisible) Icons.Default.WifiOff else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.simulateOffline,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Shows persistent offline sync banner",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isOfflineBannerVisible,
                            onCheckedChange = { onToggleOfflineBanner() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Secondary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Overpass API Diagnostics Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenDiagnostics() }
                            .padding(vertical = 10.dp)
                            .testTag("profile_overpass_diagnostic_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Overpass Diagnostics",
                                tint = BrandTeal
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Overpass API Diagnostics",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Inspect raw JSON response logs & Room mapping",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Replay Onboarding
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onReturnToOnboarding() }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.revisitOnboarding,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = PrimaryContainer
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = PrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStatItem(title: String, value: String, highlightColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = highlightColor
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BadgePill(name: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontSize = 10.sp
            )
        }
    }
}

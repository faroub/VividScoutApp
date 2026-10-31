package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.ui.components.DataStorageDashboardCard
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest

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
        // Traveler Profile Card & Badges
        item {
            ProfileHeaderSection(
                userPoints = userPoints,
                discoveredCount = discoveredCount,
                strings = strings
            )
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
            ProfilePrefetchCard(
                isPrefetchActive = isPrefetchActive,
                isPrefetching = isPrefetching,
                prefetchVector = prefetchVector,
                prefetchedGemsCount = prefetchedGemsCount,
                prefetchStatusMessage = prefetchStatusMessage,
                activePowerMode = activePowerMode,
                batteryStatus = batteryStatus,
                prefetchSpeedKmh = prefetchSpeedKmh,
                onTogglePrefetch = onTogglePrefetch,
                onTriggerManualPrefetch = onTriggerManualPrefetch
            )
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

                    // Demo Mode Peer Sync Toggle
                    var isDemoMode by remember { mutableStateOf(com.example.data.p2p.DemoModeProvider.isDemoPeerSyncEnabled) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Simulate Peer Sync (Demo Mode)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Simulates 3 sample peers for UI testing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isDemoMode,
                            onCheckedChange = {
                                isDemoMode = it
                                com.example.data.p2p.DemoModeProvider.isDemoPeerSyncEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Secondary
                            ),
                            modifier = Modifier.testTag("toggle_demo_peer_sync_switch")
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

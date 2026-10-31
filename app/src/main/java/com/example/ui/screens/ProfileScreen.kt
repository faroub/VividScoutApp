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
            ProfileOfflineQueueTile(
                offlineDraftCount = offlineDraftCount,
                onOpenQueue = onOpenQueue
            )
        }

        // GPS Telemetry & Permissions Card
        item {
            ProfileGpsTelemetryCard(
                hasLocationPermission = hasLocationPermission,
                isPreciseLocation = isPreciseLocation,
                userCoordinates = userCoordinates,
                onRequestLocationPermission = onRequestLocationPermission
            )
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
            ProfileSettingsSection(
                isOfflineBannerVisible = isOfflineBannerVisible,
                onToggleOfflineBanner = onToggleOfflineBanner,
                onOpenLanguageDialog = onOpenLanguageDialog,
                onOpenDiagnostics = onOpenDiagnostics,
                onReturnToOnboarding = onReturnToOnboarding
            )
        }
    }
}

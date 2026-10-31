package com.example.ui.screens

import android.location.Location
import com.example.ui.components.MapCanvasView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import com.example.data.local.RecentSearchEntity
import com.example.ui.components.DiscoverGemsLoadingState
import com.example.ui.components.RecentSearchStrip
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceMeters
import com.example.data.local.computedDistanceString
import com.example.data.local.computedWalkMinutes
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.Tertiary
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class RadarViewMode {
    MAP,
    RADAR
}

@Composable
fun RadarScreen(
    spots: List<ScoutSpotEntity>,
    selectedCategory: String = "all",
    onCategorySelected: (String) -> Unit = {},
    userLocation: Location? = null,
    hasLocationPermission: Boolean = true,
    isPreciseLocation: Boolean = true,
    onRequestLocationPermission: () -> Unit = {},
    onSpotClick: (ScoutSpotEntity) -> Unit,
    onToggleSave: (ScoutSpotEntity) -> Unit = {},
    isScrapingOsm: Boolean = false,
    osmScrapeMessage: String? = null,
    onScrapeOsm: () -> Unit = {},
    onDismissOsmMessage: () -> Unit = {},
    syncStatus: com.example.ui.components.OfflineSyncStatus = com.example.ui.components.OfflineSyncStatus(),
    activeTrail: com.example.engine.OfflineWalkingTrail? = null,
    onPlanWalkingTrail: () -> Unit = {},
    onClearWalkingTrail: () -> Unit = {},
    cacheHealth: com.example.data.model.VicinityCacheHealth? = null,
    onOpenCacheHealth: () -> Unit = {},
    isRegionDownloadMode: Boolean = false,
    selectionBounds: com.example.data.offline.GeoBoundingBox? = null,
    onUpdateSelectionBounds: (com.example.data.offline.GeoBoundingBox) -> Unit = {},
    onStartRegionDownload: () -> Unit = {},
    onCancelRegionDownload: () -> Unit = {},
    onConfirmRegionDownload: () -> Unit = {},
    recentSearches: List<RecentSearchEntity> = emptyList(),
    onSelectRecentSearch: (RecentSearchEntity) -> Unit = {},
    onDeleteRecentSearch: (String) -> Unit = {},
    onClearAllRecentSearches: () -> Unit = {},
    achievements: List<com.example.data.local.AchievementEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(RadarViewMode.MAP) }
    var selectedRadius by remember { mutableStateOf("1km") }
    var selectedSpot by remember { mutableStateOf<ScoutSpotEntity?>(spots.firstOrNull()) }

    val userLat = userLocation?.latitude ?: 38.7112
    val userLng = userLocation?.longitude ?: -9.1423
    val userAccuracy = userLocation?.accuracy ?: 6f
    val isLiveGps = userLocation != null

    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconPulse"
    )

    val strings = com.example.ui.theme.LocalAppStrings.current

    // Calculate real distances to all spots and sort
    val sortedSpots = remember(spots, userLat, userLng, selectedRadius) {
        val maxDist = when (selectedRadius) {
            "200m" -> 200f
            "500m" -> 500f
            "1km" -> 1000f
            "3km" -> 3000f
            else -> Float.MAX_VALUE
        }
        spots.map { spot ->
            Pair(spot, spot.computedDistanceMeters(userLat, userLng))
        }.filter { it.second <= maxDist }
            .sortedBy { it.second }
            .map { it.first }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("radar_screen_view"),
        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Permission Rationale Banner (if permission not yet granted)
        if (!hasLocationPermission) {
            item {
                RadarLocationPermissionBanner(
                    onRequestLocationPermission = onRequestLocationPermission
                )
            }
        } else if (!isPreciseLocation) {
            // Approximate GPS Active - Offer upgrade to Precise
            item {
                RadarApproximateGpsBanner(
                    onRequestLocationPermission = onRequestLocationPermission
                )
            }
        }

        // 2. Mode Selector: [ 🗺️ Live Gem Map ] | [ 📡 On-Foot Radar ]
        item {
            RadarViewModeSelector(
                viewMode = viewMode,
                onViewModeChange = { viewMode = it }
            )
        }

        // 2.5 Floating Category Chips Ribbon for Map Screen
        item {
            RadarCategoryFilterRibbon(
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected
            )
        }

        // 2.7 Recent Gemini AI Searches Horizontal Scroll Strip
        if (recentSearches.isNotEmpty()) {
            item {
                RecentSearchStrip(
                    recentSearches = recentSearches,
                    onSearchClick = onSelectRecentSearch,
                    onDeleteSearch = onDeleteRecentSearch,
                    onClearAllSearches = onClearAllRecentSearches
                )
            }
        }

        // 3. Main Visual Component (Map or Radar)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BrandNight,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (viewMode == RadarViewMode.MAP) 480.dp else 380.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (viewMode == RadarViewMode.MAP) {
                        // Scout Vector Map interactive view with user location and Room gems
                        MapCanvasView(
                            spots = sortedSpots,
                            userLat = userLat,
                            userLng = userLng,
                            userAccuracy = userAccuracy,
                            selectedSpot = selectedSpot,
                            onSpotSelect = { spot ->
                                selectedSpot = spot
                                onSpotClick(spot)
                            },
                            activeTrail = activeTrail,
                            onPlanTrailClick = onPlanWalkingTrail,
                            cacheHealth = cacheHealth,
                            onOpenCacheHealth = onOpenCacheHealth,
                            isRegionDownloadMode = isRegionDownloadMode,
                            selectionBounds = selectionBounds,
                            onUpdateSelectionBounds = onUpdateSelectionBounds,
                            onStartRegionDownload = onStartRegionDownload,
                            onCancelRegionDownload = onCancelRegionDownload,
                            onConfirmRegionDownload = onConfirmRegionDownload,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // High-Tech Radar View
                        RadarSweepView(
                            spots = sortedSpots,
                            userLat = userLat,
                            userLng = userLng,
                            sweepAngle = sweepAngle,
                            selectedRadius = selectedRadius,
                            onSpotSelect = { selectedSpot = it }
                        )
                    }
                }
            }
        }

        // 3b. OpenStreetMap Web Scraper Action Card
        item {
            RadarOsmScraperCard(
                isScrapingOsm = isScrapingOsm,
                osmScrapeMessage = osmScrapeMessage,
                selectedCategory = selectedCategory,
                userCoordinates = Pair(userLat, userLng),
                userAccuracy = userAccuracy,
                onScrapeOsm = onScrapeOsm,
                onDismissOsmMessage = onDismissOsmMessage
            )
        }

        // 4. Radius Filter Pills
        item {
            RadarRadiusSelector(
                selectedRadius = selectedRadius,
                onRadiusSelected = { selectedRadius = it }
            )
        }

        // 5. Section Title: Nearby Detected Gems
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.nearbyPings} ($selectedRadius)",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${sortedSpots.size} detected",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Secondary
                )
            }
        }

        // 6. List of Detections with Real GPS Distance
        items(sortedSpots, key = { it.id }) { spot ->
            RadarDetectedSpotItem(
                spot = spot,
                userLat = userLat,
                userLng = userLng,
                onSpotClick = {
                    selectedSpot = spot
                    onSpotClick(spot)
                },
                onToggleSave = onToggleSave
            )
        }
    }
}


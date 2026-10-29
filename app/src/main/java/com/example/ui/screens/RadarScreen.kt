package com.example.ui.screens

import android.location.Location
import com.example.ui.components.ScoutMapView
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
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BrandCoral.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandCoral.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BrandCoral),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.locationPermissionTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.locationPermissionDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onRequestLocationPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandCoral,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("grant_location_permission_button")
                        ) {
                            Text("Enable", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        } else if (!isPreciseLocation) {
            // Approximate GPS Active - Offer upgrade to Precise
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = BrandTeal.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(BrandTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = strings.approximateGpsActive,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Enable Precise Location for 1m walking accuracy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onRequestLocationPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Secondary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("upgrade_precise_location_button")
                        ) {
                            Text("Precise", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // 2. Mode Selector: [ 🗺️ Live Gem Map ] | [ 📡 On-Foot Radar ]
        item {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val isMap = viewMode == RadarViewMode.MAP
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isMap) PrimaryContainer else Color.Transparent)
                            .clickable { viewMode = RadarViewMode.MAP }
                            .padding(vertical = 10.dp)
                            .testTag("mode_toggle_map"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = if (isMap) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Gem Map",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isMap) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isRadar = viewMode == RadarViewMode.RADAR
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isRadar) PrimaryContainer else Color.Transparent)
                            .clickable { viewMode = RadarViewMode.RADAR }
                            .padding(vertical = 10.dp)
                            .testTag("mode_toggle_radar"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = if (isRadar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "On-Foot Radar",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isRadar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
                        ScoutMapView(
                            spots = sortedSpots,
                            userLat = userLat,
                            userLng = userLng,
                            userAccuracy = userAccuracy,
                            hasLocationPermission = hasLocationPermission,
                            selectedRadius = selectedRadius,
                            selectedSpot = selectedSpot,
                            onSpotSelect = { selectedSpot = it },
                            onSpotClick = onSpotClick,
                            onToggleSave = onToggleSave,
                            isScrapingOsm = isScrapingOsm,
                            osmScrapeMessage = osmScrapeMessage,
                            onScrapeOsm = onScrapeOsm,
                            onDismissOsmMessage = onDismissOsmMessage,
                            syncStatus = syncStatus,
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("radar_scrape_osm_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BrandTeal.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "✨ Discover Gems with Gemini AI",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Generate verified landmarks & viewpoints using Gemini 3.8 Flash",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onScrapeOsm,
                            enabled = !isScrapingOsm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryContainer,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("radar_scrape_osm_button")
                        ) {
                            if (isScrapingOsm) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Discovering...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Discover",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    if (isScrapingOsm) {
                        Spacer(modifier = Modifier.height(14.dp))
                        DiscoverGemsLoadingState(
                            category = selectedCategory,
                            userCoordinates = Pair(userLat, userLng),
                            gpsAccuracyMeters = userAccuracy,
                            statusMessage = osmScrapeMessage
                        )
                    } else if (osmScrapeMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandTeal.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismissOsmMessage() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = osmScrapeMessage.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Radius Filter Pills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf("200m", "500m", "1km", "3km", "All").forEach { radius ->
                    val isSelected = selectedRadius == radius
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) PrimaryContainer else SurfaceContainerLowest)
                            .shadow(if (isSelected) 4.dp else 1.dp, RoundedCornerShape(999.dp))
                            .clickable { selectedRadius = radius }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("radius_pill_$radius")
                    ) {
                        Text(
                            text = radius,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
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
            val distStr = spot.computedDistanceString(userLat, userLng)
            val walkMins = spot.computedWalkMinutes(userLat, userLng)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedSpot = spot
                        onSpotClick(spot)
                    }
                    .testTag("radar_detected_spot_${spot.id}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when (spot.category) {
                                        "eats" -> BrandCoral.copy(alpha = 0.15f)
                                        "history" -> Color(0xFFC084FC).copy(alpha = 0.15f)
                                        else -> BrandTeal.copy(alpha = 0.15f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = when (spot.category) {
                                    "eats" -> BrandCoral
                                    "history" -> Color(0xFF7C3AED)
                                    else -> Secondary
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = spot.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$distStr ($walkMins min)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "• ${spot.statusBadge}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleSave(spot) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("radar_bookmark_button_${spot.id}")
                        ) {
                            Icon(
                                imageVector = if (spot.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (spot.isSaved) "Remove bookmark" else "Bookmark",
                                tint = if (spot.isSaved) BrandCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(SecondaryContainer.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = strings.track,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}


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

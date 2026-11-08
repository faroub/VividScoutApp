package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Deck
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.ui.text.style.TextAlign
import android.location.Location
import com.example.data.local.computedDistanceMeters
import com.example.data.local.computedDistanceString
import com.example.data.local.computedWalkMinutes
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.util.ImageResolver
import com.example.data.local.ScoutSpotEntity
import com.example.ui.components.DiscoverGemsLoadingState
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.EatsAmber
import com.example.ui.theme.EatsAmberBg
import com.example.ui.theme.HistoryPurple
import com.example.ui.theme.HistoryPurpleBg
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.Secondary
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SecondaryFixedDim
import com.example.ui.theme.SightsGreen
import com.example.ui.theme.SightsGreenBg
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import androidx.compose.material.icons.filled.Layers
import com.example.data.local.RecentSearchEntity
import com.example.data.model.ExploreMapLayer
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OfflineNoCacheStateView
import com.example.ui.theme.Tertiary
import com.example.ui.theme.TertiaryFixed

@Composable
fun ExploreScreen(
    spots: List<ScoutSpotEntity>,
    selectedCategory: String,
    searchQuery: String,
    headerStyle: Int,
    isOfflineBannerVisible: Boolean,
    offlineDraftCount: Int,
    playingAudioId: String?,
    activeMapLayers: Set<ExploreMapLayer> = ExploreMapLayer.ALL_LAYERS,
    onToggleMapLayer: (ExploreMapLayer) -> Unit = {},
    onSetAllMapLayers: (Boolean) -> Unit = {},
    onCategorySelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSave: (ScoutSpotEntity) -> Unit,
    onUpvote: (ScoutSpotEntity) -> Unit,
    onToggleAudioLore: (String) -> Unit,
    onSpotClick: (ScoutSpotEntity) -> Unit,
    onAddSpotClick: () -> Unit,
    onPlanRouteClick: () -> Unit = {},
    onDismissOfflineBanner: () -> Unit,
    onOpenQueueDialog: () -> Unit,
    userLocation: Location? = null,
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    hasLocationPermission: Boolean = true,
    onRequestLocationPermission: () -> Unit = {},
    isUsingGpsLocation: Boolean = true,
    isUsingDefaultFallbackLocation: Boolean = false,
    selectedGlobalCity: String? = null,
    onGpsClick: () -> Unit = {},
    syncStatus: com.example.ui.components.OfflineSyncStatus = com.example.ui.components.OfflineSyncStatus(),
    onOpenDiagnostics: () -> Unit = {},
    cacheHealth: com.example.data.model.VicinityCacheHealth? = null,
    onOpenCacheHealth: () -> Unit = {},
    isDiscovering: Boolean = false,
    discoveryMessage: String? = null,
    onTriggerDiscover: () -> Unit = {},
    preScoutState: com.example.engine.PreScoutBannerState = com.example.engine.PreScoutBannerState(),
    onDismissPreScout: () -> Unit = {},
    onViewPreScout: () -> Unit = {},
    onOpenTouristicSources: () -> Unit = {},
    recentSearches: List<RecentSearchEntity> = emptyList(),
    onDeleteRecentSearch: (String) -> Unit = {},
    onClearAllRecentSearches: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("explore_feed_list"),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Fallback Location Status Indicator Banner
            // 0a. Default (Lisbon) fallback: device location is off and the user never picked a
            // city, so discovery silently runs on DefaultLocationConfig's built-in coordinates.
            // The banner makes that explicit and offers the city picker.
            if (isUsingDefaultFallbackLocation) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGpsClick() }
                                .testTag("explore_fallback_location_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "📍", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.fallbackLocationBannerText,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF92400E)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFD97706)
                                ) {
                                    Text(
                                        text = strings.fallbackLocationBannerAction,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 0b. Manually selected city (device GPS off): the user chose this location, so the
            // banner is informational only.
            if (!isUsingGpsLocation && !isUsingDefaultFallbackLocation) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGpsClick() }
                                .testTag("explore_fallback_location_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "📍", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Fallback Location Mode Active",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF92400E)
                                        )
                                        Text(
                                            text = "Using ${selectedGlobalCity ?: "selected region"}. Tap to change or enable GPS.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFB45309),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFD97706)
                                ) {
                                    Text(
                                        text = "CHANGE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1a. Offline Alert Banner (if visible)
            if (isOfflineBannerVisible) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OfflineAlertBanner(
                            draftCount = offlineDraftCount,
                            onQueueClick = onOpenQueueDialog,
                            onDismissClick = onDismissOfflineBanner
                        )
                    }
                }
            }

            // 1b. Pre-Scout City Boundary Entry Ticker Banner (if visible)
            if (preScoutState.isVisible) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        com.example.ui.components.PreScoutTickerBanner(
                            state = preScoutState,
                            onViewClick = onViewPreScout,
                            onDismissClick = onDismissPreScout
                        )
                    }
                }
            }

            // 2. Header Style 0 vs Header Style 1
            if (headerStyle == 0) {
                // Style 0: Floating Search + Live Scrape Pulse Ticker
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Floating Pill Search Bar
                        FloatingPillSearchBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = onSearchQueryChange,
                            recentSearches = recentSearches,
                            onDeleteRecentSearch = onDeleteRecentSearch,
                            onClearAllRecentSearches = onClearAllRecentSearches
                        )

                        // Offline Database Sync Status Badge & Cache Health Score Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.example.ui.components.OfflineSyncStatusBadge(
                                status = syncStatus,
                                onClick = onOpenQueueDialog,
                                onOpenDiagnostics = onOpenDiagnostics,
                                modifier = Modifier.weight(1f)
                            )

                            if (cacheHealth != null) {
                                com.example.ui.components.CacheHealthBadge(
                                    health = cacheHealth,
                                    onClick = onOpenCacheHealth,
                                    isCompact = true
                                )
                            }
                        }

                        // Live Scrape Pulse Ticker
                        LiveScrapePulseTicker(
                            isDiscovering = isDiscovering,
                            discoveryMessage = discoveryMessage,
                            onTriggerDiscover = onTriggerDiscover
                        )
                    }
                }

                // Category Filter Ribbon
                item {
                    CategoryFilterRibbon(
                        selectedCategory = selectedCategory,
                        onCategorySelected = onCategorySelected
                    )
                }
            } else {
                // Style 1: Chiado District Radiant Teal Depth Hero Header
                item {
                    ChiadoHeroHeader(
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange
                    )
                }

                // Category Filter Chips for Sights Hero
                item {
                    ChiadoCategoryFilterRibbon(
                        selectedCategory = selectedCategory,
                        onCategorySelected = onCategorySelected
                    )
                }
            }

            // Pinned Inline Toolbar Strip: Map & Route + Map Overlays (No floating buttons)
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    ExploreControlToolbarStrip(
                        activeMapLayers = activeMapLayers,
                        onToggleMapLayer = onToggleMapLayer,
                        onPlanRouteClick = onPlanRouteClick
                    )
                }
            }

            // Section Title: Trending Around You
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.trendingAroundYou,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PrimaryContainer.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clickable { onTriggerDiscover() }
                                .testTag("discover_gems_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Discover Gems",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = BrandTeal.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clickable { onOpenTouristicSources() }
                                .testTag("open_touristic_sources_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = BrandTeal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sources",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = BrandTeal
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onPlanRouteClick() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("map_view_trigger")
                        ) {
                            Text(
                                text = strings.mapView,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Primary
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Feed Cards OR Loading State OR Empty State OR Offline No Cache Error State
            if (isDiscovering) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        DiscoverGemsLoadingState(
                            category = selectedCategory,
                            userCoordinates = userCoordinates,
                            gpsAccuracyMeters = userLocation?.accuracy,
                            statusMessage = discoveryMessage
                        )
                    }
                }
            } else if (spots.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                        if (!hasLocationPermission || !isUsingGpsLocation) {
                            OfflineNoCacheStateView(
                                title = "Offline & No Local Cache",
                                description = "Connect to network or select a pre-cached city to explore offline.",
                                actionText = "Select City / Enable GPS",
                                onActionClick = onGpsClick
                            )
                        } else {
                            EmptyStateView(
                                title = "No Spots Found in Vicinity",
                                description = "No spots match category '$selectedCategory'. Tap discover to scan nearby viewpoints and historic gems.",
                                actionText = "Discover Gems Now ✨",
                                onActionClick = onTriggerDiscover
                            )
                        }
                    }
                }
            } else {
                items(spots, key = { it.id }) { spot ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ScoutSpotCard(
                            spot = spot,
                            userCoordinates = userCoordinates,
                            isPlayingAudio = playingAudioId == spot.id,
                            activeLayers = activeMapLayers,
                            onToggleSave = { onToggleSave(spot) },
                            onUpvote = { onUpvote(spot) },
                            onToggleAudio = { onToggleAudioLore(spot.id) },
                            onDirectionsClick = { onSpotClick(spot) }
                        )
                    }
                }
            }

            // 4. Interactive Community Scout Callout Card
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    CommunityScoutCalloutCard(onAddSpotClick = onAddSpotClick)
                }
            }
        }
    }
}

/**
 * Pinned inline toolbar strip for Explore screen containing Map & Route launcher
 * and multi-select Historical, Culinary, and Shortcut overlay chips (Zero floating buttons).
 */
@Composable
fun ExploreControlToolbarStrip(
    activeMapLayers: Set<ExploreMapLayer>,
    onToggleMapLayer: (ExploreMapLayer) -> Unit,
    onPlanRouteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceContainerLow,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("explore_control_toolbar_strip")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Map & Route Action Button (Inline, non-floating)
                Surface(
                    onClick = onPlanRouteClick,
                    shape = RoundedCornerShape(999.dp),
                    color = BrandNight,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("inline_map_route_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = "Map & Route",
                            tint = BrandTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Map & Route",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            color = Color.White
                        )
                    }
                }

                // Map Overlays Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Map Overlays",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inline segmented layer toggle chips (Historical, Culinary, Shortcut)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExploreMapLayer.entries.forEach { layer ->
                    val isActive = activeMapLayers.contains(layer)
                    Surface(
                        onClick = { onToggleMapLayer(layer) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (isActive) layer.color.copy(alpha = 0.18f) else SurfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isActive) layer.color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        shadowElevation = if (isActive) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inline_layer_chip_${layer.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) layer.color else Color.Gray.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = layer.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}





package com.example.ui.screens

import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Search
import android.location.Location
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScoutSpotEntity
import com.example.ui.components.DiscoverGemsLoadingState
import com.example.ui.theme.LocalAppStrings
import androidx.compose.material.icons.filled.Layers
import com.example.data.local.RecentSearchEntity
import com.example.data.model.ExploreMapLayer
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FallbackLocationBanner
import com.example.ui.components.OfflineNoCacheStateView

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
                    FallbackLocationBanner(
                        onClick = onGpsClick,
                        testTag = "explore_fallback_location_banner",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
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

            // 2. Header Style 0 vs Header Style 1 (section composables)
            exploreFeedHeaderSection(
                headerStyle = headerStyle,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                recentSearches = recentSearches,
                onDeleteRecentSearch = onDeleteRecentSearch,
                onClearAllRecentSearches = onClearAllRecentSearches,
                syncStatus = syncStatus,
                onOpenQueueDialog = onOpenQueueDialog,
                onOpenDiagnostics = onOpenDiagnostics,
                cacheHealth = cacheHealth,
                onOpenCacheHealth = onOpenCacheHealth,
                isDiscovering = isDiscovering,
                discoveryMessage = discoveryMessage,
                onTriggerDiscover = onTriggerDiscover,
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected
            )
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
                ExploreTrendingSectionHeader(
                    onTriggerDiscover = onTriggerDiscover,
                    onOpenTouristicSources = onOpenTouristicSources,
                    onPlanRouteClick = onPlanRouteClick
                )
            }
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


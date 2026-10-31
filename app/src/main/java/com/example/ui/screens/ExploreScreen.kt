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
    onCategorySelected: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSave: (ScoutSpotEntity) -> Unit,
    onUpvote: (ScoutSpotEntity) -> Unit,
    onToggleAudioLore: (String) -> Unit,
    onSpotClick: (ScoutSpotEntity) -> Unit,
    onAddSpotClick: () -> Unit,
    onRadarClick: () -> Unit,
    onDismissOfflineBanner: () -> Unit,
    onOpenQueueDialog: () -> Unit,
    userLocation: Location? = null,
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    hasLocationPermission: Boolean = true,
    onRequestLocationPermission: () -> Unit = {},
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
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("explore_feed_list"),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                            onSearchQueryChange = onSearchQueryChange
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

                // Instant On-Foot Radar Compass Card
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        OnFootRadarCard(
                            spots = spots,
                            userCoordinates = userCoordinates,
                            onRadarClick = onRadarClick
                        )
                    }
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onRadarClick() }
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

            // 3. Feed Cards or Discover Gems Loading State
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
                        EmptyGemsDiscoverCard(
                            selectedCategory = selectedCategory,
                            onTriggerDiscover = onTriggerDiscover
                        )
                    }
                }
            } else {
                items(spots, key = { it.id }) { spot ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ScoutSpotCard(
                            spot = spot,
                            userCoordinates = userCoordinates,
                            isPlayingAudio = playingAudioId == spot.id,
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

        // Sticky Quick Action Floating Bar above Bottom Nav
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 76.dp, start = 16.dp, end = 16.dp)
                .testTag("sticky_quick_action_bar"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = SurfaceContainerLowest.copy(alpha = 0.95f),
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!hasLocationPermission) {
                                    onRequestLocationPermission()
                                }
                            }
                            .testTag("explore_gps_status_bar"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (hasLocationPermission) Secondary else BrandCoral)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val gpsText = if (hasLocationPermission) {
                            if (userLocation != null) {
                                "GPS Active (±${userLocation.accuracy.toInt()}m)"
                            } else {
                                strings.gpsActive
                            }
                        } else {
                            strings.gpsInactiveTap
                        }
                        Text(
                            text = gpsText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (hasLocationPermission) MaterialTheme.colorScheme.onSurface else BrandCoral,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onAddSpotClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryContainer,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("floating_spot_gem_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.spotAGem,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}





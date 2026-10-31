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

// ----------------- SUB-COMPONENTS -----------------

@Composable
fun FloatingPillSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SecondaryContainer.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.TravelExplore,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        strings.searchPlaceholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("explore_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )

            IconButton(
                onClick = {},
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainerHigh)
                    .clickable {},
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filter",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
fun LiveScrapePulseTicker(
    isDiscovering: Boolean = false,
    discoveryMessage: String? = null,
    onTriggerDiscover: () -> Unit = {}
) {
    val strings = LocalAppStrings.current

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isDiscovering) BrandNight else SurfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTriggerDiscover() }
            .testTag("live_scrape_pulse_ticker")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDiscovering) BrandTeal else Primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDiscovering) {
                        discoveryMessage ?: "✨ Gemini 3.8 Flash discovering gems..."
                    } else {
                        "✨ Tap to discover tourist gems with Gemini AI"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isDiscovering) BrandTeal else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDiscovering) Icons.Default.AutoAwesome else Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (isDiscovering) BrandTeal else Secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (isDiscovering) "Scouting" else "Discover",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isDiscovering) BrandTeal else Secondary
                )
            }
        }
    }
}

@Composable
fun EmptyGemsDiscoverCard(
    selectedCategory: String,
    onTriggerDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "No Spots Found in Vicinity",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Let Gemini 3.8 Flash discover verified landmarks, historic viewpoints, and local culinary gems for your GPS location.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onTriggerDiscover,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryContainer,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.testTag("empty_state_discover_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Discover with Gemini AI",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun CategoryFilterRibbon(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // All Nearby
        item {
            FilterRibbonPill(
                title = strings.allNearby,
                icon = Icons.Default.NearMe,
                isSelected = selectedCategory == "all",
                accentColor = PrimaryContainer,
                onClick = { onCategorySelected("all") }
            )
        }
        // Sights
        item {
            FilterRibbonPill(
                title = strings.sights,
                icon = Icons.Default.PhotoCamera,
                count = "14",
                isSelected = selectedCategory == "sights",
                accentColor = SecondaryContainer,
                textColor = Secondary,
                onClick = { onCategorySelected("sights") }
            )
        }
        // Warm Bakes
        item {
            FilterRibbonPill(
                title = strings.warmBakes,
                icon = Icons.Default.Restaurant,
                showPulse = true,
                isSelected = selectedCategory == "eats",
                accentColor = PrimaryFixed,
                textColor = Primary,
                onClick = { onCategorySelected("eats") }
            )
        }
        // Gothic & History
        item {
            FilterRibbonPill(
                title = strings.gothicHistory,
                icon = Icons.Default.Explore,
                isSelected = selectedCategory == "history",
                accentColor = TertiaryFixed,
                textColor = Tertiary,
                onClick = { onCategorySelected("history") }
            )
        }
        // Secret Spots / Shortcuts
        item {
            FilterRibbonPill(
                title = strings.secretSpots,
                icon = Icons.Default.Verified,
                isSelected = selectedCategory == "shortcut",
                accentColor = SurfaceContainerHigh,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onCategorySelected("shortcut") }
            )
        }
    }
}

@Composable
fun FilterRibbonPill(
    title: String,
    icon: Any,
    count: String? = null,
    showPulse: Boolean = false,
    isSelected: Boolean,
    accentColor: Color,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    val bg = if (isSelected) PrimaryContainer else accentColor
    val fg = if (isSelected) Color.White else textColor

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        shadowElevation = if (isSelected) 6.dp else 1.dp,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("filter_pill_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when (icon) {
                is androidx.compose.ui.graphics.vector.ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = fg
            )
            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = count,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg
                    )
                }
            }
            if (showPulse && !isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Primary)
                )
            }
        }
    }
}

@Composable
fun OnFootRadarCard(
    spots: List<ScoutSpotEntity> = emptyList(),
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    onRadarClick: () -> Unit
) {
    val strings = LocalAppStrings.current

    val infiniteTransition = rememberInfiniteTransition(label = "radarRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotation"
    )

    val top3 = remember(spots, userCoordinates) {
        if (spots.isNotEmpty()) {
            spots.sortedBy { it.computedDistanceMeters(userCoordinates.first, userCoordinates.second) }.take(3)
        } else {
            emptyList()
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRadarClick() }
            .testTag("on_foot_radar_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animated Radar Compass Icon Box
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLow),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient radar ring
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(1.dp, SecondaryFixedDim.copy(alpha = 0.6f), CircleShape)
                        )
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Radar",
                            tint = Secondary,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = strings.onFootRadar,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryFixedDim)
                            )
                        }
                        Text(
                            text = strings.walkingRadiusInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Low queue pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Secondary)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.lowQueue,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Quick Mini Scanned Radar Waypoints dynamically calculated from real GPS coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (top3.isNotEmpty()) {
                    for (spot in top3) {
                        val dist = spot.computedDistanceString(userCoordinates.first, userCoordinates.second)
                        val status = when (spot.category) {
                            "eats" -> "Warm now"
                            "history" -> "Gothic lore"
                            "shortcut" -> "94% verified"
                            else -> "Sunlit free"
                        }
                        val color = when (spot.category) {
                            "eats" -> Primary
                            "history" -> Tertiary
                            else -> Secondary
                        }
                        val shortName = spot.title.split("—", "-").first().trim()
                        WaypointCard(
                            distance = dist,
                            name = shortName,
                            status = status,
                            statusColor = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    WaypointCard(
                        distance = "180m",
                        name = "Nata Fresh",
                        status = "Warm now",
                        statusColor = Primary,
                        modifier = Modifier.weight(1f)
                    )
                    WaypointCard(
                        distance = "350m",
                        name = "Pergola Nook",
                        status = "Sunlit free",
                        statusColor = Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    WaypointCard(
                        distance = "520m",
                        name = "Tile Shortcut",
                        status = "94% verified",
                        statusColor = Tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun WaypointCard(
    distance: String,
    name: String,
    status: String,
    statusColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLow.copy(alpha = 0.8f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = distance,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ScoutSpotCard(
    spot: ScoutSpotEntity,
    userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423),
    isPlayingAudio: Boolean,
    onToggleSave: () -> Unit,
    onUpvote: () -> Unit,
    onToggleAudio: () -> Unit,
    onDirectionsClick: () -> Unit
) {
    val strings = LocalAppStrings.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDirectionsClick() }
            .testTag("spot_card_${spot.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Media Bleed Image + Overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(SurfaceContainer)
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ImageResolver.resolveImageModel(spot.imageUrl, spot.category, spot.title, spot.id))
                        .crossfade(true)
                        .placeholder(ImageResolver.getDrawableResForCategory(spot.category))
                        .error(ImageResolver.getDrawableResForCategory(spot.category))
                        .fallback(ImageResolver.getDrawableResForCategory(spot.category))
                        .build(),
                    contentDescription = spot.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Left: Category & Live Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                when (spot.category) {
                                    "sights" -> SecondaryContainer
                                    "eats" -> PrimaryFixed
                                    "history" -> TertiaryFixed
                                    else -> Secondary
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = spot.badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when (spot.category) {
                                "sights" -> MaterialTheme.colorScheme.onSecondaryContainer
                                "eats" -> Primary
                                "history" -> Tertiary
                                else -> Color.White
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (spot.audioDuration != null) Icons.Default.Headphones else Icons.Default.Verified,
                                contentDescription = null,
                                tint = if (spot.audioDuration != null) Tertiary else Secondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = spot.statusBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Top Right: Floating Bookmark Heart Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f))
                        .clickable { onToggleSave() }
                        .testTag("bookmark_button_${spot.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (spot.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Save",
                        tint = if (spot.isSaved) PrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Bottom Left: Distance & Location Chip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.95f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        val distText = spot.computedDistanceString(userCoordinates.first, userCoordinates.second)
                        Text(
                            text = distText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = spot.locationName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Card Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title and Rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = spot.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (spot.rating != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(SurfaceContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${spot.rating}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Description
                Text(
                    text = spot.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Optional Pro-Tip Callout
                if (spot.proTip != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = spot.proTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Contributor row OR Audio row OR Action bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (spot.contributor != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (spot.contributorAvatar != null) {
                                AsyncImage(
                                    model = spot.contributorAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Secondary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    text = spot.contributor,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (spot.upvotes != null) "Scouted • ${spot.upvotes} upvotes" else "Scouted",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Upvote Button
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SurfaceContainer,
                            modifier = Modifier
                                .clickable { onUpvote() }
                                .testTag("upvote_button_${spot.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = "Upvote",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                if (spot.upvotes != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${spot.upvotes}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else if (spot.audioDuration != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onToggleAudio() }
                                .padding(vertical = 4.dp)
                                .testTag("audio_lore_button_${spot.id}")
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Play Audio",
                                tint = Tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingAudio) "Playing..." else strings.listenQuickLore,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Tertiary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onDirectionsClick() }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = strings.routeDetails,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Secondary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        // Standard dynamic tag + action
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (spot.category == "eats") PrimaryFixed.copy(alpha = 0.5f)
                                    else SecondaryContainer.copy(alpha = 0.4f)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (spot.category == "eats") Icons.Default.NotificationsActive else Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = if (spot.category == "eats") Primary else Secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (spot.category == "eats") "Peak Crispness" else "Quiet sunny nook",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (spot.category == "eats") Primary else Secondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onDirectionsClick() }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = if (spot.category == "eats") strings.orderAhead else strings.directions,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (spot.category == "eats") Primary else Secondary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (spot.category == "eats") Primary else Secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChiadoHeroHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.linearGradient(
                    listOf(Secondary, SecondaryFixedDim, SecondaryContainer)
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag("chiado_hero_header")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SecondaryFixed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.liveGpsSynced,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = strings.chiadoDistrict,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = strings.helloWanderer,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                ),
                color = Color.White
            )

            Text(
                text = strings.whatAreWeScouting,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.95f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Elevated Capsule Search
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                strings.searchCityPlaceholder,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chiado_search_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChiadoCategoryFilterRibbon(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val strings = LocalAppStrings.current

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            ChiadoFilterChip(
                name = strings.allSights,
                count = "82",
                icon = Icons.Default.Explore,
                isSelected = selectedCategory == "all",
                onClick = { onCategorySelected("all") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.history,
                count = "24",
                icon = Icons.Default.Explore,
                isSelected = selectedCategory == "history",
                selectedColor = Tertiary,
                onClick = { onCategorySelected("history") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.streetEats,
                count = "18",
                icon = Icons.Default.Restaurant,
                isSelected = selectedCategory == "eats",
                selectedColor = PrimaryContainer,
                onClick = { onCategorySelected("eats") }
            )
        }
        item {
            ChiadoFilterChip(
                name = strings.hiddenGems,
                count = "12",
                icon = Icons.Default.NearMe,
                isSelected = selectedCategory == "shortcut",
                selectedColor = Secondary,
                onClick = { onCategorySelected("shortcut") }
            )
        }
    }
}



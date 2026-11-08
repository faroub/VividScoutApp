package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RecentSearchEntity
import com.example.data.model.VicinityCacheHealth
import com.example.ui.components.OfflineSyncStatus
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer

/**
 * Header items of the Explore feed (search bar + sync/cache badges + discovery
 * ticker + category ribbon in style 0; Chiado hero header + chips in style 1).
 * Extracted from [ExploreScreen] as a [LazyListScope] extension so the LazyColumn
 * item structure and spacing stay byte-for-byte identical.
 */
internal fun LazyListScope.exploreFeedHeaderSection(
    headerStyle: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    recentSearches: List<RecentSearchEntity>,
    onDeleteRecentSearch: (String) -> Unit,
    onClearAllRecentSearches: () -> Unit,
    syncStatus: OfflineSyncStatus,
    onOpenQueueDialog: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    cacheHealth: VicinityCacheHealth?,
    onOpenCacheHealth: () -> Unit,
    isDiscovering: Boolean,
    discoveryMessage: String?,
    onTriggerDiscover: () -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
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
}

/**
 * "Trending Around You" section title row with the Discover Gems, Sources, and
 * Map View actions. Extracted from [ExploreScreen] with no behavior change.
 */
@Composable
internal fun ExploreTrendingSectionHeader(
    onTriggerDiscover: () -> Unit,
    onOpenTouristicSources: () -> Unit,
    onPlanRouteClick: () -> Unit
) {
    val strings = LocalAppStrings.current
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


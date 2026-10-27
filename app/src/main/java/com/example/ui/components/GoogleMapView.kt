package com.example.ui.components

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerLowest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Interactive Google Maps Compose component with:
 * - Search & Filter Bar (by name, category, distance, discovered, bookmarked)
 * - Map marker pins connected to Room database
 * - Interactive 'Bookmark' button on map markers to save hidden gems locally in Room
 * - Live OpenStreetMap Overpass Web Scraper to pull real viewpoints, historic spots, bakeries & shortcuts
 */
@Composable
fun GoogleMapView(
    spots: List<ScoutSpotEntity>,
    userLat: Double,
    userLng: Double,
    userAccuracy: Float,
    hasLocationPermission: Boolean,
    selectedRadius: String,
    selectedSpot: ScoutSpotEntity?,
    onSpotSelect: (ScoutSpotEntity) -> Unit,
    onSpotClick: (ScoutSpotEntity) -> Unit,
    onToggleSave: (ScoutSpotEntity) -> Unit = {},
    isScrapingOsm: Boolean = false,
    osmScrapeMessage: String? = null,
    onScrapeOsm: () -> Unit = {},
    onDismissOsmMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val strings = LocalAppStrings.current

    var currentMapType by remember { mutableStateOf(MapType.NORMAL) }

    // Search and Filter States
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") } // "all", "sights", "eats", "history", "shortcut"
    var selectedDistance by remember { mutableStateOf("all") } // "all", "500m", "1km", "3km"
    var filterDiscoveredOnly by remember { mutableStateOf(false) }
    var filterBookmarkedOnly by remember { mutableStateOf(false) }
    var isFilterExpanded by remember { mutableStateOf(true) }

    val userLatLng = remember(userLat, userLng) { LatLng(userLat, userLng) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLatLng, 14.8f)
    }

    // Keep camera updated if user location moves significantly and camera is tracking
    LaunchedEffect(userLat, userLng) {
        if (!cameraPositionState.isMoving) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(userLatLng)
            )
        }
    }

    // Radius in meters for the radar scanning circle
    val radiusMeters = remember(selectedRadius) {
        when (selectedRadius) {
            "200m" -> 200.0
            "500m" -> 500.0
            "1km" -> 1000.0
            "3km" -> 3000.0
            else -> 1000.0
        }
    }

    // Multi-factor Filter: Discovered, Bookmarked, Name Query, Category, Distance
    val displayedSpots = remember(
        spots,
        searchQuery,
        selectedCategory,
        selectedDistance,
        filterDiscoveredOnly,
        filterBookmarkedOnly,
        userLat,
        userLng
    ) {
        spots.filter { spot ->
            // 1. Discovered status filter
            val matchesDiscovered = !filterDiscoveredOnly || spot.isDiscovered

            // 2. Bookmarked status filter
            val matchesBookmarked = !filterBookmarkedOnly || spot.isSaved

            // 3. Name / Query search (matches title, neighborhood, tags, description)
            val query = searchQuery.trim().lowercase()
            val matchesName = query.isEmpty() ||
                spot.title.lowercase().contains(query) ||
                spot.tagsRaw.lowercase().contains(query) ||
                spot.description.lowercase().contains(query) ||
                spot.locationName.lowercase().contains(query)

            // 4. Category filter
            val matchesCategory = selectedCategory == "all" ||
                spot.category.equals(selectedCategory, ignoreCase = true)

            // 5. Distance filter
            val distMeters = spot.computedDistanceMeters(userLat, userLng)
            val matchesDistance = when (selectedDistance) {
                "500m" -> distMeters <= 500f
                "1km" -> distMeters <= 1000f
                "3km" -> distMeters <= 3000f
                else -> true
            }

            matchesDiscovered && matchesBookmarked && matchesName && matchesCategory && matchesDistance
        }
    }

    // Animate camera to single match when search narrows to one spot
    LaunchedEffect(displayedSpots.size, searchQuery) {
        if (displayedSpots.size == 1 && searchQuery.isNotBlank()) {
            val single = displayedSpots.first()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(single.latitude, single.longitude), 16f)
            )
            onSpotSelect(single)
        }
    }

    val mapProperties = remember(hasLocationPermission, currentMapType) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapType = currentMapType
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("google_map_container")
    ) {
        // 1. Google Map Composable
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = {
                // Deselect on empty map tap
            }
        ) {
            // Scanning Range Circle around user's location
            Circle(
                center = userLatLng,
                radius = radiusMeters,
                fillColor = BrandTeal.copy(alpha = 0.08f),
                strokeColor = BrandTeal.copy(alpha = 0.45f),
                strokeWidth = 3f
            )

            // Fallback user marker if location permission is not yet granted by system
            if (!hasLocationPermission) {
                Marker(
                    state = rememberMarkerState(position = userLatLng),
                    title = "Default Lisbon Center",
                    snippet = "Chiado Sector 4",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // Pins for matching gems from Room database
            displayedSpots.forEach { spot ->
                val markerPosition = remember(spot.latitude, spot.longitude) {
                    LatLng(spot.latitude, spot.longitude)
                }
                val markerState = rememberMarkerState(key = spot.id, position = markerPosition)

                val markerHue = when {
                    spot.isSaved -> BitmapDescriptorFactory.HUE_MAGENTA // Bookmarked highlight
                    spot.isDiscovered -> BitmapDescriptorFactory.HUE_YELLOW
                    spot.category == "eats" -> BitmapDescriptorFactory.HUE_ROSE
                    spot.category == "history" -> BitmapDescriptorFactory.HUE_VIOLET
                    spot.category == "sights" -> BitmapDescriptorFactory.HUE_CYAN
                    else -> BitmapDescriptorFactory.HUE_AZURE
                }

                val titlePrefix = buildString {
                    if (spot.isSaved) append("♥ [Saved] ")
                    if (spot.isDiscovered) append("★ [Discovered] ")
                }

                Marker(
                    state = markerState,
                    title = "$titlePrefix${spot.title}",
                    snippet = "${spot.category.uppercase()} • ${spot.distance} • ${if (spot.isSaved) "Bookmarked in Room" else "Tap Bookmark to save"}",
                    icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                    onClick = {
                        onSpotSelect(spot)
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLng(markerPosition)
                            )
                        }
                        true
                    }
                )
            }
        }

        // 2. Search & Filter Bar at the Top of the Map
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xF2080B1E),
                border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.45f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_search_bar")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    // Input Row (Icon, Query, Clear, Filter Toggle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = BrandTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = strings.searchMapPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(BrandTeal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("map_search_input")
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("map_search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        val hasActiveFilters = selectedCategory != "all" || selectedDistance != "all" ||
                            filterDiscoveredOnly || filterBookmarkedOnly
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isFilterExpanded || hasActiveFilters) BrandTeal.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .border(
                                    1.dp,
                                    if (hasActiveFilters) BrandTeal else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isFilterExpanded = !isFilterExpanded }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                .testTag("map_filter_expand_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filter options",
                                    tint = if (hasActiveFilters) BrandTeal else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                if (hasActiveFilters) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(BrandCoral)
                                    )
                                }
                            }
                        }
                    }

                    // Filter Chips (Discovered, Bookmarked, Category, Distance)
                    AnimatedVisibility(
                        visible = isFilterExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Row 1: Discovered Toggle + Bookmarked Toggle + Distance Filter Chips
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Discovered Toggle Chip
                                val discoveredCount = spots.count { it.isDiscovered }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (filterDiscoveredOnly) Color(0xFFFEF08A) else Color.White.copy(alpha = 0.1f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (filterDiscoveredOnly) Color(0xFFEAB308) else Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .clickable { filterDiscoveredOnly = !filterDiscoveredOnly }
                                        .testTag("map_filter_discovered_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (filterDiscoveredOnly) Icons.Default.Check else Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (filterDiscoveredOnly) Color(0xFF854D0E) else Color(0xFFFBBF24),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (filterDiscoveredOnly) "★ Discovered ($discoveredCount)" else "Discovered ($discoveredCount)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (filterDiscoveredOnly) Color(0xFF854D0E) else Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // Bookmarked Toggle Chip
                                val bookmarkedCount = spots.count { it.isSaved }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (filterBookmarkedOnly) BrandCoral.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (filterBookmarkedOnly) BrandCoral else Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .clickable { filterBookmarkedOnly = !filterBookmarkedOnly }
                                        .testTag("map_filter_bookmarked_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (filterBookmarkedOnly) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = null,
                                            tint = if (filterBookmarkedOnly) BrandCoral else Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (filterBookmarkedOnly) "♥ Saved ($bookmarkedCount)" else "Saved ($bookmarkedCount)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (filterBookmarkedOnly) BrandCoral else Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // Scrape Live OSM Web Gems Action Chip
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isScrapingOsm) BrandTeal.copy(alpha = 0.35f) else BrandTeal.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, BrandTeal),
                                    modifier = Modifier
                                        .clickable(enabled = !isScrapingOsm) { onScrapeOsm() }
                                        .testTag("map_scrape_osm_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isScrapingOsm) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(11.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Scraping OSM...",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = BrandTeal,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "⚡ Scrape Web Gems",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }

                                // Distance Filter Chips: Any, <500m, <1km, <3km
                                listOf(
                                    "all" to "Any Dist",
                                    "500m" to "< 500m",
                                    "1km" to "< 1km",
                                    "3km" to "< 3km"
                                ).forEach { (distKey, label) ->
                                    val isDistSelected = selectedDistance == distKey
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isDistSelected) BrandTeal else Color.White.copy(alpha = 0.08f))
                                            .border(
                                                1.dp,
                                                if (isDistSelected) BrandTeal else Color.White.copy(alpha = 0.15f),
                                                RoundedCornerShape(999.dp)
                                            )
                                            .clickable { selectedDistance = distKey }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("map_filter_distance_$distKey")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isDistSelected) Color(0xFF080B1E) else Color.White.copy(alpha = 0.85f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // Row 2: Category Filter Chips (All, Sights, Eats, History, Shortcut)
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "all" to "All Categories",
                                    "sights" to strings.sights,
                                    "eats" to strings.streetEats,
                                    "history" to strings.history,
                                    "shortcut" to strings.shortcut
                                ).forEach { (catKey, catLabel) ->
                                    val isCatSelected = selectedCategory == catKey
                                    val catColor = when (catKey) {
                                        "eats" -> BrandCoral
                                        "history" -> Secondary
                                        "sights" -> BrandTeal
                                        "shortcut" -> Color(0xFF38BDF8)
                                        else -> BrandTeal
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isCatSelected) catColor else Color.White.copy(alpha = 0.08f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isCatSelected) catColor else Color.White.copy(alpha = 0.15f),
                                                RoundedCornerShape(999.dp)
                                            )
                                            .clickable { selectedCategory = catKey }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("map_filter_category_$catKey")
                                    ) {
                                        Text(
                                            text = catLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isCatSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live OSM Scraping Message Banner
            AnimatedVisibility(
                visible = osmScrapeMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xF2080B1E),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.6f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissOsmMessage() }
                        .testTag("osm_scrape_message_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = osmScrapeMessage.orEmpty(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Top Status Bar: Live GPS coordinates + Matching Gems Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live GPS Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF080B1E).copy(alpha = 0.85f))
                        .border(1.dp, BrandTeal.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("gmap_telemetry_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (hasLocationPermission) BrandTeal else Color(0xFFFBBF24))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (hasLocationPermission) {
                            String.format(Locale.US, "GPS: %.4f°, %.4f°", userLat, userLng)
                        } else {
                            "Lisbon GPS • Chiado"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                // Match count pill / No results alert
                val matchesText = when {
                    displayedSpots.isEmpty() -> strings.noGemsFoundMessage
                    displayedSpots.size == spots.size -> "${displayedSpots.size} gems visible"
                    else -> "${displayedSpots.size} of ${spots.size} gems matched"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (displayedSpots.isEmpty()) BrandCoral.copy(alpha = 0.9f)
                            else Color(0xFF080B1E).copy(alpha = 0.85f)
                        )
                        .border(
                            1.dp,
                            if (displayedSpots.isEmpty()) BrandCoral else BrandTeal.copy(alpha = 0.35f),
                            RoundedCornerShape(999.dp)
                        )
                        .clickable {
                            if (displayedSpots.isEmpty()) {
                                // Reset filters on tap
                                searchQuery = ""
                                selectedCategory = "all"
                                selectedDistance = "all"
                                filterDiscoveredOnly = false
                                filterBookmarkedOnly = false
                            } else if (displayedSpots.isNotEmpty()) {
                                coroutineScope.launch {
                                    val first = displayedSpots.first()
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(first.latitude, first.longitude),
                                            15.5f
                                        )
                                    )
                                    onSpotSelect(first)
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("gmap_matches_count_pill")
                ) {
                    Text(
                        text = matchesText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 3. Floating Map Controls (Right Side, Center Aligned)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Center on Me
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, BrandTeal.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(userLatLng, 15.5f)
                            )
                        }
                    }
                    .testTag("gmap_center_on_me"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Center on Me",
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Zoom In
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                        }
                    }
                    .testTag("gmap_zoom_in"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Zoom Out
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                        }
                    }
                    .testTag("gmap_zoom_out"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Toggle Map Type (Normal -> Satellite -> Terrain)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable {
                        currentMapType = when (currentMapType) {
                            MapType.NORMAL -> MapType.SATELLITE
                            MapType.SATELLITE -> MapType.TERRAIN
                            else -> MapType.NORMAL
                        }
                    }
                    .testTag("gmap_toggle_type"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Toggle Map Type",
                    tint = if (currentMapType != MapType.NORMAL) Secondary else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 4. Bottom Floating Preview Card of Selected Spot with 'Bookmark' Button
        selectedSpot?.let { spot ->
            val distStr = spot.computedDistanceString(userLat, userLng)
            val walkMins = spot.computedWalkMinutes(userLat, userLng)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceContainerLowest.copy(alpha = 0.97f),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .fillMaxWidth()
                    .clickable { onSpotClick(spot) }
                    .testTag("selected_gmap_spot_card")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(54.dp)) {
                        AsyncImage(
                            model = spot.imageUrl,
                            contentDescription = spot.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                        // Discovered badge
                        if (spot.isDiscovered) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEAB308)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Discovered",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        // Bookmarked badge on photo
                        if (spot.isSaved) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(BrandCoral),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Saved in Room DB",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = spot.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (spot.isSaved) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "♥ ${strings.bookmarkedLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrandCoral,
                                    fontSize = 9.sp
                                )
                            } else if (spot.isDiscovered) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "★ Discovered",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFD97706),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null,
                                tint = Secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$distStr • $walkMins min",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Secondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${spot.statusBadge}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 'Bookmark' Button on the Map Marker Card
                    IconButton(
                        onClick = { onToggleSave(spot) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (spot.isSaved) BrandCoral.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .border(
                                1.dp,
                                if (spot.isSaved) BrandCoral else Color.Gray.copy(alpha = 0.25f),
                                CircleShape
                            )
                            .testTag("map_marker_bookmark_button")
                            .testTag("map_marker_bookmark_button_${spot.id}")
                    ) {
                        Icon(
                            imageVector = if (spot.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (spot.isSaved) "Remove from Bookmarks" else "Bookmark hidden gem locally",
                            tint = if (spot.isSaved) BrandCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Button(
                        onClick = { onSpotClick(spot) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryContainer,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("gmap_spot_scout_button")
                    ) {
                        Text("Scout", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.location.Location
import com.example.ui.components.GoogleMapView
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
import androidx.compose.material.icons.filled.Bookmark
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
                        // Google Maps Compose interactive view with user location and Room gems
                        GoogleMapView(
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
                                    text = "Live OSM Web Scraper",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Scrape viewpoints, bakeries & shortcuts from OpenStreetMap into Room",
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
                                    "Scraping...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Scrape",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    if (osmScrapeMessage != null) {
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
 * Interactive Vector Map Canvas for Lisbon
 */
@Composable
fun MapCanvasView(
    spots: List<ScoutSpotEntity>,
    userLat: Double,
    userLng: Double,
    userAccuracy: Float,
    beaconPulse: Float,
    zoom: Float,
    offset: Offset,
    selectedSpot: ScoutSpotEntity?,
    onOffsetChange: (Offset) -> Unit,
    onSpotSelect: (ScoutSpotEntity) -> Unit
) {
    // Conversion factor from GPS lat/lng offset to canvas dp
    // Around Lisbon (lat 38.7), 0.01 deg lat ~ 1110 meters; 0.01 deg lon ~ 870 meters.
    val baseScale = 25000f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, _, _ ->
                    onOffsetChange(pan)
                }
            }
            .testTag("map_canvas_container")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2 + offset.x, size.height / 2 + offset.y)

            // 1. Map Base / Dark Background Grid
            drawRect(color = Color(0xFF0A0F24))

            // Grid lines
            val step = 40f * zoom
            var x = (offset.x % step)
            while (x < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = (offset.y % step)
            while (y < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }

            // 2. Tagus River (Tejo) stylized blue water contour at South
            val riverPath = Path().apply {
                moveTo(-200f, size.height * 0.85f + offset.y * 0.3f)
                cubicTo(
                    size.width * 0.3f, size.height * 0.78f + offset.y * 0.3f,
                    size.width * 0.7f, size.height * 0.88f + offset.y * 0.3f,
                    size.width + 200f, size.height * 0.80f + offset.y * 0.3f
                )
                lineTo(size.width + 200f, size.height + 200f)
                lineTo(-200f, size.height + 200f)
                close()
            }
            drawPath(path = riverPath, color = Color(0xFF0F3254))

            // 3. User Accuracy Halo Ring
            val accuracyRadius = (userAccuracy * 2.5f * zoom).coerceIn(16f, 90f)
            drawCircle(
                color = BrandTeal.copy(alpha = 0.15f),
                radius = accuracyRadius * beaconPulse,
                center = center
            )
            drawCircle(
                color = BrandTeal.copy(alpha = 0.35f),
                radius = accuracyRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // 4. User GPS Marker (Center Blue/Teal Beacon)
            drawCircle(
                color = Color.White,
                radius = 9f,
                center = center
            )
            drawCircle(
                color = BrandTeal,
                radius = 7f,
                center = center
            )
            // Heading indicator cone pointing north-northeast
            drawPath(
                path = Path().apply {
                    moveTo(center.x, center.y - 18f)
                    lineTo(center.x - 6f, center.y - 7f)
                    lineTo(center.x + 6f, center.y - 7f)
                    close()
                },
                color = BrandTeal
            )

            // 5. Plot Gem Pins
            spots.forEach { spot ->
                val dLng = (spot.longitude - userLng).toFloat()
                val dLat = (spot.latitude - userLat).toFloat()

                val pinX = center.x + (dLng * baseScale * zoom)
                val pinY = center.y - (dLat * baseScale * zoom)

                val isSelected = selectedSpot?.id == spot.id
                val pinColor = when (spot.category) {
                    "eats" -> BrandCoral
                    "history" -> Color(0xFFC084FC)
                    "sights" -> Color(0xFF10B981)
                    else -> BrandTeal
                }

                // Selection glow if selected
                if (isSelected) {
                    drawCircle(
                        color = pinColor.copy(alpha = 0.4f),
                        radius = 24f,
                        center = Offset(pinX, pinY)
                    )
                }

                // Pin connector stem
                drawLine(
                    color = pinColor,
                    start = Offset(pinX, pinY),
                    end = Offset(pinX, pinY + 12f),
                    strokeWidth = 2.5f
                )

                // Pin Head
                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = Offset(pinX, pinY)
                )
                drawCircle(
                    color = pinColor,
                    radius = 9.5f,
                    center = Offset(pinX, pinY)
                )
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

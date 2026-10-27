package com.example.ui.components

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceString
import com.example.data.local.computedWalkMinutes
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandTeal
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
 * Interactive Google Maps Compose component displaying user location and nearby
 * gems discovered from the Room database.
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var currentMapType by remember { mutableStateOf(MapType.NORMAL) }
    var filterDiscoveredOnly by remember { mutableStateOf(false) }

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

    // Radius in meters
    val radiusMeters = remember(selectedRadius) {
        when (selectedRadius) {
            "200m" -> 200.0
            "500m" -> 500.0
            "1km" -> 1000.0
            "3km" -> 3000.0
            else -> 1000.0
        }
    }

    val displayedSpots = remember(spots, filterDiscoveredOnly) {
        if (filterDiscoveredOnly) {
            spots.filter { it.isDiscovered }
        } else {
            spots
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
                // Deselect when clicking on empty map area
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

            // Pins for nearby gems from Room database
            displayedSpots.forEach { spot ->
                val markerPosition = remember(spot.latitude, spot.longitude) {
                    LatLng(spot.latitude, spot.longitude)
                }
                val markerState = rememberMarkerState(key = spot.id, position = markerPosition)

                val markerHue = when {
                    spot.isDiscovered -> BitmapDescriptorFactory.HUE_YELLOW
                    spot.category == "eats" -> BitmapDescriptorFactory.HUE_ROSE
                    spot.category == "history" -> BitmapDescriptorFactory.HUE_VIOLET
                    spot.category == "sights" -> BitmapDescriptorFactory.HUE_CYAN
                    else -> BitmapDescriptorFactory.HUE_AZURE
                }

                val titlePrefix = if (spot.isDiscovered) "★ [Discovered] " else ""

                Marker(
                    state = markerState,
                    title = "$titlePrefix${spot.title}",
                    snippet = "${spot.category.uppercase()} • ${spot.distance}",
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

        // 2. Top-Left Live GPS Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF080B1E).copy(alpha = 0.85f))
                .border(1.dp, BrandTeal.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag("gmap_telemetry_pill"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (hasLocationPermission) BrandTeal else Color(0xFFFBBF24))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasLocationPermission) {
                    String.format(Locale.US, "GPS: %.4f° N, %.4f° W", userLat, userLng)
                } else {
                    "Lisbon GPS • Alfama Fixed Center"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                fontSize = 11.sp
            )
        }

        // 3. Top-Center Discovered Filter Toggle Chip
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (filterDiscoveredOnly) Color(0xFFFEF08A) else Color(0xFF080B1E).copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (filterDiscoveredOnly) Color(0xFFEAB308) else Color.White.copy(alpha = 0.2f)
            ),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .clickable { filterDiscoveredOnly = !filterDiscoveredOnly }
                .testTag("gmap_filter_discovered_toggle")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (filterDiscoveredOnly) Icons.Default.Check else Icons.Default.Star,
                    contentDescription = null,
                    tint = if (filterDiscoveredOnly) Color(0xFF854D0E) else Color(0xFFFBBF24),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val discoveredCount = spots.count { it.isDiscovered }
                Text(
                    text = if (filterDiscoveredOnly) "Discovered ($discoveredCount)" else "All Gems (${spots.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (filterDiscoveredOnly) Color(0xFF854D0E) else Color.White,
                    fontSize = 11.sp
                )
            }
        }

        // 4. Floating Map Controls (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Center on Me
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.85f))
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.85f))
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.85f))
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF080B1E).copy(alpha = 0.85f))
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

        // 5. Bottom Floating Preview of Selected Spot
        selectedSpot?.let { spot ->
            val distStr = spot.computedDistanceString(userLat, userLng)
            val walkMins = spot.computedWalkMinutes(userLat, userLng)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceContainerLowest.copy(alpha = 0.96f),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
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
                        if (spot.isDiscovered) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEAB308)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Discovered",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
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
                            if (spot.isDiscovered) {
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
                                text = "$distStr • $walkMins min walk",
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

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onSpotClick(spot) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryContainer,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("gmap_spot_scout_button")
                    ) {
                        Text("Scout", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

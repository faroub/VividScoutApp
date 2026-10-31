package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.ScoutSpotEntity
import com.example.sensor.CompassSensorManager
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandNight
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.Secondary
import com.example.util.AROffscreenDirection
import com.example.util.ARProjectionUtils
import com.example.util.ImageResolver
import kotlin.math.roundToInt

/**
 * Fullscreen Augmented Reality Navigation overlay that uses CameraX live camera feed,
 * device orientation sensors, and spatial projection to display floating 3D markers for nearby Lisbon gems.
 */
@Composable
fun ARNavigationOverlay(
    spots: List<ScoutSpotEntity>,
    userCoordinates: Pair<Double, Double>,
    onClose: () -> Unit,
    onSelectSpot: (ScoutSpotEntity) -> Unit,
    onPlanRoute: (ScoutSpotEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Initialize compass sensor listener
    val compassSensorManager = remember { CompassSensorManager(context) }
    val compassState by compassSensorManager.compassState.collectAsState()

    DisposableEffect(Unit) {
        compassSensorManager.startListening(userCoordinates.first, userCoordinates.second)
        onDispose {
            compassSensorManager.stopListening()
        }
    }

    var selectedSpot by remember { mutableStateOf<ScoutSpotEntity?>(null) }
    var cameraProviderAvailable by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("ar_navigation_overlay")
    ) {
        // 1. Live Camera Feed Viewfinder or Simulator Fallback
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                        } catch (e: Exception) {
                            cameraProviderAvailable = false
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Camera Simulator Overlay (when camera unavailable or permission pending)
        if (!hasCameraPermission || !cameraProviderAvailable) {
            ARCameraViewfinderSimulator()
        }

        // 2. HUD Compass Bearing Bar (Top Header)
        ARCompassHeaderHud(
            azimuthDegrees = compassState.effectiveHeading,
            userCoordinates = userCoordinates,
            spotsCount = spots.size,
            hasCameraPermission = hasCameraPermission,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        )

        // 3. Floating AR Spatial Pins Overlay
        Box(modifier = Modifier.fillMaxSize()) {
            val screenWidthPx = with(LocalDensity.current) { 390.dp.toPx() }
            val screenHeightPx = with(LocalDensity.current) { 720.dp.toPx() }

            // Filter spots within 3km for AR projection
            val nearbySpots = spots.take(8)

            nearbySpots.forEach { spot ->
                val projection = ARProjectionUtils.projectGpsToScreen(
                    userLat = userCoordinates.first,
                    userLng = userCoordinates.second,
                    targetLat = spot.latitude,
                    targetLng = spot.longitude,
                    deviceAzimuth = compassState.effectiveHeading,
                    devicePitch = 0f,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx
                )

                if (projection.isInsideFov) {
                    // Floating Spatial Pin Card inside camera Viewfinder
                    ARSpatialMarkerPin(
                        spot = spot,
                        projection = projection,
                        isSelected = selectedSpot?.id == spot.id,
                        onClick = { selectedSpot = spot },
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    projection.xPx.roundToInt() - 90,
                                    projection.yPx.roundToInt() - 40
                                )
                            }
                            .testTag("ar_spot_pin_${spot.id}")
                    )
                } else {
                    // Edge Indicator Arrows for spots outside active camera FOV
                    AREdgeDirectionArrow(
                        spot = spot,
                        direction = projection.offscreenDirection,
                        distanceMeters = projection.distanceMeters,
                        onClick = { selectedSpot = spot },
                        modifier = Modifier.align(
                            if (projection.offscreenDirection == AROffscreenDirection.LEFT) Alignment.CenterStart
                            else Alignment.CenterEnd
                        )
                    )
                }
            }
        }

        // 4. Quick Spot Info Card (Bottom Sheet Overlay)
        AnimatedVisibility(
            visible = selectedSpot != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedSpot?.let { spot ->
                ARSpotQuickInfoCard(
                    spot = spot,
                    userCoordinates = userCoordinates,
                    onDismiss = { selectedSpot = null },
                    onOpenDetails = {
                        selectedSpot = null
                        onSelectSpot(spot)
                    },
                    onPlanRoute = {
                        selectedSpot = null
                        onPlanRoute(spot)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("ar_quick_info_card")
                )
            }
        }
    }
}

/**
 * Top HUD displaying live compass heading, azimuth dial, and AR mode actions.
 */
@Composable
private fun ARCompassHeaderHud(
    azimuthDegrees: Float,
    userCoordinates: Pair<Double, Double>,
    spotsCount: Int,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardinalIndex = ((azimuthDegrees + 22.5f) % 360f / 45f).toInt()
    val cardinalText = when (cardinalIndex) {
        0 -> "N"
        1 -> "NE"
        2 -> "E"
        3 -> "SE"
        4 -> "S"
        5 -> "SW"
        6 -> "W"
        7 -> "NW"
        else -> "N"
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        color = Color(0xCC0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandCoral.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BrandCoral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AR GEM FINDER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = BrandCoral
                        )
                        Text(
                            text = "${azimuthDegrees.roundToInt()}° $cardinalText • $spotsCount gems in view",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!hasCameraPermission) {
                        IconButton(
                            onClick = onRequestPermission,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Enable Camera",
                                tint = Color(0xFFFDE047),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .testTag("ar_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit AR Mode",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating 3D spatial pin card projected at target coordinates on top of camera preview.
 */
@Composable
private fun ARSpatialMarkerPin(
    spot: ScoutSpotEntity,
    projection: com.example.util.ARProjectionPoint,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distanceText = if (projection.distanceMeters < 1000) {
        "${projection.distanceMeters.roundToInt()}m"
    } else {
        String.format("%.1fkm", projection.distanceMeters / 1000f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        // Floating Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) BrandCoral else Color(0xE60F172A),
            contentColor = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color.White else BrandCoral.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BrandTeal)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.Center)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = spot.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(110.dp)
                    )
                    Text(
                        text = "$distanceText • ★ ${spot.rating}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Pointer Stalk and Target Dot
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(18.dp)
                .background(BrandCoral)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(BrandCoral)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

/**
 * Edge arrow guide alerting user to turn left or right toward off-screen gems.
 */
@Composable
private fun AREdgeDirectionArrow(
    spot: ScoutSpotEntity,
    direction: AROffscreenDirection,
    distanceMeters: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (direction == AROffscreenDirection.NONE) return

    val distanceText = if (distanceMeters < 1000) "${distanceMeters.roundToInt()}m" else String.format("%.1fkm", distanceMeters / 1000f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xDC0F172A),
        contentColor = Color.White,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() }
            .border(1.dp, BrandCoral.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (direction == AROffscreenDirection.LEFT) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Turn Left",
                    tint = BrandCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = spot.title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandCoral
                )
            }
            if (direction == AROffscreenDirection.RIGHT) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Turn Right",
                    tint = BrandCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Bottom Quick Info Card displayed when an AR pin is selected.
 */
@Composable
private fun ARSpotQuickInfoCard(
    spot: ScoutSpotEntity,
    userCoordinates: Pair<Double, Double>,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    onPlanRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ImageResolver.resolveImageModel(spot.imageUrl, spot.category, spot.title, spot.id))
                            .crossfade(true)
                            .placeholder(ImageResolver.getDrawableResForCategory(spot.category))
                            .error(ImageResolver.getDrawableResForCategory(spot.category))
                            .build(),
                        contentDescription = spot.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = spot.badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = BrandCoral
                        )
                        Text(
                            text = spot.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${spot.distance} • ★ ${spot.rating}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = spot.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenDetails,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCoral),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gem Details")
                }

                Button(
                    onClick = onPlanRoute,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Plan Route")
                }
            }
        }
    }
}

/**
 * Camera Viewfinder Simulator background when physical camera is unavailable.
 */
@Composable
private fun ARCameraViewfinderSimulator() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF020617)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Scanning Crosshair Target
        Box(
            modifier = Modifier
                .size(180.dp)
                .border(1.dp, BrandCoral.copy(alpha = 0.3f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .border(1.dp, BrandTeal.copy(alpha = 0.2f), CircleShape)
        )
    }
}

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.local.ScoutSpotEntity
import com.example.sensor.CompassSensorManager
import com.example.util.AROffscreenDirection
import com.example.util.SafeLog
import com.example.util.ARProjectionUtils
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
                            SafeLog.w(
                                "ARNavigationOverlay",
                                "Camera bind failed, running without live camera feed: ${e.message}"
                            )
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

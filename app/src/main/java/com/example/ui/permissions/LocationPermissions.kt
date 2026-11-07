package com.example.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.viewmodel.LocationViewModel
import com.example.util.SafeLog

/** The permissions requested by the system location-permission dialog. */
val LOCATION_PERMISSIONS: Array<String> = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/**
 * The next step a "request location permission" affordance should take, given the
 * current permission state. Extracted as a pure decision so it is unit-testable.
 */
enum class LocationPermissionAction { Refresh, ShowRationale, ShowSettings }

object LocationPermissionDecisions {

    /**
     * Already granted: refresh state. Denied twice: the system dialog no longer
     * helps, so offer the app-settings path. Otherwise: show the rationale dialog
     * before the next system request.
     */
    fun nextAction(hasLocationPermission: Boolean, locationDeniedCount: Int): LocationPermissionAction =
        when {
            hasLocationPermission -> LocationPermissionAction.Refresh
            locationDeniedCount >= 2 -> LocationPermissionAction.ShowSettings
            else -> LocationPermissionAction.ShowRationale
        }
}

/**
 * Actions for asking the user for location permission. Owns the system permission
 * launcher, the startup prompt, and the refresh-on-resume lifecycle observer so the
 * activity no longer mixes permission plumbing with its own composition.
 */
class LocationPermissionActions(
    val launchSystemRequest: () -> Unit,
    val openAppSettings: () -> Unit,
    val requestLocationPermission: () -> Unit
)

@Composable
fun rememberLocationPermissionActions(
    locationViewModel: LocationViewModel,
    hasLocationPermission: Boolean,
    locationDeniedCount: Int
): LocationPermissionActions {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationViewModel.onPermissionResult(fineGranted, coarseGranted)
    }

    // Automatically refresh permission state when returning to the app from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationViewModel.refreshLocationPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Automatically check and prompt for location permissions on startup
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    val openAppSettings: () -> Unit = {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            // Safe recovery: System Settings activity launch failed, log telemetry
            SafeLog.w("MainActivity", "Failed to launch application details settings intent", e)
        }
    }

    return LocationPermissionActions(
        launchSystemRequest = { locationPermissionLauncher.launch(LOCATION_PERMISSIONS) },
        openAppSettings = openAppSettings,
        requestLocationPermission = {
            when (LocationPermissionDecisions.nextAction(hasLocationPermission, locationDeniedCount)) {
                LocationPermissionAction.Refresh -> locationViewModel.refreshLocationPermissions()
                LocationPermissionAction.ShowSettings -> locationViewModel.openLocationSettingsDialog()
                LocationPermissionAction.ShowRationale -> locationViewModel.openLocationRationaleDialog()
            }
        }
    )
}

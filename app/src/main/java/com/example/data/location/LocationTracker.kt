package com.example.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages real-time user coordinates tracking using Google Play Services Location API
 * (FusedLocationProviderClient).
 */
class LocationTracker(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Fallback default coordinates (Lisbon center: Chiado / Alfama)
    val defaultLocation: Location = Location("DefaultProvider").apply {
        latitude = 38.7112
        longitude = -9.1423
        accuracy = 5f
    }

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _hasPermission = MutableStateFlow(hasPermissionInitially())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _isPrecise = MutableStateFlow(hasFinePermissionInitially())
    val isPrecise: StateFlow<Boolean> = _isPrecise.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                _currentLocation.value = loc
            }
        }
    }

    private fun hasFinePermissionInitially(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarsePermissionInitially(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPermissionInitially(): Boolean {
        return hasFinePermissionInitially() || hasCoarsePermissionInitially()
    }

    fun hasFinePermission(): Boolean = hasFinePermissionInitially()

    fun hasCoarsePermission(): Boolean = hasCoarsePermissionInitially()

    fun checkHasPermission(): Boolean {
        val fine = hasFinePermissionInitially()
        val coarse = hasCoarsePermissionInitially()
        val granted = fine || coarse
        _hasPermission.value = granted
        _isPrecise.value = fine
        return granted
    }

    fun refreshPermissionState(): Boolean {
        val granted = checkHasPermission()
        if (granted && !_isTracking.value) {
            startTracking()
        } else if (!granted && _isTracking.value) {
            stopTracking()
        }
        return granted
    }

    fun onPermissionResult(fineGranted: Boolean, coarseGranted: Boolean) {
        val granted = fineGranted || coarseGranted
        _hasPermission.value = granted
        _isPrecise.value = fineGranted
        if (granted) {
            startTracking()
        } else {
            stopTracking()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        onPermissionResult(fineGranted = granted, coarseGranted = granted)
    }

    fun startTracking() {
        if (!_hasPermission.value && !checkHasPermission()) {
            return
        }

        try {
            // Immediately request last known location for rapid initial render
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    _currentLocation.value = loc
                }
            }

            // High accuracy continuous updates (5s intervals)
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L
            ).apply {
                setMinUpdateIntervalMillis(2000L)
                setMinUpdateDistanceMeters(1f)
            }.build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            _isTracking.value = true
        } catch (_: Exception) {
            _isTracking.value = false
        }
    }

    fun stopTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {
        } finally {
            _isTracking.value = false
        }
    }

    companion object {
        /**
         * Calculates distance in meters between two coordinates using Android Location API.
         */
        fun distanceBetween(
            startLat: Double,
            startLng: Double,
            endLat: Double,
            endLng: Double
        ): Float {
            return try {
                val results = FloatArray(1)
                Location.distanceBetween(startLat, startLng, endLat, endLng, results)
                results[0]
            } catch (_: Throwable) {
                // Fallback Haversine geodesic calculation for local JVM unit tests
                val r = 6371000.0 // Earth radius in meters
                val dLat = Math.toRadians(endLat - startLat)
                val dLng = Math.toRadians(endLng - startLng)
                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                (r * c).toFloat()
            }
        }

        /**
         * Formats distance into friendly text (e.g., "180m walk" or "1.2 km away").
         */
        fun formatDistance(meters: Float): String {
            return if (meters < 1000f) {
                "${meters.toInt()}m walk"
            } else {
                String.format(Locale.US, "%.1f km away", meters / 1000f)
            }
        }

        /**
         * Calculates approximate walking time in minutes (assuming 80m / min ~ 4.8 km/h).
         */
        fun calculateWalkMinutes(meters: Float): Int {
            val mins = (meters / 80f).toInt()
            return if (mins <= 0) 1 else mins
        }
    }
}

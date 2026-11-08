package com.example.ui.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.location.LocationTracker
import com.example.data.model.GlobalCityCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GpsAcquisitionStatus {
    data object CheckingPermissions : GpsAcquisitionStatus
    data object AcquiringLock : GpsAcquisitionStatus
    data class Locked(val lat: Double, val lng: Double, val cityName: String) : GpsAcquisitionStatus
    data object PermissionRequired : GpsAcquisitionStatus
    data object GpsDisabled : GpsAcquisitionStatus
}

data class LocationUiState(
    val userLocation: Location? = null,
    val userCoordinates: Pair<Double, Double>? = null,
    val gpsStatus: GpsAcquisitionStatus = GpsAcquisitionStatus.CheckingPermissions,
    val hasLocationPermission: Boolean = false,
    val isPreciseLocation: Boolean = false,
    val isUsingGpsLocation: Boolean = false,
    val showLocationRationaleDialog: Boolean = false,
    val showLocationSettingsDialog: Boolean = false,
    val locationDeniedCount: Int = 0,
    val detectedCityName: String? = null
) {
    val isLocked: Boolean get() = gpsStatus is GpsAcquisitionStatus.Locked
    val safeCoordinates: Pair<Double, Double> get() = userCoordinates ?: Pair(0.0, 0.0)
}

class LocationViewModel(
    val locationTracker: LocationTracker
) : ViewModel() {

    private val _hasLocationPermission = MutableStateFlow(locationTracker.hasPermission.value)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _isPreciseLocation = MutableStateFlow(locationTracker.isPrecise.value)
    val isPreciseLocation: StateFlow<Boolean> = _isPreciseLocation.asStateFlow()

    private val _isUsingGpsLocation = MutableStateFlow(locationTracker.hasPermission.value)
    val isUsingGpsLocation: StateFlow<Boolean> = _isUsingGpsLocation.asStateFlow()

    private val _gpsStatus = MutableStateFlow<GpsAcquisitionStatus>(GpsAcquisitionStatus.CheckingPermissions)
    val gpsStatus: StateFlow<GpsAcquisitionStatus> = _gpsStatus.asStateFlow()

    private val _showLocationRationaleDialog = MutableStateFlow(false)
    val showLocationRationaleDialog: StateFlow<Boolean> = _showLocationRationaleDialog.asStateFlow()

    private val _showLocationSettingsDialog = MutableStateFlow(false)
    val showLocationSettingsDialog: StateFlow<Boolean> = _showLocationSettingsDialog.asStateFlow()

    private val _locationDeniedCount = MutableStateFlow(0)
    val locationDeniedCount: StateFlow<Int> = _locationDeniedCount.asStateFlow()

    val userLocation: StateFlow<Location?> = locationTracker.currentLocation

    private val _manualCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    private val _detectedCityName = MutableStateFlow<String?>(null)

    val userCoordinates: StateFlow<Pair<Double, Double>?> = combine(
        userLocation,
        _manualCoordinates,
        _isUsingGpsLocation
    ) { loc, manual, isGps ->
        when {
            isGps && loc != null -> Pair(loc.latitude, loc.longitude)
            manual != null -> manual
            loc != null -> Pair(loc.latitude, loc.longitude)
            else -> null // Strictly no default fallback location loaded first!
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * True only when the app is running on the built-in default location (Lisbon) because
     * device location is unavailable and the user has not picked a city manually. This is
     * the "silent fallback" case that must be surfaced to the user with an explicit banner.
     */
    val isUsingDefaultFallbackLocation: StateFlow<Boolean> = combine(
        _isUsingGpsLocation,
        _manualCoordinates
    ) { isGps, manual -> !isGps && manual == null }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            !locationTracker.hasPermission.value
        )

    val uiState: StateFlow<LocationUiState> = combine(
        userLocation,
        userCoordinates,
        _hasLocationPermission,
        _isPreciseLocation,
        _isUsingGpsLocation,
        _gpsStatus,
        _showLocationRationaleDialog,
        _showLocationSettingsDialog,
        _locationDeniedCount,
        _detectedCityName
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        LocationUiState(
            userLocation = values[0] as? Location,
            userCoordinates = values[1] as? Pair<Double, Double>,
            hasLocationPermission = values[2] as Boolean,
            isPreciseLocation = values[3] as Boolean,
            isUsingGpsLocation = values[4] as Boolean,
            gpsStatus = values[5] as GpsAcquisitionStatus,
            showLocationRationaleDialog = values[6] as Boolean,
            showLocationSettingsDialog = values[7] as Boolean,
            locationDeniedCount = values[8] as Int,
            detectedCityName = values[9] as? String
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationUiState())

    init {
        val initialFine = locationTracker.hasFinePermission()
        val initialCoarse = locationTracker.hasCoarsePermission()
        val hasInitialPerm = initialFine || initialCoarse

        if (hasInitialPerm) {
            _hasLocationPermission.value = true
            _isPreciseLocation.value = initialFine
            _isUsingGpsLocation.value = true
            if (!locationTracker.isGpsProviderEnabled()) {
                _gpsStatus.value = GpsAcquisitionStatus.GpsDisabled
            } else {
                _gpsStatus.value = GpsAcquisitionStatus.AcquiringLock
                locationTracker.startTracking()
            }
        } else {
            _hasLocationPermission.value = false
            _isPreciseLocation.value = false
            _isUsingGpsLocation.value = false
            _gpsStatus.value = GpsAcquisitionStatus.PermissionRequired
        }

        // Collect location updates to transition into Locked state
        viewModelScope.launch {
            userLocation.collect { loc ->
                if (loc != null && _isUsingGpsLocation.value) {
                    val cityName = GlobalCityCatalog.findNearestCityName(loc.latitude, loc.longitude)
                    _detectedCityName.value = cityName
                    _gpsStatus.value = GpsAcquisitionStatus.Locked(loc.latitude, loc.longitude, cityName)
                }
            }
        }
    }

    fun onPermissionResult(fineGranted: Boolean, coarseGranted: Boolean) {
        if (fineGranted || coarseGranted) {
            _hasLocationPermission.value = true
            _isPreciseLocation.value = fineGranted
            _isUsingGpsLocation.value = true
            _showLocationRationaleDialog.value = false
            _showLocationSettingsDialog.value = false
            _locationDeniedCount.value = 0
            locationTracker.onPermissionResult(fineGranted, coarseGranted)

            if (!locationTracker.isGpsProviderEnabled()) {
                _gpsStatus.value = GpsAcquisitionStatus.GpsDisabled
            } else {
                _gpsStatus.value = GpsAcquisitionStatus.AcquiringLock
                locationTracker.startTracking()
            }
        } else {
            _hasLocationPermission.value = false
            _isPreciseLocation.value = false
            _isUsingGpsLocation.value = false
            _gpsStatus.value = GpsAcquisitionStatus.PermissionRequired
            locationTracker.onPermissionResult(false, false)
            val currentDenied = _locationDeniedCount.value + 1
            _locationDeniedCount.value = currentDenied
            if (currentDenied >= 2) {
                _showLocationSettingsDialog.value = true
            } else {
                _showLocationRationaleDialog.value = true
            }
        }
    }

    fun refreshLocationPermissions() {
        val fineGranted = locationTracker.hasFinePermission()
        val coarseGranted = locationTracker.hasCoarsePermission()
        if (fineGranted || coarseGranted) {
            _hasLocationPermission.value = true
            _isPreciseLocation.value = fineGranted
            _isUsingGpsLocation.value = true
            if (!locationTracker.isGpsProviderEnabled()) {
                _gpsStatus.value = GpsAcquisitionStatus.GpsDisabled
            } else {
                _gpsStatus.value = GpsAcquisitionStatus.AcquiringLock
                locationTracker.startTracking()
            }
        }
    }

    fun retryLocationAcquisition() {
        val fineGranted = locationTracker.hasFinePermission()
        val coarseGranted = locationTracker.hasCoarsePermission()
        val hasPerm = fineGranted || coarseGranted
        _hasLocationPermission.value = hasPerm
        _isPreciseLocation.value = fineGranted

        if (!hasPerm) {
            _gpsStatus.value = GpsAcquisitionStatus.PermissionRequired
            return
        }

        if (!locationTracker.isGpsProviderEnabled()) {
            _gpsStatus.value = GpsAcquisitionStatus.GpsDisabled
            return
        }

        _isUsingGpsLocation.value = true
        _gpsStatus.value = GpsAcquisitionStatus.AcquiringLock
        locationTracker.startTracking()
    }

    fun openLocationSettingsDialog() {
        _showLocationSettingsDialog.value = true
    }

    fun openLocationRationaleDialog() {
        _showLocationRationaleDialog.value = true
    }

    fun dismissLocationRationale() {
        _showLocationRationaleDialog.value = false
    }

    fun dismissLocationSettings() {
        _showLocationSettingsDialog.value = false
    }

    fun setManualCoordinates(lat: Double, lng: Double, cityName: String? = null) {
        _manualCoordinates.value = Pair(lat, lng)
        _isUsingGpsLocation.value = false
        val resolvedName = cityName ?: GlobalCityCatalog.findNearestCityName(lat, lng)
        _detectedCityName.value = resolvedName
        _gpsStatus.value = GpsAcquisitionStatus.Locked(lat, lng, resolvedName)
    }

    fun enableGpsMode() {
        if (_hasLocationPermission.value) {
            _isUsingGpsLocation.value = true
            _manualCoordinates.value = null
            _gpsStatus.value = GpsAcquisitionStatus.AcquiringLock
            locationTracker.startTracking()
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
    }
}

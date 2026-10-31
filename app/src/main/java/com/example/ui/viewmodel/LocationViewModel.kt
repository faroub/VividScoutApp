package com.example.ui.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LocationUiState(
    val userLocation: Location? = null,
    val userCoordinates: Pair<Double, Double> = Pair(38.7112, -9.1423), // Default Lisbon (Miradouro de Santa Luzia)
    val hasLocationPermission: Boolean = false,
    val isPreciseLocation: Boolean = false,
    val isUsingGpsLocation: Boolean = false,
    val showLocationRationaleDialog: Boolean = false,
    val showLocationSettingsDialog: Boolean = false,
    val locationDeniedCount: Int = 0
)

class LocationViewModel(
    val locationTracker: LocationTracker
) : ViewModel() {

    private val _hasLocationPermission = MutableStateFlow(locationTracker.hasPermission.value)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _isPreciseLocation = MutableStateFlow(locationTracker.isPrecise.value)
    val isPreciseLocation: StateFlow<Boolean> = _isPreciseLocation.asStateFlow()

    private val _isUsingGpsLocation = MutableStateFlow(locationTracker.hasPermission.value)
    val isUsingGpsLocation: StateFlow<Boolean> = _isUsingGpsLocation.asStateFlow()

    private val _showLocationRationaleDialog = MutableStateFlow(false)
    val showLocationRationaleDialog: StateFlow<Boolean> = _showLocationRationaleDialog.asStateFlow()

    private val _showLocationSettingsDialog = MutableStateFlow(false)
    val showLocationSettingsDialog: StateFlow<Boolean> = _showLocationSettingsDialog.asStateFlow()

    private val _locationDeniedCount = MutableStateFlow(0)
    val locationDeniedCount: StateFlow<Int> = _locationDeniedCount.asStateFlow()

    val userLocation: StateFlow<Location?> = locationTracker.currentLocation

    private val defaultCoordinates = Pair(38.7112, -9.1423)
    private val _manualCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)

    val userCoordinates: StateFlow<Pair<Double, Double>> = combine(
        userLocation,
        _manualCoordinates,
        _isUsingGpsLocation
    ) { loc, manual, isGps ->
        when {
            isGps && loc != null -> Pair(loc.latitude, loc.longitude)
            manual != null -> manual
            loc != null -> Pair(loc.latitude, loc.longitude)
            else -> defaultCoordinates
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultCoordinates)

    val uiState: StateFlow<LocationUiState> = combine(
        userLocation,
        userCoordinates,
        _hasLocationPermission,
        _isPreciseLocation,
        _isUsingGpsLocation,
        _showLocationRationaleDialog,
        _showLocationSettingsDialog,
        _locationDeniedCount
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        LocationUiState(
            userLocation = values[0] as? Location,
            userCoordinates = values[1] as? Pair<Double, Double> ?: defaultCoordinates,
            hasLocationPermission = values[2] as Boolean,
            isPreciseLocation = values[3] as Boolean,
            isUsingGpsLocation = values[4] as Boolean,
            showLocationRationaleDialog = values[5] as Boolean,
            showLocationSettingsDialog = values[6] as Boolean,
            locationDeniedCount = values[7] as Int
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationUiState())

    init {
        val initialFine = locationTracker.hasFinePermission()
        val initialCoarse = locationTracker.hasCoarsePermission()
        if (initialFine || initialCoarse) {
            _hasLocationPermission.value = true
            _isPreciseLocation.value = initialFine
            _isUsingGpsLocation.value = true
            locationTracker.startTracking()
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
        } else {
            _hasLocationPermission.value = false
            _isPreciseLocation.value = false
            _isUsingGpsLocation.value = false
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

    fun dismissLocationRationale() {
        _showLocationRationaleDialog.value = false
    }

    fun dismissLocationSettings() {
        _showLocationSettingsDialog.value = false
    }

    fun setManualCoordinates(lat: Double, lng: Double) {
        _manualCoordinates.value = Pair(lat, lng)
        _isUsingGpsLocation.value = false
    }

    fun enableGpsMode() {
        if (_hasLocationPermission.value) {
            _isUsingGpsLocation.value = true
            _manualCoordinates.value = null
            locationTracker.startTracking()
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
    }
}

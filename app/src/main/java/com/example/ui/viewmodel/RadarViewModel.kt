package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScoutSpotEntity
import com.example.data.location.LocationTracker
import com.example.data.repository.ScoutRepository
import com.example.sensor.CompassSensorManager
import com.example.sensor.CompassState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RadarUiState(
    val radarRadiusMeters: Float = 500f,
    val radarCategory: String = "ALL",
    val nearbyRadarSpots: List<ScoutSpotEntity> = emptyList(),
    val compassState: CompassState = CompassState()
)

class RadarViewModel(
    private val repository: ScoutRepository,
    private val compassSensorManager: CompassSensorManager
) : ViewModel() {

    private val _radarRadiusMeters = MutableStateFlow(500f)
    val radarRadiusMeters: StateFlow<Float> = _radarRadiusMeters.asStateFlow()

    private val _radarCategory = MutableStateFlow("ALL")
    val radarCategory: StateFlow<String> = _radarCategory.asStateFlow()

    val compassState: StateFlow<CompassState> = compassSensorManager.compassState

    val allSpots: StateFlow<List<ScoutSpotEntity>> = repository.allSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<RadarUiState> = combine(
        _radarRadiusMeters,
        _radarCategory,
        allSpots,
        compassState
    ) { radius, category, spots, compass ->
        val filtered = spots.filter { spot ->
            category == "ALL" || spot.category.equals(category, ignoreCase = true)
        }
        RadarUiState(
            radarRadiusMeters = radius,
            radarCategory = category,
            nearbyRadarSpots = filtered,
            compassState = compass
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RadarUiState())

    init {
        compassSensorManager.startListening()
    }

    fun setRadarRadius(radius: Float) {
        _radarRadiusMeters.value = radius
    }

    fun setRadarCategory(category: String) {
        _radarCategory.value = category
    }

    fun toggleTrueNorthMode() {
        compassSensorManager.toggleTrueNorthMode()
    }

    override fun onCleared() {
        super.onCleared()
        compassSensorManager.stopListening()
    }
}

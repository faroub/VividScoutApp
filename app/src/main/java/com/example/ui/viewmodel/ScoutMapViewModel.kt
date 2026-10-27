package com.example.ui.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.example.data.local.ScoutSpotEntity
import com.example.data.model.ScoutGem
import com.example.data.model.toScoutGem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import com.example.engine.ScoutVectorEngine

/**
 * Predefined Vector Map Zoom Scale Presets.
 */
enum class ZoomPreset(val scale: Float, val label: String) {
    CITY(ScoutVectorEngine.PRESET_CITY_SCALE, "City 0.6x"),
    NEIGHBORHOOD(ScoutVectorEngine.PRESET_NEIGHBORHOOD_SCALE, "Hood 1.2x"),
    STREET(ScoutVectorEngine.PRESET_STREET_SCALE, "Street 2.5x"),
    CLOSEUP(ScoutVectorEngine.PRESET_CLOSEUP_SCALE, "Gem 4.0x")
}

/**
 * Unified Immutable UI State for the Scout Vector Map.
 */
data class ScoutMapUiState(
    val gems: List<ScoutGem> = emptyList(),
    val selectedGem: ScoutGem? = null,
    val selectedSpot: ScoutSpotEntity? = null,
    val panOffset: Offset = Offset.Zero,
    val zoomScale: Float = 1.0f,
    val zoomPreset: ZoomPreset? = null,
    val isEngineReady: Boolean = false,
    val userLat: Double = 38.7112,
    val userLng: Double = -9.1423,
    val userAccuracy: Float = 6f
) {
    val zoomPercentage: Int
        get() = (zoomScale * 100).toInt()
}

/**
 * ViewModel managing Scout Vector Map state and ScoutGem pins using unified StateFlow UDF pattern.
 */
class ScoutMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScoutMapUiState())
    val uiState: StateFlow<ScoutMapUiState> = _uiState.asStateFlow()

    fun onEngineReady() {
        _uiState.update { it.copy(isEngineReady = true) }
    }

    /**
     * Loads and updates the list of ScoutGem location pins rendered on the vector canvas.
     */
    fun setGems(gems: List<ScoutGem>) {
        _uiState.update { current ->
            current.copy(
                gems = gems,
                selectedGem = current.selectedGem?.let { sel -> gems.find { it.id == sel.id } } ?: gems.firstOrNull()
            )
        }
    }

    /**
     * Converts Room database ScoutSpotEntity items into ScoutGem domain models and updates state.
     */
    fun loadGemsFromEntities(entities: List<ScoutSpotEntity>, userLat: Double = 38.7112, userLng: Double = -9.1423) {
        val gemList = entities.map { it.toScoutGem(userLat, userLng) }
        setGems(gemList)
    }

    fun selectGem(gem: ScoutGem?) {
        _uiState.update { current ->
            current.copy(selectedGem = gem)
        }
        if (gem != null) {
            focusZoomOnGem(gem)
        }
    }

    fun selectSpot(spot: ScoutSpotEntity?) {
        _uiState.update { current ->
            current.copy(selectedSpot = spot)
        }
    }

    fun toggleBookmark(gemId: String) {
        _uiState.update { current ->
            val updatedGems = current.gems.map { gem ->
                if (gem.id == gemId) gem.copy(isSaved = !gem.isSaved) else gem
            }
            val updatedSelected = if (current.selectedGem?.id == gemId) {
                current.selectedGem.copy(isSaved = !current.selectedGem.isSaved)
            } else {
                current.selectedGem
            }
            current.copy(gems = updatedGems, selectedGem = updatedSelected)
        }
    }

    fun updatePan(delta: Offset) {
        _uiState.update { current ->
            current.copy(panOffset = current.panOffset + delta)
        }
    }

    fun updateZoom(zoomChange: Float) {
        _uiState.update { current ->
            val newScale = (current.zoomScale * zoomChange).coerceIn(
                ScoutVectorEngine.MIN_ZOOM_SCALE,
                ScoutVectorEngine.MAX_ZOOM_SCALE
            )
            val matchingPreset = ZoomPreset.entries.find { Math.abs(it.scale - newScale) < 0.05f }
            current.copy(zoomScale = newScale, zoomPreset = matchingPreset)
        }
    }

    /**
     * Step-wise manual Zoom In controls (+).
     */
    fun zoomIn(step: Float = 0.35f) {
        _uiState.update { current ->
            val newScale = (current.zoomScale + step).coerceIn(
                ScoutVectorEngine.MIN_ZOOM_SCALE,
                ScoutVectorEngine.MAX_ZOOM_SCALE
            )
            val matchingPreset = ZoomPreset.entries.find { Math.abs(it.scale - newScale) < 0.05f }
            current.copy(zoomScale = newScale, zoomPreset = matchingPreset)
        }
    }

    /**
     * Step-wise manual Zoom Out controls (-).
     */
    fun zoomOut(step: Float = 0.35f) {
        _uiState.update { current ->
            val newScale = (current.zoomScale - step).coerceIn(
                ScoutVectorEngine.MIN_ZOOM_SCALE,
                ScoutVectorEngine.MAX_ZOOM_SCALE
            )
            val matchingPreset = ZoomPreset.entries.find { Math.abs(it.scale - newScale) < 0.05f }
            current.copy(zoomScale = newScale, zoomPreset = matchingPreset)
        }
    }

    /**
     * Sets exact Zoom Preset level (City 0.6x, Hood 1.2x, Street 2.5x, Gem 4.0x).
     */
    fun setZoomPreset(preset: ZoomPreset) {
        _uiState.update { current ->
            current.copy(
                zoomScale = preset.scale,
                zoomPreset = preset
            )
        }
    }

    /**
     * Focuses zoom and pan center on a specific hidden gem location pin.
     */
    fun focusZoomOnGem(gem: ScoutGem) {
        _uiState.update { current ->
            val targetScale = ScoutVectorEngine.PRESET_STREET_SCALE
            val panCenter = ScoutVectorEngine.calculatePanToCenterGem(
                spotLat = gem.latitude,
                spotLng = gem.longitude,
                userLat = current.userLat,
                userLng = current.userLng,
                zoomScale = targetScale
            )
            current.copy(
                selectedGem = gem,
                zoomScale = targetScale,
                zoomPreset = ZoomPreset.STREET,
                panOffset = panCenter
            )
        }
    }

    fun setZoomScale(scale: Float) {
        _uiState.update { current ->
            val newScale = scale.coerceIn(
                ScoutVectorEngine.MIN_ZOOM_SCALE,
                ScoutVectorEngine.MAX_ZOOM_SCALE
            )
            val matchingPreset = ZoomPreset.entries.find { Math.abs(it.scale - newScale) < 0.05f }
            current.copy(zoomScale = newScale, zoomPreset = matchingPreset)
        }
    }

    fun resetCenter() {
        _uiState.update { current ->
            current.copy(panOffset = Offset.Zero)
        }
    }

    fun resetZoom() {
        _uiState.update { current ->
            current.copy(
                zoomScale = 1.0f,
                zoomPreset = null,
                panOffset = Offset.Zero
            )
        }
    }

    fun updateUserLocation(lat: Double, lng: Double, accuracy: Float) {
        _uiState.update { current ->
            current.copy(userLat = lat, userLng = lng, userAccuracy = accuracy)
        }
    }
}

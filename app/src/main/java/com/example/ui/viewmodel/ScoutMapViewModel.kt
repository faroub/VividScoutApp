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

/**
 * Unified Immutable UI State for the Scout Vector Map.
 */
data class ScoutMapUiState(
    val gems: List<ScoutGem> = emptyList(),
    val selectedGem: ScoutGem? = null,
    val selectedSpot: ScoutSpotEntity? = null,
    val panOffset: Offset = Offset.Zero,
    val zoomScale: Float = 1.0f,
    val isEngineReady: Boolean = false,
    val userLat: Double = 38.7112,
    val userLng: Double = -9.1423,
    val userAccuracy: Float = 6f
)

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
            val newScale = (current.zoomScale * zoomChange).coerceIn(0.4f, 4.0f)
            current.copy(zoomScale = newScale)
        }
    }

    fun setZoomScale(scale: Float) {
        _uiState.update { current ->
            current.copy(zoomScale = scale.coerceIn(0.4f, 4.0f))
        }
    }

    fun resetCenter() {
        _uiState.update { current ->
            current.copy(panOffset = Offset.Zero)
        }
    }

    fun updateUserLocation(lat: Double, lng: Double, accuracy: Float) {
        _uiState.update { current ->
            current.copy(userLat = lat, userLng = lng, userAccuracy = accuracy)
        }
    }
}

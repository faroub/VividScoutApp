package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScoutSpotEntity
import com.example.data.offline.DownloadedRegionPack
import com.example.data.offline.GeoBoundingBox
import com.example.data.offline.OfflineMapManager
import com.example.data.offline.OfflineMapPack
import com.example.data.offline.OfflineRegionStorage
import com.example.data.repository.ScoutRepository
import com.example.engine.WalkingRoute
import com.example.util.VicinityCacheReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OfflineMapsUiState(
    val offlineMapPacks: List<OfflineMapPack> = emptyList(),
    val offlineStorageBytes: Long = 0L,
    val offlineDrafts: List<ScoutSpotEntity> = emptyList(),
    val downloadedRegions: List<DownloadedRegionPack> = emptyList(),
    val isDownloadingRegion: Boolean = false,
    val isRegionDownloadMode: Boolean = false,
    val activeSelectionBounds: GeoBoundingBox? = null,
    val showRegionDownloadDialog: Boolean = false,
    val showQueueDialog: Boolean = false,
    val showDiagnosticDialog: Boolean = false,
    val diagnosticLog: String = "",
    val isDiagnosticRunning: Boolean = false,
    val vicinityCacheHealth: VicinityCacheReport = VicinityCacheReport(0, 0, 0f),
    val showCacheHealthDialog: Boolean = false,
    val isRoutePlannerOpen: Boolean = false,
    val routePlannerOrigin: ScoutSpotEntity? = null,
    val routePlannerDestination: ScoutSpotEntity? = null,
    val plannedRoute: WalkingRoute? = null,
    val isCalculatingRoute: Boolean = false,
    val activeTrail: ScoutSpotEntity? = null,
    val databaseSizeBytes: Long = 0L
)

class OfflineMapsViewModel(
    val offlineMapManager: OfflineMapManager,
    val regionStorage: OfflineRegionStorage,
    private val repository: ScoutRepository
) : ViewModel() {

    val offlineMapPacks: StateFlow<List<OfflineMapPack>> = offlineMapManager.packsState
    val offlineStorageBytes: StateFlow<Long> = offlineMapManager.totalStorageBytes

    val offlineDrafts: StateFlow<List<ScoutSpotEntity>> = repository.offlineDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downloadedRegions = MutableStateFlow<List<DownloadedRegionPack>>(emptyList())
    val downloadedRegions: StateFlow<List<DownloadedRegionPack>> = _downloadedRegions.asStateFlow()

    private val _isDownloadingRegion = MutableStateFlow(false)
    val isDownloadingRegion: StateFlow<Boolean> = _isDownloadingRegion.asStateFlow()

    private val _isRegionDownloadMode = MutableStateFlow(false)
    val isRegionDownloadMode: StateFlow<Boolean> = _isRegionDownloadMode.asStateFlow()

    private val _activeSelectionBounds = MutableStateFlow<GeoBoundingBox?>(null)
    val activeSelectionBounds: StateFlow<GeoBoundingBox?> = _activeSelectionBounds.asStateFlow()

    private val _showRegionDownloadDialog = MutableStateFlow(false)
    val showRegionDownloadDialog: StateFlow<Boolean> = _showRegionDownloadDialog.asStateFlow()

    private val _showQueueDialog = MutableStateFlow(false)
    val showQueueDialog: StateFlow<Boolean> = _showQueueDialog.asStateFlow()

    private val _showDiagnosticDialog = MutableStateFlow(false)
    val showDiagnosticDialog: StateFlow<Boolean> = _showDiagnosticDialog.asStateFlow()

    private val _diagnosticLog = MutableStateFlow("")
    val diagnosticLog: StateFlow<String> = _diagnosticLog.asStateFlow()

    private val _isDiagnosticRunning = MutableStateFlow(false)
    val isDiagnosticRunning: StateFlow<Boolean> = _isDiagnosticRunning.asStateFlow()

    private val _vicinityCacheHealth = MutableStateFlow(VicinityCacheReport(0, 0, 0f))
    val vicinityCacheHealth: StateFlow<VicinityCacheReport> = _vicinityCacheHealth.asStateFlow()

    private val _showCacheHealthDialog = MutableStateFlow(false)
    val showCacheHealthDialog: StateFlow<Boolean> = _showCacheHealthDialog.asStateFlow()

    private val _isRoutePlannerOpen = MutableStateFlow(false)
    val isRoutePlannerOpen: StateFlow<Boolean> = _isRoutePlannerOpen.asStateFlow()

    private val _routePlannerOrigin = MutableStateFlow<ScoutSpotEntity?>(null)
    val routePlannerOrigin: StateFlow<ScoutSpotEntity?> = _routePlannerOrigin.asStateFlow()

    private val _routePlannerDestination = MutableStateFlow<ScoutSpotEntity?>(null)
    val routePlannerDestination: StateFlow<ScoutSpotEntity?> = _routePlannerDestination.asStateFlow()

    private val _plannedRoute = MutableStateFlow<WalkingRoute?>(null)
    val plannedRoute: StateFlow<WalkingRoute?> = _plannedRoute.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    private val _activeTrail = MutableStateFlow<ScoutSpotEntity?>(null)
    val activeTrail: StateFlow<ScoutSpotEntity?> = _activeTrail.asStateFlow()

    private val _databaseSizeBytes = MutableStateFlow(0L)
    val databaseSizeBytes: StateFlow<Long> = _databaseSizeBytes.asStateFlow()

    val uiState: StateFlow<OfflineMapsUiState> = combine(
        offlineMapPacks,
        offlineStorageBytes,
        offlineDrafts,
        _downloadedRegions,
        _isDownloadingRegion,
        _isRegionDownloadMode,
        _activeSelectionBounds,
        _showRegionDownloadDialog,
        _showQueueDialog,
        _showDiagnosticDialog
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        OfflineMapsUiState(
            offlineMapPacks = values[0] as List<OfflineMapPack>,
            offlineStorageBytes = values[1] as Long,
            offlineDrafts = values[2] as List<ScoutSpotEntity>,
            downloadedRegions = values[3] as List<DownloadedRegionPack>,
            isDownloadingRegion = values[4] as Boolean,
            isRegionDownloadMode = values[5] as Boolean,
            activeSelectionBounds = values[6] as? GeoBoundingBox,
            showRegionDownloadDialog = values[7] as Boolean,
            showQueueDialog = values[8] as Boolean,
            showDiagnosticDialog = values[9] as Boolean,
            diagnosticLog = _diagnosticLog.value,
            isDiagnosticRunning = _isDiagnosticRunning.value,
            vicinityCacheHealth = _vicinityCacheHealth.value,
            showCacheHealthDialog = _showCacheHealthDialog.value,
            isRoutePlannerOpen = _isRoutePlannerOpen.value,
            routePlannerOrigin = _routePlannerOrigin.value,
            routePlannerDestination = _routePlannerDestination.value,
            plannedRoute = _plannedRoute.value,
            isCalculatingRoute = _isCalculatingRoute.value,
            activeTrail = _activeTrail.value,
            databaseSizeBytes = _databaseSizeBytes.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfflineMapsUiState())

    init {
        refreshDownloadedRegions()
        refreshDatabaseSize()
    }

    fun refreshDownloadedRegions() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadedRegions.value = regionStorage.getAllPacks()
        }
    }

    fun refreshDatabaseSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _databaseSizeBytes.value = repository.calculateDatabaseSize()
        }
    }

    fun downloadOfflinePack(packId: String) {
        viewModelScope.launch {
            offlineMapManager.downloadPack(packId)
        }
    }

    fun deleteOfflinePack(packId: String) {
        viewModelScope.launch {
            offlineMapManager.deletePack(packId)
        }
    }

    fun downloadCurrentGpsPack() {
        viewModelScope.launch {
            offlineMapManager.downloadGpsPack()
        }
    }

    fun setRegionDownloadMode(enabled: Boolean) {
        _isRegionDownloadMode.value = enabled
        if (!enabled) {
            _activeSelectionBounds.value = null
        }
    }

    fun updateSelectionBounds(bounds: GeoBoundingBox) {
        _activeSelectionBounds.value = bounds
        _showRegionDownloadDialog.value = true
    }

    fun dismissRegionDownloadDialog() {
        _showRegionDownloadDialog.value = false
    }

    fun deleteCustomRegion(regionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            regionStorage.deletePack(regionId)
            refreshDownloadedRegions()
        }
    }

    fun openQueueDialog() {
        _showQueueDialog.value = true
    }

    fun dismissQueueDialog() {
        _showQueueDialog.value = false
    }

    fun openDiagnosticDialog() {
        _showDiagnosticDialog.value = true
    }

    fun dismissDiagnosticDialog() {
        _showDiagnosticDialog.value = false
    }

    fun openCacheHealthDialog() {
        _showCacheHealthDialog.value = true
    }

    fun dismissCacheHealthDialog() {
        _showCacheHealthDialog.value = false
    }

    fun setActiveTrail(spot: ScoutSpotEntity?) {
        _activeTrail.value = spot
    }

    fun openRoutePlanner(origin: ScoutSpotEntity? = null, destination: ScoutSpotEntity? = null) {
        _routePlannerOrigin.value = origin
        _routePlannerDestination.value = destination
        _isRoutePlannerOpen.value = true
    }

    fun closeRoutePlanner() {
        _isRoutePlannerOpen.value = false
        _plannedRoute.value = null
    }

    fun setRouteOrigin(spot: ScoutSpotEntity?) {
        _routePlannerOrigin.value = spot
    }

    fun setRouteDestination(spot: ScoutSpotEntity?) {
        _routePlannerDestination.value = spot
    }

    fun swapRouteEndpoints() {
        val temp = _routePlannerOrigin.value
        _routePlannerOrigin.value = _routePlannerDestination.value
        _routePlannerDestination.value = temp
    }

    fun clearPlannedRoute() {
        _plannedRoute.value = null
    }

    fun clearWalkingTrail() {
        _activeTrail.value = null
    }

    fun enterRegionDownloadMode() {
        setRegionDownloadMode(true)
    }

    fun exitRegionDownloadMode() {
        setRegionDownloadMode(false)
    }

    fun openRegionDownloadDialog() {
        _showRegionDownloadDialog.value = true
    }

    fun closeRegionDownloadDialog() {
        _showRegionDownloadDialog.value = false
    }

    fun downloadCurrentSelectedRegion(name: String) {
        val bounds = _activeSelectionBounds.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingRegion.value = true
            regionStorage.savePack(
                DownloadedRegionPack(
                    id = "region_${System.currentTimeMillis()}",
                    regionName = name,
                    bounds = bounds,
                    downloadDateMillis = System.currentTimeMillis()
                )
            )
            refreshDownloadedRegions()
            _isDownloadingRegion.value = false
            _showRegionDownloadDialog.value = false
            _isRegionDownloadMode.value = false
            _activeSelectionBounds.value = null
        }
    }

    fun runDiagnosticQuery() {
        _diagnosticLog.value = "Running Overpass diagnostic query..."
    }

    fun prefetchVicinityCache() {
        // Trigger pre-cache
    }

    fun syncAllDrafts() {
        viewModelScope.launch {
            repository.syncDrafts()
            _showQueueDialog.value = false
        }
    }
}

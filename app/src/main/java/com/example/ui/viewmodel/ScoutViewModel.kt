package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutSpotEntity
import com.example.data.repository.ScoutRepository
import com.example.data.repository.ScoutGemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.location.Location
import com.example.data.location.LocationTracker
import com.example.data.local.computedDistanceMeters

enum class MainTab {
    EXPLORE,
    RADAR,
    SAVED,
    PROFILE
}

sealed class AppScreen {
    object Onboarding : AppScreen()
    data class Main(val tab: MainTab = MainTab.EXPLORE) : AppScreen()
}

class ScoutViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository: ScoutRepository = ScoutRepository(db.scoutDao(), db.discoveredGemDao())
    val gemRepository: ScoutGemRepository = ScoutGemRepository(db.scoutDao(), db.discoveredGemDao())
    val locationTracker: LocationTracker = LocationTracker(application)
    val offlineMapManager: com.example.data.offline.OfflineMapManager = com.example.data.offline.OfflineMapManager(application, gemRepository)
    val regionStorage = com.example.data.offline.OfflineRegionStorage(application)

    val offlineMapPacks: StateFlow<List<com.example.data.offline.OfflineMapPack>> = offlineMapManager.packsState
    val offlineStorageBytes: StateFlow<Long> = offlineMapManager.totalStorageBytes

    private val _isRegionDownloadMode = MutableStateFlow(false)
    val isRegionDownloadMode: StateFlow<Boolean> = _isRegionDownloadMode.asStateFlow()

    private val _activeSelectionBounds = MutableStateFlow<com.example.data.offline.GeoBoundingBox?>(null)
    val activeSelectionBounds: StateFlow<com.example.data.offline.GeoBoundingBox?> = _activeSelectionBounds.asStateFlow()

    private val _showRegionDownloadDialog = MutableStateFlow(false)
    val showRegionDownloadDialog: StateFlow<Boolean> = _showRegionDownloadDialog.asStateFlow()

    private val _downloadedRegions = MutableStateFlow<List<com.example.data.offline.DownloadedRegionPack>>(emptyList())
    val downloadedRegions: StateFlow<List<com.example.data.offline.DownloadedRegionPack>> = _downloadedRegions.asStateFlow()

    private val _isDownloadingRegion = MutableStateFlow(false)
    val isDownloadingRegion: StateFlow<Boolean> = _isDownloadingRegion.asStateFlow()

    val userLocation: StateFlow<Location?> = locationTracker.currentLocation
    val hasLocationPermission: StateFlow<Boolean> = locationTracker.hasPermission
    val isPreciseLocation: StateFlow<Boolean> = locationTracker.isPrecise
    val isGpsTracking: StateFlow<Boolean> = locationTracker.isTracking

    private val _showLocationRationaleDialog = MutableStateFlow(false)
    val showLocationRationaleDialog: StateFlow<Boolean> = _showLocationRationaleDialog.asStateFlow()

    private val _showLocationSettingsDialog = MutableStateFlow(false)
    val showLocationSettingsDialog: StateFlow<Boolean> = _showLocationSettingsDialog.asStateFlow()

    private val _locationDeniedCount = MutableStateFlow(0)
    val locationDeniedCount: StateFlow<Int> = _locationDeniedCount.asStateFlow()

    val userCoordinates: StateFlow<Pair<Double, Double>> = combine(
        userLocation
    ) { locs ->
        val loc = locs[0]
        if (loc != null) {
            Pair(loc.latitude, loc.longitude)
        } else {
            Pair(38.7112, -9.1423)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(38.7112, -9.1423))

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Onboarding)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _currentTab = MutableStateFlow(MainTab.EXPLORE)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow("all")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isOfflineBannerVisible = MutableStateFlow(true)
    val isOfflineBannerVisible: StateFlow<Boolean> = _isOfflineBannerVisible.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _showSubmitModal = MutableStateFlow(false)
    val showSubmitModal: StateFlow<Boolean> = _showSubmitModal.asStateFlow()

    private val _showQueueDialog = MutableStateFlow(false)
    val showQueueDialog: StateFlow<Boolean> = _showQueueDialog.asStateFlow()

    private val _userPoints = MutableStateFlow(145)
    val userPoints: StateFlow<Int> = _userPoints.asStateFlow()

    private val _playingAudioId = MutableStateFlow<String?>(null)
    val playingAudioId: StateFlow<String?> = _playingAudioId.asStateFlow()

    private val _activeTrail = MutableStateFlow<com.example.engine.OfflineWalkingTrail?>(null)
    val activeTrail: StateFlow<com.example.engine.OfflineWalkingTrail?> = _activeTrail.asStateFlow()

    private val _isRoutePlannerOpen = MutableStateFlow(false)
    val isRoutePlannerOpen: StateFlow<Boolean> = _isRoutePlannerOpen.asStateFlow()

    private val _routePlannerOrigin = MutableStateFlow<ScoutSpotEntity?>(null)
    val routePlannerOrigin: StateFlow<ScoutSpotEntity?> = _routePlannerOrigin.asStateFlow()

    private val _routePlannerDestination = MutableStateFlow<ScoutSpotEntity?>(null)
    val routePlannerDestination: StateFlow<ScoutSpotEntity?> = _routePlannerDestination.asStateFlow()

    private val _plannedRoute = MutableStateFlow<com.example.engine.OfflineWalkingTrail?>(null)
    val plannedRoute: StateFlow<com.example.engine.OfflineWalkingTrail?> = _plannedRoute.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    private val _exploreHeaderStyle = MutableStateFlow(0) // 0: Radar & Ticker, 1: Chiado Hero
    val exploreHeaderStyle: StateFlow<Int> = _exploreHeaderStyle.asStateFlow()

    private val _appLanguage = MutableStateFlow(com.example.ui.theme.AppLanguage.ENGLISH)
    val appLanguage: StateFlow<com.example.ui.theme.AppLanguage> = _appLanguage.asStateFlow()

    private val _showLanguageDialog = MutableStateFlow(false)
    val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

    private val _isScrapingOsm = MutableStateFlow(false)
    val isScrapingOsm: StateFlow<Boolean> = _isScrapingOsm.asStateFlow()

    private val _osmScrapeMessage = MutableStateFlow<String?>(null)
    val osmScrapeMessage: StateFlow<String?> = _osmScrapeMessage.asStateFlow()

    private val _lastOsmSyncedCount = MutableStateFlow(0)
    val lastOsmSyncedCount: StateFlow<Int> = _lastOsmSyncedCount.asStateFlow()

    val diagnosticLog: StateFlow<com.example.data.network.OverpassDiagnosticLog?> = gemRepository.lastDiagnosticLog

    private val _showDiagnosticDialog = MutableStateFlow(false)
    val showDiagnosticDialog: StateFlow<Boolean> = _showDiagnosticDialog.asStateFlow()

    private val _isDiagnosticRunning = MutableStateFlow(false)
    val isDiagnosticRunning: StateFlow<Boolean> = _isDiagnosticRunning.asStateFlow()

    private val _hasAutoScrapedForLocation = MutableStateFlow(false)

    val allSpots: StateFlow<List<ScoutSpotEntity>> = repository.allSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedSpots: StateFlow<List<ScoutSpotEntity>> = repository.savedSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredSpots: StateFlow<List<ScoutSpotEntity>> = repository.discoveredSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineDrafts: StateFlow<List<ScoutSpotEntity>> = repository.offlineDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatus: StateFlow<com.example.ui.components.OfflineSyncStatus> = combine(
        allSpots,
        offlineDrafts,
        _lastOsmSyncedCount,
        _isScrapingOsm
    ) { spots, drafts, osmCount, isSyncing ->
        com.example.ui.components.OfflineSyncStatus(
            totalLocalGems = spots.size,
            pendingDraftsCount = drafts.size,
            lastOsmSyncedCount = osmCount,
            isSyncing = isSyncing
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.ui.components.OfflineSyncStatus()
    )

    private val _showCacheHealthDialog = MutableStateFlow(false)
    val showCacheHealthDialog: StateFlow<Boolean> = _showCacheHealthDialog.asStateFlow()

    val vicinityCacheHealth: StateFlow<com.example.data.model.VicinityCacheHealth> = combine(
        userCoordinates,
        allSpots,
        _lastOsmSyncedCount,
        _isScrapingOsm
    ) { coords, spots, osmCount, isSyncing ->
        com.example.data.model.CacheHealthCalculator.calculate(
            userLat = coords.first,
            userLng = coords.second,
            spots = spots,
            radiusMeters = 1000,
            targetCount = 12,
            lastSyncedCount = osmCount,
            isFetching = isSyncing
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.data.model.CacheHealthCalculator.calculate(
            userLat = 38.7112,
            userLng = -9.1423,
            spots = emptyList(),
            radiusMeters = 1000,
            targetCount = 12
        )
    )

    init {
        viewModelScope.launch {
            repository.initializeIfEmpty()
        }
        if (locationTracker.checkHasPermission()) {
            locationTracker.startTracking()
        }

        // Load custom downloaded regions from disk
        viewModelScope.launch(Dispatchers.IO) {
            val regions = regionStorage.getAllRegions()
            _downloadedRegions.value = regions
            com.example.engine.VectorWalkGraph.reloadAllDownloadedRegions(regions)
        }

        // Auto-discover local spots via OpenStreetMap Overpass when user GPS is outside seeded region
        viewModelScope.launch {
            userLocation.collect { loc ->
                if (loc != null) {
                    offlineMapManager.refreshPacksState(loc.latitude, loc.longitude)
                    val currentSpots = allSpots.value
                    val nearbyCount = currentSpots.count { spot ->
                        spot.computedDistanceMeters(loc.latitude, loc.longitude) <= 10000f
                    }
                    if (nearbyCount == 0 && !_hasAutoScrapedForLocation.value && !_isScrapingOsm.value) {
                        _hasAutoScrapedForLocation.value = true
                        scrapeOsmGems(loc.latitude, loc.longitude, radiusMeters = 3000)
                    }
                }
            }
        }
    }

    val filteredSpots: StateFlow<List<ScoutSpotEntity>> = combine(
        allSpots,
        _selectedCategory,
        _searchQuery
    ) { spots, category, query ->
        spots.filter { spot ->
            val matchesCategory = if (category == "all") true else spot.category.equals(category, ignoreCase = true)
            val matchesQuery = if (query.isBlank()) true else {
                spot.title.contains(query, ignoreCase = true) ||
                        spot.description.contains(query, ignoreCase = true) ||
                        spot.locationName.contains(query, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun navigateToMain(tab: MainTab = MainTab.EXPLORE) {
        _currentScreen.value = AppScreen.Main(tab)
        _currentTab.value = tab
    }

    fun navigateToOnboarding() {
        _currentScreen.value = AppScreen.Onboarding
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
        _currentScreen.value = AppScreen.Main(tab)
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun dismissOfflineBanner() {
        _isOfflineBannerVisible.value = false
    }

    fun toggleOfflineBanner() {
        _isOfflineBannerVisible.value = !_isOfflineBannerVisible.value
    }

    fun openSubmitModal() {
        _showSubmitModal.value = true
    }

    fun closeSubmitModal() {
        _showSubmitModal.value = false
    }

    fun openQueueDialog() {
        _showQueueDialog.value = true
    }

    fun closeQueueDialog() {
        _showQueueDialog.value = false
    }

    fun toggleHeaderStyle() {
        _exploreHeaderStyle.value = if (_exploreHeaderStyle.value == 0) 1 else 0
    }

    fun setLanguage(language: com.example.ui.theme.AppLanguage) {
        _appLanguage.value = language
        _showLanguageDialog.value = false
    }

    fun openLanguageDialog() {
        _showLanguageDialog.value = true
    }

    fun closeLanguageDialog() {
        _showLanguageDialog.value = false
    }

    fun toggleSave(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.toggleSave(spot.id, spot.isSaved)
        }
    }

    fun toggleDiscovered(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.toggleDiscovered(spot.id, spot.isDiscovered)
            if (!spot.isDiscovered) {
                _userPoints.value += 20
            }
        }
    }

    fun updateSpotNotes(spotId: String, notes: String) {
        viewModelScope.launch {
            repository.updateSpotNotes(spotId, notes)
        }
    }

    fun deleteSpot(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.deleteSpot(spot)
        }
    }

    fun upvote(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.upvote(spot.id)
        }
    }

    fun toggleAudioLore(spotId: String) {
        if (_playingAudioId.value == spotId) {
            _playingAudioId.value = null
        } else {
            _playingAudioId.value = spotId
        }
    }

    fun openLocationRationaleDialog() {
        _showLocationRationaleDialog.value = true
    }

    fun dismissLocationRationaleDialog() {
        _showLocationRationaleDialog.value = false
    }

    fun openLocationSettingsDialog() {
        _showLocationSettingsDialog.value = true
    }

    fun dismissLocationSettingsDialog() {
        _showLocationSettingsDialog.value = false
    }

    fun onLocationPermissionResult(fineGranted: Boolean, coarseGranted: Boolean) {
        locationTracker.onPermissionResult(fineGranted, coarseGranted)
        _showLocationRationaleDialog.value = false
        if (!fineGranted && !coarseGranted) {
            _locationDeniedCount.value += 1
            if (_locationDeniedCount.value >= 2) {
                _showLocationSettingsDialog.value = true
            }
        } else {
            _showLocationSettingsDialog.value = false
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        onLocationPermissionResult(fineGranted = granted, coarseGranted = granted)
    }

    fun onLocationPermissionDenied() {
        _locationDeniedCount.value += 1
        if (_locationDeniedCount.value >= 2) {
            _showLocationSettingsDialog.value = true
        }
    }

    fun refreshLocationPermissions() {
        locationTracker.refreshPermissionState()
    }

    fun requestStartTracking() {
        locationTracker.startTracking()
    }

    fun submitGem(name: String, category: String, photoUri: String?, notes: String = "", onComplete: () -> Unit) {
        viewModelScope.launch {
            val coords = userCoordinates.value
            val locName = if (userLocation.value != null) "GPS: Chiado / Alfama" else "Chiado Alleyway"
            repository.submitSpotDraft(
                name = if (name.isNotBlank()) name else "Miradouro Secreto do Beco do Forno",
                category = category,
                photoUri = photoUri,
                notes = notes,
                locationName = locName,
                latitude = coords.first,
                longitude = coords.second
            )
            _userPoints.value += 15
            delay(400)
            _showSubmitModal.value = false
            onComplete()
        }
    }

    fun syncAllDrafts() {
        viewModelScope.launch {
            val drafts = offlineDrafts.value
            drafts.forEach { draft ->
                repository.syncDraft(draft)
            }
            _showQueueDialog.value = false
        }
    }

    fun scrapeOsmGems(lat: Double, lng: Double, radiusMeters: Int = 1500) {
        if (_isScrapingOsm.value) return
        viewModelScope.launch {
            _isScrapingOsm.value = true
            _osmScrapeMessage.value = "Scraping viewpoints, bakeries & shortcuts from OpenStreetMap..."
            val result = gemRepository.syncLiveOsmGems(lat, lng, radiusMeters)
            result.fold(
                onSuccess = { count ->
                    _lastOsmSyncedCount.value = count
                    _osmScrapeMessage.value = if (count > 0) {
                        "✨ $count real gems scraped from OpenStreetMap into Room DB!"
                    } else {
                        "OSM scan complete. All available spots up-to-date."
                    }
                    if (count > 0) {
                        _userPoints.value += count * 5
                    }
                },
                onFailure = { err ->
                    _osmScrapeMessage.value = "OSM Overpass query: ${err.message ?: "Network error"}. Tap to retry."
                }
            )
            _isScrapingOsm.value = false
        }
    }

    fun openCacheHealthDialog() {
        _showCacheHealthDialog.value = true
    }

    fun closeCacheHealthDialog() {
        _showCacheHealthDialog.value = false
    }

    fun prefetchVicinityCache() {
        val coords = userCoordinates.value
        scrapeOsmGems(coords.first, coords.second, radiusMeters = 1500)
    }

    fun openDiagnosticDialog() {
        _showDiagnosticDialog.value = true
        if (diagnosticLog.value == null) {
            runDiagnosticQuery()
        }
    }

    fun closeDiagnosticDialog() {
        _showDiagnosticDialog.value = false
    }

    fun runDiagnosticQuery() {
        if (_isDiagnosticRunning.value) return
        val coords = userCoordinates.value
        viewModelScope.launch {
            _isDiagnosticRunning.value = true
            gemRepository.runDiagnosticQuery(coords.first, coords.second, 1500)
            _isDiagnosticRunning.value = false
        }
    }

    fun downloadOfflineMapPack(packId: String) {
        viewModelScope.launch {
            val coords = userCoordinates.value
            val pack = offlineMapPacks.value.find { it.id == packId }
            val cityName = pack?.cityName ?: "Vector Map"
            val lat = if (packId == "custom_gps_pack") coords.first else (pack?.lat ?: coords.first)
            val lng = if (packId == "custom_gps_pack") coords.second else (pack?.lng ?: coords.second)

            com.example.data.offline.OfflineMapDownloadService.startDownload(
                getApplication(),
                packId,
                cityName,
                lat,
                lng
            )
        }
    }

    fun deleteOfflineMapPack(packId: String) {
        viewModelScope.launch {
            val coords = userCoordinates.value
            offlineMapManager.deletePack(packId, coords.first, coords.second)
        }
    }

    fun downloadCurrentGpsMapPack() {
        downloadOfflineMapPack("custom_gps_pack")
    }

    fun planWalkingTrailForSavedGems() {
        val coords = userCoordinates.value
        val targets = if (savedSpots.value.isNotEmpty()) {
            savedSpots.value
        } else {
            allSpots.value.take(4)
        }
        val trail = com.example.engine.OfflineRouteEngine.calculateTrail(coords.first, coords.second, targets)
        _activeTrail.value = trail
    }

    fun planWalkingTrail(spots: List<ScoutSpotEntity>) {
        val coords = userCoordinates.value
        val trail = com.example.engine.OfflineRouteEngine.calculateTrail(coords.first, coords.second, spots)
        _activeTrail.value = trail
    }

    fun openRoutePlanner(origin: ScoutSpotEntity? = null, destination: ScoutSpotEntity? = null) {
        _routePlannerOrigin.value = origin
        if (destination != null) {
            _routePlannerDestination.value = destination
        } else if (_routePlannerDestination.value == null) {
            _routePlannerDestination.value = savedSpots.value.firstOrNull() ?: allSpots.value.firstOrNull()
        }
        _isRoutePlannerOpen.value = true
        calculateAStarRoute()
    }

    fun closeRoutePlanner() {
        _isRoutePlannerOpen.value = false
    }

    fun setRouteOrigin(spot: ScoutSpotEntity?) {
        _routePlannerOrigin.value = spot
        calculateAStarRoute()
    }

    fun setRouteDestination(spot: ScoutSpotEntity?) {
        _routePlannerDestination.value = spot
        calculateAStarRoute()
    }

    fun swapRouteEndpoints() {
        val oldOrigin = _routePlannerOrigin.value
        val oldDest = _routePlannerDestination.value
        _routePlannerOrigin.value = oldDest
        _routePlannerDestination.value = oldOrigin
        calculateAStarRoute()
    }

    fun calculateAStarRoute() {
        val dest = _routePlannerDestination.value ?: return
        val origin = _routePlannerOrigin.value
        val (startLat, startLng, startName) = if (origin != null) {
            Triple(origin.latitude, origin.longitude, origin.name)
        } else {
            val coords = userCoordinates.value
            Triple(coords.first, coords.second, "My Location (GPS)")
        }

        viewModelScope.launch {
            _isCalculatingRoute.value = true
            val trail = com.example.engine.OfflineRouteEngine.calculateShortestAStarRoute(
                originLat = startLat,
                originLng = startLng,
                destLat = dest.latitude,
                destLng = dest.longitude,
                originName = startName,
                destName = dest.name,
                originEntity = origin,
                destEntity = dest
            )
            _plannedRoute.value = trail
            _activeTrail.value = trail
            _isCalculatingRoute.value = false
        }
    }

    fun clearPlannedRoute() {
        _plannedRoute.value = null
        _activeTrail.value = null
    }

    fun clearWalkingTrail() {
        clearPlannedRoute()
    }

    fun dismissOsmMessage() {
        _osmScrapeMessage.value = null
    }

    fun enterRegionDownloadMode() {
        val center = userCoordinates.value
        // Default bounding box approx 1.6km x 1.4km
        val latDelta = 0.007
        val lngDelta = 0.009
        _activeSelectionBounds.value = com.example.data.offline.GeoBoundingBox(
            minLat = center.first - latDelta,
            minLng = center.second - lngDelta,
            maxLat = center.first + latDelta,
            maxLng = center.second + lngDelta
        )
        _isRegionDownloadMode.value = true
    }

    fun exitRegionDownloadMode() {
        _isRegionDownloadMode.value = false
        _activeSelectionBounds.value = null
    }

    fun updateSelectionBounds(bounds: com.example.data.offline.GeoBoundingBox) {
        _activeSelectionBounds.value = bounds
    }

    fun openRegionDownloadDialog() {
        if (_activeSelectionBounds.value != null) {
            _showRegionDownloadDialog.value = true
        }
    }

    fun closeRegionDownloadDialog() {
        _showRegionDownloadDialog.value = false
    }

    fun downloadCurrentSelectedRegion(regionName: String) {
        val bounds = _activeSelectionBounds.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingRegion.value = true
            _showRegionDownloadDialog.value = false

            val spotsInRegion = allSpots.value.filter { bounds.contains(it.latitude, it.longitude) }
            val (roadSegments, elevationAnchors) = regionStorage.synthesizeRoadNetworkForBounds(
                bounds = bounds,
                regionName = regionName,
                cachedSpots = spotsInRegion
            )

            val estimatedBytes = (roadSegments.size * 220L) + (elevationAnchors.size * 64L) + 4096L
            val newPack = com.example.data.offline.DownloadedRegionPack(
                id = "custom_${System.currentTimeMillis()}",
                name = regionName,
                bounds = bounds,
                roadSegments = roadSegments,
                elevationAnchors = elevationAnchors,
                spotsCount = spotsInRegion.size,
                totalSizeBytes = estimatedBytes
            )

            regionStorage.saveRegion(newPack)
            val allPacks = regionStorage.getAllRegions()
            _downloadedRegions.value = allPacks
            com.example.engine.VectorWalkGraph.reloadAllDownloadedRegions(allPacks)

            delay(400)
            _isDownloadingRegion.value = false
            _isRegionDownloadMode.value = false
            _activeSelectionBounds.value = null
        }
    }

    fun deleteDownloadedRegion(regionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            regionStorage.deleteRegion(regionId)
            val allPacks = regionStorage.getAllRegions()
            _downloadedRegions.value = allPacks
            com.example.engine.VectorWalkGraph.reloadAllDownloadedRegions(allPacks)
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
    }
}

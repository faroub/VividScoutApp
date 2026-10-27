package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ScoutSpotEntity
import com.example.data.repository.ScoutRepository
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

    private val repository: ScoutRepository
    val locationTracker = LocationTracker(application)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ScoutRepository(db.scoutDao(), db.discoveredGemDao())
        viewModelScope.launch {
            repository.initializeIfEmpty()
        }
        if (locationTracker.checkHasPermission()) {
            locationTracker.startTracking()
        }
    }

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

    val allSpots: StateFlow<List<ScoutSpotEntity>> = repository.allSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedSpots: StateFlow<List<ScoutSpotEntity>> = repository.savedSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredSpots: StateFlow<List<ScoutSpotEntity>> = repository.discoveredSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineDrafts: StateFlow<List<ScoutSpotEntity>> = repository.offlineDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            val result = repository.scrapeAndSaveOsmGems(lat, lng, radiusMeters)
            result.fold(
                onSuccess = { count ->
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

    fun dismissOsmMessage() {
        _osmScrapeMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopTracking()
    }
}

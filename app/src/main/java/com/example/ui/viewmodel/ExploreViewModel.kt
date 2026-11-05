package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.RecentSearchEntity
import com.example.data.local.ScoutSpotEntity
import com.example.data.local.computedDistanceMeters
import com.example.data.location.DefaultLocationConfig
import com.example.data.model.CityProfileRegistry
import com.example.data.model.GlobalCityCatalog
import com.example.data.model.ScoutCategory
import com.example.data.repository.ScoutGemRepository
import com.example.data.repository.ScoutRepository
import com.example.engine.PreScoutBannerState
import com.example.engine.PreScoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.model.ExploreMapLayer
import com.example.util.SafeLog

data class ExploreUiState(
    val filteredSpots: List<ScoutSpotEntity> = emptyList(),
    val allSpots: List<ScoutSpotEntity> = emptyList(),
    val savedSpots: List<ScoutSpotEntity> = emptyList(),
    val discoveredSpots: List<ScoutSpotEntity> = emptyList(),
    val selectedCategory: String = "all",
    val searchQuery: String = "",
    val recentSearches: List<RecentSearchEntity> = emptyList(),
    val selectedGlobalCity: String = "Detecting Location...",
    val cityProfile: com.example.data.model.CityProfile = CityProfileRegistry.LISBON_PROFILE,
    val showGlobalCityDialog: Boolean = false,
    val preScoutBannerState: PreScoutBannerState = PreScoutBannerState(),
    val exploreHeaderStyle: Int = 0,
    val isScrapingOsm: Boolean = false,
    val osmScrapeMessage: String? = null,
    val playingAudioId: String? = null,
    val isAwaitingLocation: Boolean = true,
    val isDiscoveringGems: Boolean = false,
    val activeMapLayers: Set<ExploreMapLayer> = ExploreMapLayer.ALL_LAYERS,
    val showTouristicSourcesModal: Boolean = false,
    val isSyncingTouristicSources: Boolean = false
)

class ExploreViewModel(
    private val repository: ScoutRepository,
    private val gemRepository: ScoutGemRepository,
    private val preScoutEngine: PreScoutEngine
) : ViewModel() {

    val allSpots: StateFlow<List<ScoutSpotEntity>> = repository.allSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedSpots: StateFlow<List<ScoutSpotEntity>> = repository.savedSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredSpots: StateFlow<List<ScoutSpotEntity>> = repository.discoveredSpots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches: StateFlow<List<RecentSearchEntity>> = gemRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategory = MutableStateFlow("all")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGlobalCity = MutableStateFlow("Detecting Location...")
    val selectedGlobalCity: StateFlow<String> = _selectedGlobalCity.asStateFlow()

    private val _cityProfile = MutableStateFlow(
        CityProfileRegistry.getProfileForCity("Detecting Location...")
    )
    val cityProfile: StateFlow<com.example.data.model.CityProfile> = _cityProfile.asStateFlow()

    private val _showGlobalCityDialog = MutableStateFlow(false)
    val showGlobalCityDialog: StateFlow<Boolean> = _showGlobalCityDialog.asStateFlow()

    private val _preScoutBannerState = MutableStateFlow(PreScoutBannerState())
    val preScoutBannerState: StateFlow<PreScoutBannerState> = _preScoutBannerState.asStateFlow()

    private val _exploreHeaderStyle = MutableStateFlow(0)
    val exploreHeaderStyle: StateFlow<Int> = _exploreHeaderStyle.asStateFlow()

    private val _isScrapingOsm = MutableStateFlow(false)
    val isScrapingOsm: StateFlow<Boolean> = _isScrapingOsm.asStateFlow()

    private val _osmScrapeMessage = MutableStateFlow<String?>(null)
    val osmScrapeMessage: StateFlow<String?> = _osmScrapeMessage.asStateFlow()

    private val _playingAudioId = MutableStateFlow<String?>(null)
    val playingAudioId: StateFlow<String?> = _playingAudioId.asStateFlow()

    private val _userCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val userCoordinates: StateFlow<Pair<Double, Double>?> = _userCoordinates.asStateFlow()

    private val _isAwaitingLocation = MutableStateFlow(true)
    val isAwaitingLocation: StateFlow<Boolean> = _isAwaitingLocation.asStateFlow()

    private val _isDiscoveringGems = MutableStateFlow(false)
    val isDiscoveringGems: StateFlow<Boolean> = _isDiscoveringGems.asStateFlow()

    val filteredSpots: StateFlow<List<ScoutSpotEntity>> = combine(
        allSpots,
        _selectedCategory,
        _searchQuery,
        _userCoordinates
    ) { spots, category, query, coords ->
        if (coords == null) {
            return@combine emptyList()
        }
        val userLat = coords.first
        val userLng = coords.second

        val matching = spots.filter { spot ->
            val matchesCategory = ScoutCategory.matches(category, spot.category)
            val matchesQuery = query.isBlank() ||
                spot.title.contains(query, ignoreCase = true) ||
                spot.description.contains(query, ignoreCase = true) ||
                spot.category.contains(query, ignoreCase = true)

            // Vicinity Filter: Exclude distant spots from other regions (>60km away)
            val distMeters = spot.computedDistanceMeters(userLat, userLng)
            val matchesVicinity = distMeters <= 60000f

            matchesCategory && matchesQuery && matchesVicinity
        }

        // Proximity Sorting: Places closest to user GPS appear FIRST
        matching.sortedBy { spot ->
            spot.computedDistanceMeters(userLat, userLng)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeMapLayers = MutableStateFlow<Set<ExploreMapLayer>>(ExploreMapLayer.ALL_LAYERS)
    val activeMapLayers: StateFlow<Set<ExploreMapLayer>> = _activeMapLayers.asStateFlow()

    private val _showTouristicSourcesModal = MutableStateFlow(false)
    val showTouristicSourcesModal: StateFlow<Boolean> = _showTouristicSourcesModal.asStateFlow()

    private val _isSyncingTouristicSources = MutableStateFlow(false)
    val isSyncingTouristicSources: StateFlow<Boolean> = _isSyncingTouristicSources.asStateFlow()

    val uiState: StateFlow<ExploreUiState> = combine(
        filteredSpots,
        allSpots,
        savedSpots,
        discoveredSpots,
        _selectedCategory,
        _searchQuery,
        recentSearches,
        _selectedGlobalCity,
        _showGlobalCityDialog,
        _preScoutBannerState,
        _activeMapLayers,
        _showTouristicSourcesModal,
        _isSyncingTouristicSources
    ) { vals: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        ExploreUiState(
            filteredSpots = vals[0] as List<ScoutSpotEntity>,
            allSpots = vals[1] as List<ScoutSpotEntity>,
            savedSpots = vals[2] as List<ScoutSpotEntity>,
            discoveredSpots = vals[3] as List<ScoutSpotEntity>,
            selectedCategory = vals[4] as String,
            searchQuery = vals[5] as String,
            recentSearches = vals[6] as List<RecentSearchEntity>,
            selectedGlobalCity = vals[7] as String,
            cityProfile = _cityProfile.value,
            showGlobalCityDialog = vals[8] as Boolean,
            preScoutBannerState = vals[9] as PreScoutBannerState,
            exploreHeaderStyle = _exploreHeaderStyle.value,
            isScrapingOsm = _isScrapingOsm.value,
            osmScrapeMessage = _osmScrapeMessage.value,
            playingAudioId = _playingAudioId.value,
            isAwaitingLocation = _isAwaitingLocation.value,
            isDiscoveringGems = _isDiscoveringGems.value,
            activeMapLayers = vals[10] as Set<ExploreMapLayer>,
            showTouristicSourcesModal = vals[11] as Boolean,
            isSyncingTouristicSources = vals[12] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreUiState())

    fun toggleMapLayer(layer: ExploreMapLayer) {
        val current = _activeMapLayers.value
        _activeMapLayers.value = if (current.contains(layer)) {
            current - layer
        } else {
            current + layer
        }
    }

    fun setAllMapLayers(enabled: Boolean) {
        _activeMapLayers.value = if (enabled) {
            ExploreMapLayer.ALL_LAYERS
        } else {
            emptySet()
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.initializeIfEmpty()
            // Automatically enrich any spots with placeholder images using live Wikipedia/Wikimedia
            gemRepository.enrichSpotsPhotosInBackground()
        }
    }

    suspend fun refreshSpotPhoto(spotId: String): Boolean {
        return gemRepository.refreshSpotPhoto(spotId)
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank() && query.length >= 3) {
            viewModelScope.launch {
                gemRepository.recordRecentSearch(query.trim())
            }
        }
    }

    fun executeRecentSearch(query: String) {
        setSearchQuery(query)
    }

    fun deleteRecentSearch(id: String) {
        viewModelScope.launch {
            gemRepository.removeRecentSearch(id)
        }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            gemRepository.clearAllRecentSearches()
        }
    }

    fun toggleSave(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.toggleSave(spot.id, spot.isSaved)
        }
    }

    fun toggleDiscovered(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.toggleDiscovered(spot.id, spot.isDiscovered)
        }
    }

    fun upvote(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.upvote(spot.id)
        }
    }

    fun toggleAudioLore(spotId: String) {
        _playingAudioId.value = if (_playingAudioId.value == spotId) null else spotId
    }

    fun openGlobalCityDialog() {
        _showGlobalCityDialog.value = true
    }

    fun dismissGlobalCityDialog() {
        _showGlobalCityDialog.value = false
    }

    fun selectGlobalCity(cityName: String, context: Context? = null) {
        _selectedGlobalCity.value = cityName
        val profile = CityProfileRegistry.getProfileForCity(cityName)
        _cityProfile.value = profile
        _showGlobalCityDialog.value = false
        val lat = if (profile.lat != 0.0) profile.lat else DefaultLocationConfig.activeFallbackLat
        val lng = if (profile.lng != 0.0) profile.lng else DefaultLocationConfig.activeFallbackLng
        if (lat != 0.0 && lng != 0.0) {
            DefaultLocationConfig.setActiveFallbackCity(lat, lng, cityName, context)
            _userCoordinates.value = Pair(lat, lng)
            _isAwaitingLocation.value = false
            viewModelScope.launch(Dispatchers.IO) {
                _isDiscoveringGems.value = true
                val banner = preScoutEngine.executePreScout(lat, lng, cityName)
                _preScoutBannerState.value = banner
                try {
                    gemRepository.syncLiveOsmGems(lat, lng, 1500, "all")
                } catch (e: Exception) {
                    SafeLog.w("ExploreViewModel", "City selection discovery error: ${e.message}")
                } finally {
                    _isDiscoveringGems.value = false
                }
            }
        }
    }

    fun updateUserLocation(lat: Double, lng: Double, context: Context? = null) {
        if (lat == 0.0 && lng == 0.0) return
        val prev = _userCoordinates.value
        val isFirstFix = prev == null
        if (isFirstFix || Math.abs(prev!!.first - lat) > 0.0005 || Math.abs(prev.second - lng) > 0.0005) {
            _userCoordinates.value = Pair(lat, lng)
            _isAwaitingLocation.value = false

            val detectedCityName = GlobalCityCatalog.findNearestCityName(lat, lng)
            _selectedGlobalCity.value = detectedCityName
            _cityProfile.value = CityProfileRegistry.getProfileForCity(detectedCityName, lat, lng)
            DefaultLocationConfig.setActiveFallbackCity(lat, lng, detectedCityName, context)

            viewModelScope.launch(Dispatchers.IO) {
                _isDiscoveringGems.value = true
                val banner = preScoutEngine.executePreScout(lat, lng, detectedCityName)
                _preScoutBannerState.value = banner

                // Immediately query Gemini AI for authentic tourist attractions, historic landmarks, dining gems within 1.5km walking radius
                try {
                    gemRepository.syncLiveOsmGems(
                        userLat = lat,
                        userLng = lng,
                        radiusMeters = 1500,
                        category = "all"
                    )
                } catch (e: Exception) {
                    SafeLog.w("ExploreViewModel", "Gemini vicinity discovery error: ${e.message}")
                } finally {
                    _isDiscoveringGems.value = false
                }
            }
        }
    }

    fun triggerNearbyDiscovery() {
        val coords = _userCoordinates.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isDiscoveringGems.value = true
            try {
                gemRepository.syncLiveOsmGems(
                    userLat = coords.first,
                    userLng = coords.second,
                    radiusMeters = 1500,
                    category = _selectedCategory.value
                )
            } finally {
                _isDiscoveringGems.value = false
            }
        }
    }

    fun dismissPreScoutBanner() {
        _preScoutBannerState.value = _preScoutBannerState.value.copy(isVisible = false)
    }

    fun filterPreScoutedSpots() {
        _selectedCategory.value = "all"
        _searchQuery.value = ""
    }

    fun cycleHeaderStyle() {
        _exploreHeaderStyle.value = (_exploreHeaderStyle.value + 1) % 2
    }

    fun dismissOsmMessage() {
        _osmScrapeMessage.value = null
    }

    fun scrapeOsmGems(lat: Double, lng: Double, category: String = "all") {
        if (_isScrapingOsm.value) return
        _isScrapingOsm.value = true
        _osmScrapeMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = gemRepository.syncLiveOsmGems(lat, lng, 1500, category)
                result.fold(
                    onSuccess = { count ->
                        _osmScrapeMessage.value = "$count gems discovered around your location"
                    },
                    onFailure = { e ->
                        _osmScrapeMessage.value = "Discovery note: ${e.message}"
                    }
                )
            } catch (e: Exception) {
                _osmScrapeMessage.value = "Discovery error: ${e.message}"
            } finally {
                _isScrapingOsm.value = false
                delay(4000)
                _osmScrapeMessage.value = null
            }
        }
    }

    fun openTouristicSourcesModal() {
        _showTouristicSourcesModal.value = true
    }

    fun dismissTouristicSourcesModal() {
        _showTouristicSourcesModal.value = false
    }

    fun syncTouristicSource(sourceId: String) {
        val coords = _userCoordinates.value ?: Pair(DefaultLocationConfig.DEFAULT_LAT, DefaultLocationConfig.DEFAULT_LNG)
        val city = _selectedGlobalCity.value
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingTouristicSources.value = true
            try {
                when (sourceId) {
                    "open_trip_map" -> gemRepository.syncOpenTripMapGems(coords.first, coords.second)
                    "wikivoyage_wikidata" -> gemRepository.syncWikidataGems(coords.first, coords.second)
                    "geonames_gazetteer" -> gemRepository.syncGeonamesGems(coords.first, coords.second)
                    "unesco_world_heritage" -> gemRepository.syncUnescoGems(coords.first, coords.second)
                    "europeana_cultural" -> gemRepository.syncEuropeanaGems(city, coords.first, coords.second)
                    "municipal_open_data" -> gemRepository.syncMunicipalGems(city, coords.first, coords.second)
                    else -> gemRepository.syncLiveOsmGems(coords.first, coords.second)
                }
            } catch (e: Exception) {
                SafeLog.w("ExploreViewModel", "Failed to sync touristic source $sourceId: ${e.message}")
            } finally {
                _isSyncingTouristicSources.value = false
            }
        }
    }

    fun syncAllTouristicSources() {
        val coords = _userCoordinates.value ?: Pair(DefaultLocationConfig.DEFAULT_LAT, DefaultLocationConfig.DEFAULT_LNG)
        val city = _selectedGlobalCity.value
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingTouristicSources.value = true
            try {
                gemRepository.syncMultiSourceGems(coords.first, coords.second, cityName = city)
            } catch (e: Exception) {
                SafeLog.w("ExploreViewModel", "Failed multi-source sync: ${e.message}")
            } finally {
                _isSyncingTouristicSources.value = false
            }
        }
    }
}

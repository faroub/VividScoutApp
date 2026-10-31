package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.RecentSearchEntity
import com.example.data.local.ScoutSpotEntity
import com.example.data.network.OverpassRetrofitClient
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

data class ExploreUiState(
    val filteredSpots: List<ScoutSpotEntity> = emptyList(),
    val allSpots: List<ScoutSpotEntity> = emptyList(),
    val savedSpots: List<ScoutSpotEntity> = emptyList(),
    val discoveredSpots: List<ScoutSpotEntity> = emptyList(),
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    val recentSearches: List<RecentSearchEntity> = emptyList(),
    val selectedGlobalCity: String = "Lisbon, Portugal",
    val showGlobalCityDialog: Boolean = false,
    val preScoutBannerState: PreScoutBannerState = PreScoutBannerState(),
    val exploreHeaderStyle: Int = 0,
    val isScrapingOsm: Boolean = false,
    val osmScrapeMessage: String? = null,
    val playingAudioId: String? = null
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

    private val _selectedCategory = MutableStateFlow("ALL")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGlobalCity = MutableStateFlow("Lisbon, Portugal")
    val selectedGlobalCity: StateFlow<String> = _selectedGlobalCity.asStateFlow()

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

    val filteredSpots: StateFlow<List<ScoutSpotEntity>> = combine(
        allSpots,
        _selectedCategory,
        _searchQuery
    ) { spots, category, query ->
        spots.filter { spot ->
            val matchesCategory = (category == "ALL" || spot.category.equals(category, ignoreCase = true))
            val matchesQuery = query.isBlank() ||
                spot.title.contains(query, ignoreCase = true) ||
                spot.description.contains(query, ignoreCase = true) ||
                spot.category.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        _preScoutBannerState
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
            showGlobalCityDialog = vals[8] as Boolean,
            preScoutBannerState = vals[9] as PreScoutBannerState,
            exploreHeaderStyle = _exploreHeaderStyle.value,
            isScrapingOsm = _isScrapingOsm.value,
            osmScrapeMessage = _osmScrapeMessage.value,
            playingAudioId = _playingAudioId.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureInitialGemsSeeded()
            preScoutEngine.preScoutCity(_selectedGlobalCity.value) { bannerState ->
                _preScoutBannerState.value = bannerState
            }
        }
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
            repository.toggleSaveSpot(spot)
        }
    }

    fun toggleDiscovered(spot: ScoutSpotEntity) {
        viewModelScope.launch {
            repository.markDiscovered(spot.id, spot.isDiscovered)
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

    fun selectGlobalCity(cityName: String) {
        _selectedGlobalCity.value = cityName
        _showGlobalCityDialog.value = false
        viewModelScope.launch(Dispatchers.IO) {
            preScoutEngine.preScoutCity(cityName) { bannerState ->
                _preScoutBannerState.value = bannerState
            }
        }
    }

    fun dismissPreScoutBanner() {
        _preScoutBannerState.value = _preScoutBannerState.value.copy(isVisible = false)
    }

    fun filterPreScoutedSpots() {
        _selectedCategory.value = "ALL"
        _searchQuery.value = ""
    }

    fun cycleHeaderStyle() {
        _exploreHeaderStyle.value = (_exploreHeaderStyle.value + 1) % 2
    }

    fun dismissOsmMessage() {
        _osmScrapeMessage.value = null
    }

    fun scrapeOsmGems(lat: Double, lng: Double, category: String = "ALL") {
        if (_isScrapingOsm.value) return
        _isScrapingOsm.value = true
        _osmScrapeMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawSpots = OverpassRetrofitClient.fetchLiveOverpassSpots(lat, lng, 2000.0)
                if (rawSpots.isNotEmpty()) {
                    rawSpots.forEach { spot ->
                        repository.insertSpot(spot)
                    }
                    _osmScrapeMessage.value = "${rawSpots.size} gems scraped from OSM"
                } else {
                    _osmScrapeMessage.value = "No new OSM gems found"
                }
            } catch (e: Exception) {
                _osmScrapeMessage.value = "OSM Scrape failed: ${e.message}"
            } finally {
                _isScrapingOsm.value = false
                delay(4000)
                _osmScrapeMessage.value = null
            }
        }
    }
}

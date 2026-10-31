package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScoutSpotEntity
import com.example.data.repository.ScoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab {
    EXPLORE,
    RADAR,
    COMMUNITY,
    SAVED,
    PROFILE
}

sealed interface AppScreen {
    data object Onboarding : AppScreen
    data class Main(val tab: MainTab) : AppScreen
}

data class MainAppUiState(
    val currentScreen: AppScreen = AppScreen.Main(MainTab.EXPLORE),
    val currentTab: MainTab = MainTab.EXPLORE,
    val selectedSpotForDetail: ScoutSpotEntity? = null,
    val showSubmitModal: Boolean = false
)

class MainViewModel(
    private val repository: ScoutRepository
) : ViewModel() {

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Main(MainTab.EXPLORE))
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _currentTab = MutableStateFlow(MainTab.EXPLORE)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _selectedSpotForDetail = MutableStateFlow<ScoutSpotEntity?>(null)
    val selectedSpotForDetail: StateFlow<ScoutSpotEntity?> = _selectedSpotForDetail.asStateFlow()

    private val _showSubmitModal = MutableStateFlow(false)
    val showSubmitModal: StateFlow<Boolean> = _showSubmitModal.asStateFlow()

    val uiState: StateFlow<MainAppUiState> = combine(
        _currentScreen,
        _currentTab,
        _selectedSpotForDetail,
        _showSubmitModal
    ) { screen, tab, spot, submit ->
        MainAppUiState(
            currentScreen = screen,
            currentTab = tab,
            selectedSpotForDetail = spot,
            showSubmitModal = submit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainAppUiState())

    fun navigateToTab(tab: MainTab) {
        _currentTab.value = tab
        _currentScreen.value = AppScreen.Main(tab)
    }

    fun navigateToScreen(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen is AppScreen.Main) {
            _currentTab.value = screen.tab
        }
    }

    fun selectSpotForDetail(spot: ScoutSpotEntity?) {
        _selectedSpotForDetail.value = spot
    }

    fun dismissSpotDetail() {
        _selectedSpotForDetail.value = null
    }

    fun openSubmitModal() {
        _showSubmitModal.value = true
    }

    fun dismissSubmitModal() {
        _showSubmitModal.value = false
    }

    fun submitSpot(
        title: String,
        description: String,
        category: String,
        latitude: Double,
        longitude: Double,
        photoUri: String? = null
    ) {
        viewModelScope.launch {
            repository.submitSpotDraft(
                name = title,
                category = category,
                photoUri = photoUri,
                notes = description,
                latitude = latitude,
                longitude = longitude
            )
            _showSubmitModal.value = false
        }
    }
}

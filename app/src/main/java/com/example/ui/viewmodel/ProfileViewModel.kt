package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AchievementEntity
import com.example.data.repository.ScoutRepository
import com.example.service.ProximityPrefetchService
import com.example.ui.theme.AppLanguage
import com.example.util.BatteryStatus
import com.example.util.MovementVector
import com.example.util.PrefetchPowerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val achievements: List<AchievementEntity> = emptyList(),
    val unlockedAchievementsCount: Int = 0,
    val userPoints: Int = 0,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val showLanguageDialog: Boolean = false,
    val isOfflineBannerVisible: Boolean = false,
    val showClearCacheDialog: Boolean = false,
    val isPrefetchServiceRunning: Boolean = false,
    val isPrefetching: Boolean = false,
    val activePrefetchVector: MovementVector? = null,
    val prefetchedGemsCount: Int = 0,
    val prefetchStatusMessage: String = "",
    val activePowerMode: PrefetchPowerMode = PrefetchPowerMode.OPTIMAL_WALKING,
    val prefetchBatteryStatus: BatteryStatus = BatteryStatus(),
    val prefetchSpeedKmh: Float = 0f
)

class ProfileViewModel(
    private val repository: ScoutRepository
) : ViewModel() {

    val achievements: StateFlow<List<AchievementEntity>> = repository.allAchievements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unlockedAchievementsCount: StateFlow<Int> = repository.unlockedAchievementsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val userPoints: StateFlow<Int> = repository.userPoints
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _appLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _showLanguageDialog = MutableStateFlow(false)
    val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

    private val _isOfflineBannerVisible = MutableStateFlow(false)
    val isOfflineBannerVisible: StateFlow<Boolean> = _isOfflineBannerVisible.asStateFlow()

    private val _showClearCacheDialog = MutableStateFlow(false)
    val showClearCacheDialog: StateFlow<Boolean> = _showClearCacheDialog.asStateFlow()

    val isPrefetchServiceRunning: StateFlow<Boolean> = ProximityPrefetchService.isServiceRunning
    val isPrefetching: StateFlow<Boolean> = ProximityPrefetchService.isPrefetching
    val activePrefetchVector: StateFlow<MovementVector?> = ProximityPrefetchService.activeVector
    val prefetchedGemsCount: StateFlow<Int> = ProximityPrefetchService.prefetchedCount
    val prefetchStatusMessage: StateFlow<String> = ProximityPrefetchService.statusMessage
    val activePowerMode: StateFlow<PrefetchPowerMode> = ProximityPrefetchService.activePowerMode
    val prefetchBatteryStatus: StateFlow<BatteryStatus> = ProximityPrefetchService.batteryStatus
    val prefetchSpeedKmh: StateFlow<Float> = ProximityPrefetchService.currentSpeedKmh

    val uiState: StateFlow<ProfileUiState> = combine(
        achievements,
        unlockedAchievementsCount,
        userPoints,
        _appLanguage,
        _showLanguageDialog,
        _isOfflineBannerVisible,
        _showClearCacheDialog,
        isPrefetchServiceRunning
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        ProfileUiState(
            achievements = values[0] as List<AchievementEntity>,
            unlockedAchievementsCount = values[1] as Int,
            userPoints = values[2] as Int,
            appLanguage = values[3] as AppLanguage,
            showLanguageDialog = values[4] as Boolean,
            isOfflineBannerVisible = values[5] as Boolean,
            showClearCacheDialog = values[6] as Boolean,
            isPrefetchServiceRunning = values[7] as Boolean,
            isPrefetching = isPrefetching.value,
            activePrefetchVector = activePrefetchVector.value,
            prefetchedGemsCount = prefetchedGemsCount.value,
            prefetchStatusMessage = prefetchStatusMessage.value,
            activePowerMode = activePowerMode.value,
            prefetchBatteryStatus = prefetchBatteryStatus.value,
            prefetchSpeedKmh = prefetchSpeedKmh.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        _showLanguageDialog.value = false
    }

    fun openLanguageDialog() {
        _showLanguageDialog.value = true
    }

    fun dismissLanguageDialog() {
        _showLanguageDialog.value = false
    }

    fun toggleOfflineBanner() {
        _isOfflineBannerVisible.value = !_isOfflineBannerVisible.value
    }

    fun openClearCacheDialog() {
        _showClearCacheDialog.value = true
    }

    fun dismissClearCacheDialog() {
        _showClearCacheDialog.value = false
    }

    fun closeLanguageDialog() {
        _showLanguageDialog.value = false
    }

    fun confirmClearCache() {
        clearCache()
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCachedData()
            _showClearCacheDialog.value = false
        }
    }

    fun startProximityPrefetch(context: android.content.Context, lat: Double = 38.7112, lng: Double = -9.1423, bearing: Float = 45f) {
        ProximityPrefetchService.start(context, lat, lng, bearing)
    }

    fun stopProximityPrefetch(context: android.content.Context) {
        ProximityPrefetchService.stop(context)
    }

    fun triggerManualPrefetch(context: android.content.Context, lat: Double = 38.7112, lng: Double = -9.1423) {
        ProximityPrefetchService.start(context, lat, lng, 45f)
    }
}

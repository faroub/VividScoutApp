package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import com.example.ui.components.OfflineSyncStatus
import com.example.ui.viewmodel.CommunityUiState
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.ExploreUiState
import com.example.ui.viewmodel.ExploreViewModel
import com.example.ui.viewmodel.LocationUiState
import com.example.ui.viewmodel.MainAppUiState
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.OfflineMapsUiState
import com.example.ui.viewmodel.OfflineMapsViewModel
import com.example.ui.viewmodel.ProfileUiState
import com.example.ui.viewmodel.ProfileViewModel

/**
 * The tabbed content area of the main app: routes the active tab to its screen and
 * owns the tab transition animation. Extracted from [com.example.VividScoutApp] so the
 * activity no longer wires four screens' worth of parameters inline.
 */
@Composable
fun MainTabContent(
    mainViewModel: MainViewModel,
    exploreViewModel: ExploreViewModel,
    communityViewModel: CommunityViewModel,
    offlineMapsViewModel: OfflineMapsViewModel,
    profileViewModel: ProfileViewModel,
    mainState: MainAppUiState,
    exploreState: ExploreUiState,
    communityState: CommunityUiState,
    offlineMapsState: OfflineMapsUiState,
    profileState: ProfileUiState,
    locationState: LocationUiState,
    isUsingDefaultFallbackLocation: Boolean = false,
    requestLocationPermission: () -> Unit,
    context: Context
) {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    AnimatedContent(
        targetState = mainState.currentTab,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tab_navigation_transition"
    ) { tab ->
            when (tab) {
                MainTab.EXPLORE -> {
                    ExploreScreen(
                        spots = exploreState.filteredSpots,
                        selectedCategory = exploreState.selectedCategory,
                        searchQuery = exploreState.searchQuery,
                        headerStyle = exploreState.exploreHeaderStyle,
                        isOfflineBannerVisible = profileState.isOfflineBannerVisible,
                        offlineDraftCount = offlineMapsState.offlineDrafts.size,
                        playingAudioId = exploreState.playingAudioId,
                        activeMapLayers = exploreState.activeMapLayers,
                        onToggleMapLayer = { exploreViewModel.toggleMapLayer(it) },
                        onSetAllMapLayers = { exploreViewModel.setAllMapLayers(it) },
                        userLocation = locationState.userLocation,
                        userCoordinates = locationState.safeCoordinates,
                        hasLocationPermission = locationState.hasLocationPermission,
                        isUsingGpsLocation = locationState.isUsingGpsLocation,
                        isUsingDefaultFallbackLocation = isUsingDefaultFallbackLocation,
                        selectedGlobalCity = exploreState.selectedGlobalCity,
                        onGpsClick = { exploreViewModel.openGlobalCityDialog() },
                        onRequestLocationPermission = { requestLocationPermission() },
                        onCategorySelected = { exploreViewModel.setCategory(it) },
                        onSearchQueryChange = { exploreViewModel.setSearchQuery(it) },
                        onToggleSave = { exploreViewModel.toggleSave(it) },
                        onUpvote = { exploreViewModel.upvote(it) },
                        onToggleAudioLore = { exploreViewModel.toggleAudioLore(it) },
                        onSpotClick = { mainViewModel.selectSpotForDetail(it) },
                        onAddSpotClick = { mainViewModel.openSubmitModal() },
                        onPlanRouteClick = { offlineMapsViewModel.openRoutePlanner() },
                        onDismissOfflineBanner = { profileViewModel.toggleOfflineBanner() },
                        onOpenQueueDialog = { offlineMapsViewModel.openQueueDialog() },
                        syncStatus = OfflineSyncStatus(totalLocalGems = exploreState.allSpots.size, pendingDraftsCount = offlineMapsState.offlineDrafts.size, isSyncing = exploreState.isScrapingOsm),
                        onOpenDiagnostics = { offlineMapsViewModel.openDiagnosticDialog() },
                        cacheHealth = offlineMapsState.vicinityCacheHealth,
                        onOpenCacheHealth = { offlineMapsViewModel.openCacheHealthDialog() },
                        isDiscovering = exploreState.isScrapingOsm,
                        discoveryMessage = exploreState.osmScrapeMessage,
                        onTriggerDiscover = {
                            exploreViewModel.scrapeOsmGems(
                                locationState.safeCoordinates.first,
                                locationState.safeCoordinates.second,
                                category = exploreState.selectedCategory
                            )
                        },
                        preScoutState = exploreState.preScoutBannerState,
                        onDismissPreScout = { exploreViewModel.dismissPreScoutBanner() },
                        onViewPreScout = { exploreViewModel.filterPreScoutedSpots() },
                        onOpenTouristicSources = { exploreViewModel.openTouristicSourcesModal() },
                        recentSearches = exploreState.recentSearches,
                        onDeleteRecentSearch = { exploreViewModel.deleteRecentSearch(it) },
                        onClearAllRecentSearches = { exploreViewModel.clearAllRecentSearches() }
                    )
                }

                MainTab.COMMUNITY -> {
                    CommunityFeedScreen(
                        tips = communityState.tips,
                        spots = exploreState.allSpots,
                        syncPhase = communityState.syncPhase,
                        lastSyncResult = communityState.lastSyncResult,
                        onTriggerSync = { communityViewModel.triggerSync() },
                        onUpvoteTip = { communityViewModel.upvoteTip(it) },
                        onPostTip = { spotId, spotTitle, content, isStatus, category ->
                            communityViewModel.postTip(spotId, spotTitle, content, isStatus, category)
                        },
                        onSpotClick = { spotId ->
                            exploreState.allSpots.find { it.id == spotId }?.let {
                                mainViewModel.selectSpotForDetail(it)
                            }
                        },
                        userCoordinates = locationState.safeCoordinates,
                        successMessage = communityState.postTipSuccessMessage
                    )
                }

                MainTab.SAVED -> {
                    SavedScreen(
                        savedSpots = exploreState.savedSpots,
                        discoveredSpots = exploreState.discoveredSpots,
                        onToggleSave = { exploreViewModel.toggleSave(it) },
                        onToggleDiscovered = { exploreViewModel.toggleDiscovered(it) },
                        onSpotClick = { mainViewModel.selectSpotForDetail(it) },
                        onPlanWalkingTrail = {
                            offlineMapsViewModel.openRoutePlanner()
                        },
                        onPlanRouteToSpot = { spot ->
                            offlineMapsViewModel.openRoutePlanner(destination = spot)
                        }
                    )
                }

                MainTab.PROFILE -> {
                    ProfileScreen(
                        userPoints = profileState.userPoints,
                        discoveredCount = exploreState.discoveredSpots.size,
                        offlineDraftCount = offlineMapsState.offlineDrafts.size,
                        isOfflineBannerVisible = profileState.isOfflineBannerVisible,
                        onToggleOfflineBanner = { profileViewModel.toggleOfflineBanner() },
                        onOpenQueue = { offlineMapsViewModel.openQueueDialog() },
                        onAddSpotClick = { mainViewModel.openSubmitModal() },
                        onReturnToOnboarding = { mainViewModel.navigateToScreen(com.example.ui.viewmodel.AppScreen.Onboarding) },
                        onOpenLanguageDialog = { profileViewModel.openLanguageDialog() },
                        hasLocationPermission = locationState.hasLocationPermission,
                        isPreciseLocation = locationState.isPreciseLocation,
                        userCoordinates = locationState.safeCoordinates,
                        isUsingGpsLocation = locationState.isUsingGpsLocation,
                        selectedGlobalCity = exploreState.selectedGlobalCity,
                        onRequestLocationPermission = { requestLocationPermission() },
                        offlineMapPacks = offlineMapsState.offlineMapPacks,
                        totalOfflineStorageBytes = offlineMapsState.offlineStorageBytes,
                        onDownloadOfflinePack = { packId ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            offlineMapsViewModel.downloadOfflinePack(packId)
                        },
                        onDeleteOfflinePack = { packId -> offlineMapsViewModel.deleteOfflinePack(packId) },
                        onDownloadCurrentGpsPack = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            offlineMapsViewModel.downloadCurrentGpsPack()
                        },
                        customRegions = offlineMapsState.downloadedRegions,
                        onDeleteCustomRegion = { offlineMapsViewModel.deleteCustomRegion(it) },
                        onOpenDiagnostics = { offlineMapsViewModel.openDiagnosticDialog() },
                        totalSpotsCount = exploreState.allSpots.size,
                        savedCount = exploreState.savedSpots.size,
                        databaseSizeBytes = offlineMapsState.databaseSizeBytes,
                        onClearCacheClick = { profileViewModel.openClearCacheDialog() },
                        achievements = profileState.achievements,
                        unlockedAchievementsCount = profileState.unlockedAchievementsCount,
                        isPrefetchActive = profileState.isPrefetchServiceRunning,
                        isPrefetching = profileState.isPrefetching,
                        prefetchVector = profileState.activePrefetchVector,
                        prefetchedGemsCount = profileState.prefetchedGemsCount,
                        prefetchStatusMessage = profileState.prefetchStatusMessage,
                        activePowerMode = profileState.activePowerMode,
                        batteryStatus = profileState.prefetchBatteryStatus,
                        prefetchSpeedKmh = profileState.prefetchSpeedKmh,
                        onTogglePrefetch = { active ->
                            if (active) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                profileViewModel.startProximityPrefetch(
                                    context,
                                    locationState.safeCoordinates.first,
                                    locationState.safeCoordinates.second
                                )
                            } else {
                                profileViewModel.stopProximityPrefetch(context)
                            }
                        },
                        onTriggerManualPrefetch = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            profileViewModel.triggerManualPrefetch(
                                context,
                                locationState.safeCoordinates.first,
                                locationState.safeCoordinates.second
                            )
                        }
                    )
                }
            }
    }
}

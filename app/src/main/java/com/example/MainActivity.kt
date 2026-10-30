package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.ScoutSpotEntity
import com.example.ui.components.ARNavigationOverlay
import com.example.ui.components.BottomNavigationDock
import com.example.ui.components.VividScoutTopBar
import com.example.ui.dialogs.ClearCacheConfirmationDialog
import com.example.ui.dialogs.LanguageSelectionDialog
import com.example.ui.dialogs.LocationPermissionRationaleDialog
import com.example.ui.dialogs.LocationPermissionSettingsDialog
import com.example.ui.dialogs.OfflineQueueDialog
import com.example.ui.dialogs.SpotDetailBottomSheet
import com.example.ui.screens.CommunityFeedScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.screens.SpotGemModal
import com.example.ui.theme.AppStrings
import com.example.engine.ScoutVectorEngine
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.VividScoutTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.ScoutViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ScoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        com.example.util.configureAppCheck(this, intent)

        enableEdgeToEdge()

        // Initialize the Scout Vector Map Engine on app startup
        ScoutVectorEngine.initialize(applicationContext)

        setContent {
            val currentScreen by viewModel.currentScreen.collectAsState()
            val isDarkTheme = currentScreen is AppScreen.Onboarding

            VividScoutTheme(darkTheme = isDarkTheme) {
                VividScoutApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun VividScoutApp(viewModel: ScoutViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val filteredSpots by viewModel.filteredSpots.collectAsState()
    val allSpots by viewModel.allSpots.collectAsState()
    val savedSpots by viewModel.savedSpots.collectAsState()
    val discoveredSpots by viewModel.discoveredSpots.collectAsState()
    val offlineDrafts by viewModel.offlineDrafts.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isOfflineBannerVisible by viewModel.isOfflineBannerVisible.collectAsState()
    val showSubmitModal by viewModel.showSubmitModal.collectAsState()
    val showQueueDialog by viewModel.showQueueDialog.collectAsState()
    val userPoints by viewModel.userPoints.collectAsState()
    val playingAudioId by viewModel.playingAudioId.collectAsState()
    val exploreHeaderStyle by viewModel.exploreHeaderStyle.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val showLanguageDialog by viewModel.showLanguageDialog.collectAsState()
    val selectedGlobalCity by viewModel.selectedGlobalCity.collectAsState()
    val isUsingGpsLocation by viewModel.isUsingGpsLocation.collectAsState()
    val showGlobalCityDialog by viewModel.showGlobalCityDialog.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsState()
    val isPreciseLocation by viewModel.isPreciseLocation.collectAsState()
    val userCoordinates by viewModel.userCoordinates.collectAsState()
    val showLocationRationaleDialog by viewModel.showLocationRationaleDialog.collectAsState()
    val showLocationSettingsDialog by viewModel.showLocationSettingsDialog.collectAsState()
    val locationDeniedCount by viewModel.locationDeniedCount.collectAsState()
    val isScrapingOsm by viewModel.isScrapingOsm.collectAsState()
    val osmScrapeMessage by viewModel.osmScrapeMessage.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val offlineMapPacks by viewModel.offlineMapPacks.collectAsState()
    val offlineStorageBytes by viewModel.offlineStorageBytes.collectAsState()
    val activeTrail by viewModel.activeTrail.collectAsState()
    val showDiagnosticDialog by viewModel.showDiagnosticDialog.collectAsState()
    val diagnosticLog by viewModel.diagnosticLog.collectAsState()
    val isDiagnosticRunning by viewModel.isDiagnosticRunning.collectAsState()
    val vicinityCacheHealth by viewModel.vicinityCacheHealth.collectAsState()
    val showCacheHealthDialog by viewModel.showCacheHealthDialog.collectAsState()
    val isRoutePlannerOpen by viewModel.isRoutePlannerOpen.collectAsState()
    val routePlannerOrigin by viewModel.routePlannerOrigin.collectAsState()
    val routePlannerDestination by viewModel.routePlannerDestination.collectAsState()
    val plannedRoute by viewModel.plannedRoute.collectAsState()
    val isCalculatingRoute by viewModel.isCalculatingRoute.collectAsState()
    val isRegionDownloadMode by viewModel.isRegionDownloadMode.collectAsState()
    val activeSelectionBounds by viewModel.activeSelectionBounds.collectAsState()
    val showRegionDownloadDialog by viewModel.showRegionDownloadDialog.collectAsState()
    val downloadedRegions by viewModel.downloadedRegions.collectAsState()
    val isDownloadingRegion by viewModel.isDownloadingRegion.collectAsState()
    val showClearCacheDialog by viewModel.showClearCacheDialog.collectAsState()
    val databaseSizeBytes by viewModel.databaseSizeBytes.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val unlockedAchievementsCount by viewModel.unlockedAchievementsCount.collectAsState()
    val isPrefetchActive by viewModel.isPrefetchServiceRunning.collectAsState()
    val isPrefetching by viewModel.isPrefetching.collectAsState()
    val prefetchVector by viewModel.activePrefetchVector.collectAsState()
    val prefetchedGemsCount by viewModel.prefetchedGemsCount.collectAsState()
    val prefetchStatusMessage by viewModel.prefetchStatusMessage.collectAsState()
    val activePowerMode by viewModel.activePowerMode.collectAsState()
    val prefetchBatteryStatus by viewModel.prefetchBatteryStatus.collectAsState()
    val prefetchSpeedKmh by viewModel.prefetchSpeedKmh.collectAsState()
    val communityTips by viewModel.allCommunityTips.collectAsState()
    val meshSyncPhase by viewModel.meshSyncPhase.collectAsState()
    val lastMeshSyncResult by viewModel.lastMeshSyncResult.collectAsState()
    val postTipSuccessMessage by viewModel.postTipSuccessMessage.collectAsState()

    // Automatically refresh permission state when returning to the app from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocationPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.onLocationPermissionResult(fineGranted, coarseGranted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val openAppSettings = {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    val requestLocationPermission = {
        if (hasLocationPermission) {
            viewModel.refreshLocationPermissions()
        } else if (locationDeniedCount >= 2) {
            viewModel.openLocationSettingsDialog()
        } else {
            viewModel.openLocationRationaleDialog()
        }
    }

    var selectedSpotForDetail by remember { mutableStateOf<ScoutSpotEntity?>(null) }
    var isArModeActive by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalLayoutDirection provides (if (appLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr),
        LocalAppLanguage provides appLanguage,
        LocalAppStrings provides AppStrings.forLanguage(appLanguage)
    ) {
        when (currentScreen) {
            is AppScreen.Onboarding -> {
                OnboardingScreen(
                    onExploreClick = {
                        viewModel.navigateToMain(MainTab.EXPLORE)
                        if (!hasLocationPermission && locationDeniedCount == 0) {
                            viewModel.openLocationRationaleDialog()
                        }
                    },
                    onSignInClick = { viewModel.navigateToMain(MainTab.EXPLORE) },
                    onLanguageClick = { viewModel.openLanguageDialog() }
                )
            }

            is AppScreen.Main -> {
                // Handle system back navigation to return to explore tab if on another tab or exit AR mode
                BackHandler(enabled = isArModeActive || currentTab != MainTab.EXPLORE) {
                    if (isArModeActive) {
                        isArModeActive = false
                    } else {
                        viewModel.selectTab(MainTab.EXPLORE)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        VividScoutTopBar(
                            onSearchClick = {
                                viewModel.selectTab(MainTab.EXPLORE)
                            },
                            onRadarClick = {
                                viewModel.selectTab(MainTab.RADAR)
                            },
                            onProfileClick = {
                                viewModel.selectTab(MainTab.PROFILE)
                            },
                            onToggleHeaderStyle = {
                                viewModel.toggleHeaderStyle()
                            },
                            onLanguageClick = {
                                viewModel.openLanguageDialog()
                            },
                            onGpsClick = {
                                viewModel.openGlobalCityDialog()
                            },
                            onArClick = {
                                isArModeActive = true
                            },
                            hasLocationPermission = hasLocationPermission,
                            selectedGlobalCity = selectedGlobalCity,
                            isUsingGpsLocation = isUsingGpsLocation,
                            isPrefetchActive = isPrefetchActive,
                            prefetchedCount = prefetchedGemsCount,
                            prefetchPowerMode = activePowerMode,
                            onPrefetchClick = { viewModel.selectTab(MainTab.PROFILE) }
                        )
                    },
                    bottomBar = {
                        BottomNavigationDock(
                            currentTab = currentTab,
                            onTabSelected = { tab -> viewModel.selectTab(tab) },
                            onAddSpotClick = { viewModel.openSubmitModal() }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tab_navigation_transition"
                        ) { tab ->
                            when (tab) {
                                MainTab.EXPLORE -> {
                                    ExploreScreen(
                                        spots = filteredSpots,
                                        selectedCategory = selectedCategory,
                                        searchQuery = searchQuery,
                                        headerStyle = exploreHeaderStyle,
                                        isOfflineBannerVisible = isOfflineBannerVisible,
                                        offlineDraftCount = offlineDrafts.size,
                                        playingAudioId = playingAudioId,
                                        userLocation = userLocation,
                                        userCoordinates = userCoordinates,
                                        hasLocationPermission = hasLocationPermission,
                                        onRequestLocationPermission = { requestLocationPermission() },
                                        onCategorySelected = { cat -> viewModel.setCategory(cat) },
                                        onSearchQueryChange = { q -> viewModel.setSearchQuery(q) },
                                        onToggleSave = { spot -> viewModel.toggleSave(spot) },
                                        onUpvote = { spot -> viewModel.upvote(spot) },
                                        onToggleAudioLore = { id -> viewModel.toggleAudioLore(id) },
                                        onSpotClick = { spot -> selectedSpotForDetail = spot },
                                        onAddSpotClick = { viewModel.openSubmitModal() },
                                        onRadarClick = { viewModel.selectTab(MainTab.RADAR) },
                                        onDismissOfflineBanner = { viewModel.dismissOfflineBanner() },
                                        onOpenQueueDialog = { viewModel.openQueueDialog() },
                                        syncStatus = syncStatus,
                                        onOpenDiagnostics = { viewModel.openDiagnosticDialog() },
                                        cacheHealth = vicinityCacheHealth,
                                        onOpenCacheHealth = { viewModel.openCacheHealthDialog() },
                                        isDiscovering = isScrapingOsm,
                                        discoveryMessage = osmScrapeMessage,
                                        onTriggerDiscover = { viewModel.scrapeOsmGems(userCoordinates.first, userCoordinates.second, category = selectedCategory) }
                                    )
                                }

                                MainTab.RADAR -> {
                                    RadarScreen(
                                        spots = filteredSpots,
                                        selectedCategory = selectedCategory,
                                        onCategorySelected = { cat -> viewModel.setCategory(cat) },
                                        userLocation = userLocation,
                                        hasLocationPermission = hasLocationPermission,
                                        isPreciseLocation = isPreciseLocation,
                                        onRequestLocationPermission = { requestLocationPermission() },
                                        onSpotClick = { spot -> selectedSpotForDetail = spot },
                                        onToggleSave = { spot -> viewModel.toggleSave(spot) },
                                        isScrapingOsm = isScrapingOsm,
                                        osmScrapeMessage = osmScrapeMessage,
                                        onScrapeOsm = { viewModel.scrapeOsmGems(userCoordinates.first, userCoordinates.second) },
                                        onDismissOsmMessage = { viewModel.dismissOsmMessage() },
                                        syncStatus = syncStatus,
                                        activeTrail = activeTrail,
                                        onPlanWalkingTrail = { viewModel.openRoutePlanner() },
                                        onClearWalkingTrail = { viewModel.clearPlannedRoute() },
                                        cacheHealth = vicinityCacheHealth,
                                        onOpenCacheHealth = { viewModel.openCacheHealthDialog() },
                                        isRegionDownloadMode = isRegionDownloadMode,
                                        selectionBounds = activeSelectionBounds,
                                        onUpdateSelectionBounds = { viewModel.updateSelectionBounds(it) },
                                        onStartRegionDownload = { viewModel.enterRegionDownloadMode() },
                                        onCancelRegionDownload = { viewModel.exitRegionDownloadMode() },
                                        onConfirmRegionDownload = { viewModel.openRegionDownloadDialog() },
                                        recentSearches = recentSearches,
                                        onSelectRecentSearch = { viewModel.executeRecentSearch(it) },
                                        onDeleteRecentSearch = { viewModel.deleteRecentSearch(it) },
                                        onClearAllRecentSearches = { viewModel.clearAllRecentSearches() },
                                        achievements = achievements
                                    )
                                }

                                MainTab.COMMUNITY -> {
                                    CommunityFeedScreen(
                                        tips = communityTips,
                                        spots = allSpots,
                                        syncPhase = meshSyncPhase,
                                        lastSyncResult = lastMeshSyncResult,
                                        onTriggerSync = { viewModel.triggerPeerMeshSync() },
                                        onUpvoteTip = { tipId -> viewModel.upvoteCommunityTip(tipId) },
                                        onPostTip = { spotId, spotTitle, content, isStatus, category ->
                                            viewModel.postCommunityTip(spotId, spotTitle, content, isStatus, category)
                                        },
                                        onSpotClick = { spotId ->
                                            val spot = allSpots.find { it.id == spotId }
                                            if (spot != null) {
                                                selectedSpotForDetail = spot
                                            }
                                        },
                                        userCoordinates = userCoordinates,
                                        successMessage = postTipSuccessMessage
                                    )
                                }

                                MainTab.SAVED -> {
                                    SavedScreen(
                                        savedSpots = savedSpots,
                                        discoveredSpots = discoveredSpots,
                                        onToggleSave = { spot -> viewModel.toggleSave(spot) },
                                        onToggleDiscovered = { spot -> viewModel.toggleDiscovered(spot) },
                                        onSpotClick = { spot -> selectedSpotForDetail = spot },
                                        onPlanWalkingTrail = {
                                            viewModel.openRoutePlanner()
                                            viewModel.selectTab(MainTab.RADAR)
                                        },
                                        onPlanRouteToSpot = { spot ->
                                            viewModel.openRoutePlanner(destination = spot)
                                            viewModel.selectTab(MainTab.RADAR)
                                        }
                                    )
                                }

                                MainTab.PROFILE -> {
                                    ProfileScreen(
                                        userPoints = userPoints,
                                        discoveredCount = discoveredSpots.size,
                                        offlineDraftCount = offlineDrafts.size,
                                        isOfflineBannerVisible = isOfflineBannerVisible,
                                        onToggleOfflineBanner = { viewModel.toggleOfflineBanner() },
                                        onOpenQueue = { viewModel.openQueueDialog() },
                                        onAddSpotClick = { viewModel.openSubmitModal() },
                                        onReturnToOnboarding = { viewModel.navigateToOnboarding() },
                                        onOpenLanguageDialog = { viewModel.openLanguageDialog() },
                                        hasLocationPermission = hasLocationPermission,
                                        isPreciseLocation = isPreciseLocation,
                                        userCoordinates = userCoordinates,
                                        onRequestLocationPermission = { requestLocationPermission() },
                                        offlineMapPacks = offlineMapPacks,
                                        totalOfflineStorageBytes = offlineStorageBytes,
                                        onDownloadOfflinePack = { packId ->
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            viewModel.downloadOfflineMapPack(packId)
                                        },
                                        onDeleteOfflinePack = { packId -> viewModel.deleteOfflineMapPack(packId) },
                                         onDownloadCurrentGpsPack = {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            viewModel.downloadCurrentGpsMapPack()
                                        },
                                        customRegions = downloadedRegions,
                                        onDeleteCustomRegion = { viewModel.deleteDownloadedRegion(it) },
                                        onOpenDiagnostics = { viewModel.openDiagnosticDialog() },
                                        totalSpotsCount = allSpots.size,
                                        savedCount = savedSpots.size,
                                        databaseSizeBytes = databaseSizeBytes,
                                        onClearCacheClick = { viewModel.openClearCacheDialog() },
                                        achievements = achievements,
                                        unlockedAchievementsCount = unlockedAchievementsCount,
                                        isPrefetchActive = isPrefetchActive,
                                        isPrefetching = isPrefetching,
                                        prefetchVector = prefetchVector,
                                        prefetchedGemsCount = prefetchedGemsCount,
                                        prefetchStatusMessage = prefetchStatusMessage,
                                        activePowerMode = activePowerMode,
                                        batteryStatus = prefetchBatteryStatus,
                                        prefetchSpeedKmh = prefetchSpeedKmh,
                                        onTogglePrefetch = { active ->
                                            if (active) {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                                viewModel.startProximityPrefetch()
                                            } else {
                                                viewModel.stopProximityPrefetch()
                                            }
                                        },
                                        onTriggerManualPrefetch = {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            viewModel.triggerManualPrefetch()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // AR Navigation Overlay (Fullscreen Camera HUD)
                if (isArModeActive) {
                    ARNavigationOverlay(
                        spots = filteredSpots.ifEmpty { allSpots },
                        userCoordinates = userCoordinates,
                        onClose = { isArModeActive = false },
                        onSelectSpot = { spot ->
                            selectedSpotForDetail = spot
                            isArModeActive = false
                        },
                        onPlanRoute = { spot ->
                            viewModel.openRoutePlanner(destination = spot)
                            viewModel.selectTab(MainTab.RADAR)
                            isArModeActive = false
                        }
                    )
                }

                // Spot Detail Bottom Sheet
                selectedSpotForDetail?.let { spot ->
                    SpotDetailBottomSheet(
                        spot = spot,
                        isPlayingAudio = playingAudioId == spot.id,
                        onDismiss = { selectedSpotForDetail = null },
                        onToggleSave = { viewModel.toggleSave(spot) },
                        onToggleDiscovered = { viewModel.toggleDiscovered(spot) },
                        onSaveNote = { note -> viewModel.updateSpotNotes(spot.id, note) },
                        onUpvote = { viewModel.upvote(spot) },
                        onToggleAudio = { viewModel.toggleAudioLore(spot.id) },
                        onPlanRoute = {
                            viewModel.openRoutePlanner(destination = spot)
                            viewModel.selectTab(MainTab.RADAR)
                        }
                    )
                }

                // Offline A* Walking Route Planner Sheet
                com.example.ui.components.RoutePlannerSheet(
                    isOpen = isRoutePlannerOpen,
                    onDismiss = { viewModel.closeRoutePlanner() },
                    origin = routePlannerOrigin,
                    destination = routePlannerDestination,
                    allSpots = allSpots,
                    plannedRoute = plannedRoute,
                    isCalculating = isCalculatingRoute,
                    onSelectOrigin = { viewModel.setRouteOrigin(it) },
                    onSelectDestination = { viewModel.setRouteDestination(it) },
                    onSwapEndpoints = { viewModel.swapRouteEndpoints() },
                    onClearRoute = { viewModel.clearPlannedRoute() }
                )

                // Active Offline Walking Trail Bottom Sheet
                activeTrail?.let { trail ->
                    com.example.ui.dialogs.OfflineTrailBottomSheet(
                        trail = trail,
                        onDismiss = { viewModel.clearWalkingTrail() },
                        onClearTrail = { viewModel.clearWalkingTrail() },
                        onSpotClick = { spot -> selectedSpotForDetail = spot }
                    )
                }

                // Spot a Gem Offline Submission Modal
                if (showSubmitModal) {
                    SpotGemModal(
                        onDismiss = { viewModel.closeSubmitModal() },
                        onSubmit = { name, category, photoUri, notes ->
                            viewModel.submitGem(name, category, photoUri, notes) {
                                // On complete
                            }
                        },
                        draftCount = offlineDrafts.size,
                        userLocation = userLocation,
                        userCoordinates = userCoordinates
                    )
                }

                // Offline Queue Dialog
                if (showQueueDialog) {
                    OfflineQueueDialog(
                        drafts = offlineDrafts,
                        onSyncAll = { viewModel.syncAllDrafts() },
                        onDismiss = { viewModel.closeQueueDialog() },
                        onOpenDiagnostics = { viewModel.openDiagnosticDialog() }
                    )
                }

                // Overpass API Diagnostics Dialog
                if (showDiagnosticDialog) {
                    com.example.ui.dialogs.OverpassDiagnosticDialog(
                        log = diagnosticLog,
                        isRunning = isDiagnosticRunning,
                        onDismiss = { viewModel.closeDiagnosticDialog() },
                        onRerunQuery = { viewModel.runDiagnosticQuery() }
                    )
                }

                // Cache Health & Vicinity Diagnostic Dialog
                if (showCacheHealthDialog) {
                    com.example.ui.dialogs.CacheHealthDialog(
                        health = vicinityCacheHealth,
                        onDismiss = { viewModel.closeCacheHealthDialog() },
                        onPreCacheVicinity = { viewModel.prefetchVicinityCache() },
                        onOpenDiagnostics = {
                            viewModel.closeCacheHealthDialog()
                            viewModel.openDiagnosticDialog()
                        }
                    )
                }

                // Global City / Destination Selector Dialog
                if (showGlobalCityDialog) {
                    com.example.ui.dialogs.GlobalCitySelectionDialog(
                        selectedCity = selectedGlobalCity,
                        isUsingGps = isUsingGpsLocation,
                        onSelectCity = { city -> viewModel.selectGlobalCity(city) },
                        onSelectGpsLocation = {
                            viewModel.selectCurrentGpsLocation()
                            if (!hasLocationPermission) {
                                requestLocationPermission()
                            }
                        },
                        onDismiss = { viewModel.closeGlobalCityDialog() }
                    )
                }

                // Language Selection Dialog
                if (showLanguageDialog) {
                    LanguageSelectionDialog(
                        currentLanguage = appLanguage,
                        onSelectLanguage = { lang ->
                            viewModel.setLanguage(lang)
                        },
                        onDismiss = { viewModel.closeLanguageDialog() }
                    )
                }

                // Location Permission Rationale Dialog
                if (showLocationRationaleDialog) {
                    LocationPermissionRationaleDialog(
                        onAllowClick = {
                            viewModel.dismissLocationRationaleDialog()
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onDismiss = { viewModel.dismissLocationRationaleDialog() }
                    )
                }

                // Location Permission Settings Dialog (for permanently denied or multi-denied cases)
                if (showLocationSettingsDialog) {
                    LocationPermissionSettingsDialog(
                        onOpenSettingsClick = {
                            viewModel.dismissLocationSettingsDialog()
                            openAppSettings()
                        },
                        onDismiss = { viewModel.dismissLocationSettingsDialog() }
                    )
                }

                // Custom Region Downloader Confirmation Dialog
                if (showRegionDownloadDialog && activeSelectionBounds != null) {
                    val bounds = activeSelectionBounds!!
                    val spotsCount = allSpots.count { bounds.contains(it.latitude, it.longitude) }
                    com.example.ui.dialogs.RegionDownloadDialog(
                        bounds = bounds,
                        spotsInBoundsCount = spotsCount,
                        isDownloading = isDownloadingRegion,
                        onConfirmDownload = { name -> viewModel.downloadCurrentSelectedRegion(name) },
                        onDismiss = { viewModel.closeRegionDownloadDialog() }
                    )
                }

                // Clear Cache Confirmation Dialog
                if (showClearCacheDialog) {
                    val permanentCount = (savedSpots.size + discoveredSpots.size).coerceAtMost(allSpots.size)
                    val transientCount = (allSpots.size - permanentCount - offlineDrafts.size).coerceAtLeast(0)
                    ClearCacheConfirmationDialog(
                        transientCount = transientCount,
                        savedCount = savedSpots.size,
                        discoveredCount = discoveredSpots.size,
                        onConfirm = { viewModel.confirmClearCache() },
                        onDismiss = { viewModel.dismissClearCacheDialog() }
                    )
                }
            }
        }
    }
}

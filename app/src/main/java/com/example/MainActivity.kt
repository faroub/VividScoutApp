package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.GlobalCityCatalog
import com.example.data.sync.SharedTrip
import com.example.data.sync.SharedTripManager
import com.example.ui.components.OfflineSyncStatus
import com.example.engine.ScoutVectorEngine
import com.example.ui.components.ARNavigationOverlay
import com.example.ui.components.BottomNavigationDock
import com.example.ui.components.RoutePlannerSheet
import com.example.ui.components.VividScoutTopBar
import com.example.ui.dialogs.CacheHealthDialog
import com.example.ui.dialogs.ClearCacheConfirmationDialog
import com.example.ui.dialogs.GlobalCitySelectionDialog
import com.example.ui.dialogs.LanguageSelectionDialog
import com.example.ui.dialogs.LocationPermissionRationaleDialog
import com.example.ui.dialogs.LocationPermissionSettingsDialog
import com.example.ui.dialogs.OfflineQueueDialog
import com.example.ui.dialogs.OfflineTrailBottomSheet
import com.example.ui.dialogs.OverpassDiagnosticDialog
import com.example.ui.dialogs.RegionDownloadDialog
import com.example.ui.dialogs.SharedTripDialog
import com.example.ui.dialogs.SpotDetailBottomSheet
import com.example.ui.screens.CommunityFeedScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.screens.SpotGemModal
import com.example.ui.screens.StartupGpsLocatingScreen
import com.example.ui.viewmodel.GpsAcquisitionStatus
import com.example.ui.theme.AppStrings
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalAppStrings
import com.example.ui.theme.VividScoutTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.ExploreViewModel
import com.example.ui.viewmodel.LocationViewModel
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.OfflineMapsViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.SpotPhotoViewModel
import com.example.util.configureAppCheck
import com.example.util.SafeLog

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { AppViewModelProvider.Factory }
    private val locationViewModel: LocationViewModel by viewModels { AppViewModelProvider.Factory }
    private val exploreViewModel: ExploreViewModel by viewModels { AppViewModelProvider.Factory }
    private val communityViewModel: CommunityViewModel by viewModels { AppViewModelProvider.Factory }
    private val offlineMapsViewModel: OfflineMapsViewModel by viewModels { AppViewModelProvider.Factory }
    private val profileViewModel: ProfileViewModel by viewModels { AppViewModelProvider.Factory }
    private val spotPhotoViewModel: SpotPhotoViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureAppCheck(this)
        enableEdgeToEdge()
        ScoutVectorEngine.initialize(applicationContext)

        setContent {
            val mainState by mainViewModel.uiState.collectAsState()
            val isDarkTheme = mainState.currentScreen is AppScreen.Onboarding

            VividScoutTheme(darkTheme = isDarkTheme) {
                VividScoutApp(
                    mainViewModel = mainViewModel,
                    locationViewModel = locationViewModel,
                    exploreViewModel = exploreViewModel,
                    communityViewModel = communityViewModel,
                    offlineMapsViewModel = offlineMapsViewModel,
                    profileViewModel = profileViewModel,
                    spotPhotoViewModel = spotPhotoViewModel
                )
            }
        }
    }
}

@Composable
fun VividScoutApp(
    mainViewModel: MainViewModel,
    locationViewModel: LocationViewModel,
    exploreViewModel: ExploreViewModel,
    communityViewModel: CommunityViewModel,
    offlineMapsViewModel: OfflineMapsViewModel,
    profileViewModel: ProfileViewModel,
    spotPhotoViewModel: SpotPhotoViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect 1 immutable UI-state per feature ViewModel
    val mainState by mainViewModel.uiState.collectAsState()
    val locationState by locationViewModel.uiState.collectAsState()
    val exploreState by exploreViewModel.uiState.collectAsState()
    val communityState by communityViewModel.uiState.collectAsState()
    val offlineMapsState by offlineMapsViewModel.uiState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()

    // Automatically refresh permission state when returning to the app from System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationViewModel.refreshLocationPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Automatically sync active location to ExploreViewModel for proximity sorting & auto-discovery
    LaunchedEffect(locationState.userCoordinates) {
        val coords = locationState.userCoordinates ?: return@LaunchedEffect
        val (lat, lng) = coords
        exploreViewModel.updateUserLocation(lat, lng, context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        locationViewModel.onPermissionResult(fineGranted, coarseGranted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Automatically check and prompt for location permissions on startup
    LaunchedEffect(Unit) {
        if (!locationState.hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val openAppSettings: () -> Unit = {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            // Safe recovery: System Settings activity launch failed, log telemetry
            SafeLog.w("MainActivity", "Failed to launch application details settings intent", e)
        }
    }

    val requestLocationPermission = {
        if (locationState.hasLocationPermission) {
            locationViewModel.refreshLocationPermissions()
        } else if (locationState.locationDeniedCount >= 2) {
            locationViewModel.openLocationSettingsDialog()
        } else {
            locationViewModel.openLocationRationaleDialog()
        }
    }

    var isArModeActive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var activeSharedTrip by remember { mutableStateOf<SharedTrip?>(null) }
    var activeSharedTripError by remember { mutableStateOf<String?>(null) }
    var showSharedTripDialog by remember { mutableStateOf(false) }

    val handleDeepLinkIntent: (Intent?) -> Unit = { deepIntent ->
        val dataUri = deepIntent?.dataString
        val tripId = SharedTripManager.parseTripIdFromUri(dataUri)
        if (!tripId.isNullOrBlank()) {
            coroutineScope.launch {
                val fetchResult = SharedTripManager.fetchSharedTripFromFirestore(context, tripId)
                if (fetchResult.isSuccess) {
                    activeSharedTrip = fetchResult.getOrNull()
                    activeSharedTripError = null
                } else {
                    activeSharedTrip = null
                    activeSharedTripError = fetchResult.exceptionOrNull()?.message ?: "Shared itinerary not found or expired."
                }
                showSharedTripDialog = true
            }
        }
    }

    val currentActivity = context as? Activity
    LaunchedEffect(currentActivity?.intent) {
        handleDeepLinkIntent(currentActivity?.intent)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides (if (profileState.appLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr),
        LocalAppLanguage provides profileState.appLanguage,
        LocalAppStrings provides AppStrings.forLanguage(profileState.appLanguage)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
            !locationState.isLocked -> {
                StartupGpsLocatingScreen(
                    status = locationState.gpsStatus,
                    detectedCity = locationState.detectedCityName,
                    onRequestPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onOpenSettings = openAppSettings,
                    onRetry = { locationViewModel.retryLocationAcquisition() },
                    onManualCityClick = { exploreViewModel.openGlobalCityDialog() }
                )
            }

            mainState.currentScreen is AppScreen.Onboarding -> {
                OnboardingScreen(
                    onExploreClick = {
                        mainViewModel.navigateToTab(MainTab.EXPLORE)
                        if (!locationState.hasLocationPermission && locationState.locationDeniedCount == 0) {
                            locationViewModel.openLocationRationaleDialog()
                        }
                    },
                    onSignInClick = { mainViewModel.navigateToTab(MainTab.EXPLORE) },
                    onLanguageClick = { profileViewModel.openLanguageDialog() }
                )
            }

            else -> {
                BackHandler(enabled = isArModeActive || mainState.currentTab != MainTab.EXPLORE) {
                    if (isArModeActive) {
                        isArModeActive = false
                    } else {
                        mainViewModel.navigateToTab(MainTab.EXPLORE)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        VividScoutTopBar(
                            onSearchClick = { mainViewModel.navigateToTab(MainTab.EXPLORE) },
                            onRouteClick = { offlineMapsViewModel.openRoutePlanner() },
                            onProfileClick = { mainViewModel.navigateToTab(MainTab.PROFILE) },
                            onAddSpotClick = { mainViewModel.openSubmitModal() },
                            onToggleHeaderStyle = { exploreViewModel.cycleHeaderStyle() },
                            onLanguageClick = { profileViewModel.openLanguageDialog() },
                            onGpsClick = { exploreViewModel.openGlobalCityDialog() },
                            onArClick = { isArModeActive = true },
                            hasLocationPermission = locationState.hasLocationPermission,
                            selectedGlobalCity = exploreState.selectedGlobalCity,
                            isUsingGpsLocation = locationState.isUsingGpsLocation,
                            isPrefetchActive = profileState.isPrefetchServiceRunning,
                            prefetchedCount = profileState.prefetchedGemsCount,
                            prefetchPowerMode = profileState.activePowerMode,
                            onPrefetchClick = { mainViewModel.navigateToTab(MainTab.PROFILE) }
                        )
                    },
                    bottomBar = {
                        BottomNavigationDock(
                            currentTab = mainState.currentTab,
                            onTabSelected = { tab -> mainViewModel.navigateToTab(tab) },
                            onAddSpotClick = { mainViewModel.openSubmitModal() }
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
                                        onReturnToOnboarding = { mainViewModel.navigateToScreen(AppScreen.Onboarding) },
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
                }

                // AR Navigation Overlay
                if (isArModeActive) {
                    ARNavigationOverlay(
                        spots = exploreState.filteredSpots.ifEmpty { exploreState.allSpots },
                        userCoordinates = locationState.safeCoordinates,
                        onClose = { isArModeActive = false },
                        onSelectSpot = { spot ->
                            mainViewModel.selectSpotForDetail(spot)
                            isArModeActive = false
                        },
                        onPlanRoute = { spot ->
                            offlineMapsViewModel.openRoutePlanner(destination = spot)
                            isArModeActive = false
                        }
                    )
                }

                // Spot Detail Bottom Sheet
                mainState.selectedSpotForDetail?.let { spot ->
                    SpotDetailBottomSheet(
                        spot = spot,
                        isPlayingAudio = exploreState.playingAudioId == spot.id,
                        onDismiss = { mainViewModel.dismissSpotDetail() },
                        onToggleSave = { exploreViewModel.toggleSave(spot) },
                        onToggleDiscovered = { exploreViewModel.toggleDiscovered(spot) },
                        onSaveNote = { },
                        onUpvote = { exploreViewModel.upvote(spot) },
                        onToggleAudio = { exploreViewModel.toggleAudioLore(spot.id) },
                        onPlanRoute = {
                            offlineMapsViewModel.openRoutePlanner(destination = spot)
                        },
                        onPostTip = { spotId, spotTitle, content, isStatus, category ->
                            communityViewModel.postTip(spotId, spotTitle, content, isStatus, category)
                        },
                        onReFetchImage = { spotId, callback ->
                            coroutineScope.launch {
                                val success = exploreViewModel.refreshSpotPhoto(spotId)
                                callback(success, if (success) "Real photo extracted from Wikipedia & cached in database!" else "No online Wikipedia photo available")
                            }
                        },
                        onUploadPhoto = { spotId, uri ->
                            spotPhotoViewModel.uploadSpotPhoto(context, spotId, uri)
                        },
                        onReportPhoto = { photoId ->
                            spotPhotoViewModel.reportSpotPhoto(photoId)
                        },
                        onLoadOlderPhotos = { spotId, cursor ->
                            spotPhotoViewModel.loadOlderPhotos(spotId, cursor)
                        }
                    )
                }

                // Offline Route Planner Sheet
                RoutePlannerSheet(
                    isOpen = offlineMapsState.isRoutePlannerOpen,
                    onDismiss = { offlineMapsViewModel.closeRoutePlanner() },
                    origin = offlineMapsState.routePlannerOrigin,
                    destination = offlineMapsState.routePlannerDestination,
                    allSpots = exploreState.allSpots,
                    plannedRoute = offlineMapsState.plannedRoute,
                    isCalculating = offlineMapsState.isCalculatingRoute,
                    onSelectOrigin = { offlineMapsViewModel.setRouteOrigin(it) },
                    onSelectDestination = { offlineMapsViewModel.setRouteDestination(it) },
                    onSwapEndpoints = { offlineMapsViewModel.swapRouteEndpoints() },
                    onClearRoute = { offlineMapsViewModel.clearPlannedRoute() }
                )

                // Active Offline Walking Trail Bottom Sheet
                offlineMapsState.plannedRoute?.let { trail ->
                    OfflineTrailBottomSheet(
                        trail = trail,
                        onDismiss = { offlineMapsViewModel.clearWalkingTrail() },
                        onClearTrail = { offlineMapsViewModel.clearWalkingTrail() },
                        onSpotClick = { spot -> mainViewModel.selectSpotForDetail(spot) }
                    )
                }

                // Spot a Gem Offline Submission Modal
                if (mainState.showSubmitModal) {
                    SpotGemModal(
                        onDismiss = { mainViewModel.dismissSubmitModal() },
                        onSubmit = { name, category, photoUri, notes ->
                            mainViewModel.submitSpot(
                                title = name,
                                description = notes,
                                category = category,
                                latitude = locationState.safeCoordinates.first,
                                longitude = locationState.safeCoordinates.second,
                                photoUri = photoUri
                            )
                        },
                        draftCount = offlineMapsState.offlineDrafts.size,
                        userLocation = locationState.userLocation,
                        userCoordinates = locationState.safeCoordinates,
                        isUsingGpsLocation = locationState.isUsingGpsLocation,
                        selectedGlobalCity = exploreState.selectedGlobalCity
                    )
                }

                // Offline Queue Dialog
                if (offlineMapsState.showQueueDialog) {
                    OfflineQueueDialog(
                        drafts = offlineMapsState.offlineDrafts,
                        onSyncAll = { offlineMapsViewModel.syncAllDrafts() },
                        onDismiss = { offlineMapsViewModel.dismissQueueDialog() },
                        onOpenDiagnostics = { offlineMapsViewModel.openDiagnosticDialog() }
                    )
                }

                // Overpass API Diagnostics Dialog
                if (offlineMapsState.showDiagnosticDialog) {
                    OverpassDiagnosticDialog(
                        log = offlineMapsState.diagnosticLog,
                        isRunning = offlineMapsState.isDiagnosticRunning,
                        onDismiss = { offlineMapsViewModel.dismissDiagnosticDialog() },
                        onRerunQuery = { offlineMapsViewModel.runDiagnosticQuery() }
                    )
                }

                // Cache Health & Vicinity Diagnostic Dialog
                if (offlineMapsState.showCacheHealthDialog) {
                    CacheHealthDialog(
                        health = offlineMapsState.vicinityCacheHealth,
                        onDismiss = { offlineMapsViewModel.dismissCacheHealthDialog() },
                        onPreCacheVicinity = { offlineMapsViewModel.prefetchVicinityCache() },
                        onOpenDiagnostics = {
                            offlineMapsViewModel.dismissCacheHealthDialog()
                            offlineMapsViewModel.openDiagnosticDialog()
                        }
                    )
                }

                // Touristic Place Sources Modal Dialog
                if (exploreState.showTouristicSourcesModal) {
                    com.example.ui.dialogs.TouristicSourcesModal(
                        onDismiss = { exploreViewModel.dismissTouristicSourcesModal() },
                        onSyncSource = { sourceId -> exploreViewModel.syncTouristicSource(sourceId) },
                        onSyncAllSources = { exploreViewModel.syncAllTouristicSources() },
                        isSyncing = exploreState.isSyncingTouristicSources
                    )
                }

                // Language Selection Dialog
                if (profileState.showLanguageDialog) {
                    LanguageSelectionDialog(
                        currentLanguage = profileState.appLanguage,
                        onSelectLanguage = { lang -> profileViewModel.setAppLanguage(lang) },
                        onDismiss = { profileViewModel.closeLanguageDialog() }
                    )
                }

                // Location Permission Rationale Dialog
                if (locationState.showLocationRationaleDialog) {
                    LocationPermissionRationaleDialog(
                        onAllowClick = {
                            locationViewModel.dismissLocationRationale()
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onDismiss = { locationViewModel.dismissLocationRationale() }
                    )
                }

                // Location Permission Settings Dialog
                if (locationState.showLocationSettingsDialog) {
                    LocationPermissionSettingsDialog(
                        onOpenSettingsClick = {
                            locationViewModel.dismissLocationSettings()
                            openAppSettings()
                        },
                        onDismiss = { locationViewModel.dismissLocationSettings() }
                    )
                }

                // Custom Region Downloader Confirmation Dialog
                if (offlineMapsState.showRegionDownloadDialog && offlineMapsState.activeSelectionBounds != null) {
                    val bounds = offlineMapsState.activeSelectionBounds!!
                    val spotsCount = exploreState.allSpots.count { bounds.contains(it.latitude, it.longitude) }
                    RegionDownloadDialog(
                        bounds = bounds,
                        spotsInBoundsCount = spotsCount,
                        isDownloading = offlineMapsState.isDownloadingRegion,
                        onConfirmDownload = { name -> offlineMapsViewModel.downloadCurrentSelectedRegion(name) },
                        onDismiss = { offlineMapsViewModel.dismissRegionDownloadDialog() }
                    )
                }

                // Clear Cache Confirmation Dialog
                if (profileState.showClearCacheDialog) {
                    val permanentCount = (exploreState.savedSpots.size + exploreState.discoveredSpots.size)
                        .coerceAtMost(exploreState.allSpots.size)
                    val transientCount = (exploreState.allSpots.size - permanentCount - offlineMapsState.offlineDrafts.size)
                        .coerceAtLeast(0)
                    ClearCacheConfirmationDialog(
                        transientCount = transientCount,
                        savedCount = exploreState.savedSpots.size,
                        discoveredCount = exploreState.discoveredSpots.size,
                        onConfirm = { profileViewModel.confirmClearCache() },
                        onDismiss = { profileViewModel.dismissClearCacheDialog() }
                    )
                }
            }
        }

        // Global City / Destination Selector Dialog (rendered independently of GPS startup branch)
        if (exploreState.showGlobalCityDialog) {
            GlobalCitySelectionDialog(
                selectedCity = GlobalCityCatalog.POPULAR_WORLD_CITIES.find { exploreState.selectedGlobalCity.contains(it.name, ignoreCase = true) },
                isUsingGps = locationState.isUsingGpsLocation,
                onSelectCity = { city ->
                    val cityName = "${city.name}, ${city.country}"
                    exploreViewModel.selectGlobalCity(cityName)
                    locationViewModel.setManualCoordinates(city.latitude, city.longitude, cityName)
                },
                onSelectGpsLocation = {
                    locationViewModel.enableGpsMode()
                    exploreViewModel.dismissGlobalCityDialog()
                    if (!locationState.hasLocationPermission) {
                        requestLocationPermission()
                    }
                },
                onDismiss = { exploreViewModel.dismissGlobalCityDialog() }
            )
        }

        // Shared Read-Only Itinerary Dialog
        if (showSharedTripDialog) {
            SharedTripDialog(
                trip = activeSharedTrip,
                errorMessage = activeSharedTripError,
                onDismiss = { showSharedTripDialog = false },
                onNavigateToSpot = { lat, lng ->
                    showSharedTripDialog = false
                    locationViewModel.setManualCoordinates(lat, lng)
                }
            )
        }
    }
}
}

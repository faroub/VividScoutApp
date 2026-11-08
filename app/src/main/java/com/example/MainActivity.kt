package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.data.model.GlobalCityCatalog
import com.example.engine.ScoutVectorEngine
import com.example.ui.components.ARNavigationOverlay
import com.example.ui.components.BottomNavigationDock
import com.example.ui.components.RoutePlannerSheet
import com.example.ui.components.VividScoutTopBar
import com.example.ui.deeplink.rememberSharedTripDeepLinkHandler
import com.example.ui.dialogs.AppDialogLayer
import com.example.ui.dialogs.GlobalCitySelectionDialog
import com.example.ui.dialogs.OfflineTrailBottomSheet
import com.example.ui.dialogs.SharedTripDialog
import com.example.ui.dialogs.SpotDetailBottomSheet
import com.example.ui.permissions.rememberLocationPermissionActions
import com.example.ui.screens.MainTabContent
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SpotGemModal
import com.example.ui.screens.StartupGpsLocatingScreen
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

    // Collect 1 immutable UI-state per feature ViewModel
    val mainState by mainViewModel.uiState.collectAsState()
    val locationState by locationViewModel.uiState.collectAsState()
    val exploreState by exploreViewModel.uiState.collectAsState()
    val communityState by communityViewModel.uiState.collectAsState()
    val offlineMapsState by offlineMapsViewModel.uiState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val isUsingDefaultFallbackLocation by locationViewModel.isUsingDefaultFallbackLocation.collectAsState()

    // Location permission plumbing (system launcher, startup prompt, refresh-on-resume)
    val permissionActions = rememberLocationPermissionActions(
        locationViewModel = locationViewModel,
        hasLocationPermission = locationState.hasLocationPermission,
        locationDeniedCount = locationState.locationDeniedCount
    )

    // Automatically sync active location to ExploreViewModel for proximity sorting & auto-discovery
    LaunchedEffect(locationState.userCoordinates) {
        val coords = locationState.userCoordinates ?: return@LaunchedEffect
        val (lat, lng) = coords
        exploreViewModel.updateUserLocation(lat, lng, context)
    }

    var isArModeActive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val deepLinkHandler = rememberSharedTripDeepLinkHandler(context, coroutineScope)

    val currentActivity = context as? Activity
    LaunchedEffect(currentActivity?.intent) {
        deepLinkHandler.handleIntent(currentActivity?.intent)
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
                    onRequestPermission = { permissionActions.launchSystemRequest() },
                    onOpenSettings = permissionActions.openAppSettings,
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
                        MainTabContent(
                            mainViewModel = mainViewModel,
                            exploreViewModel = exploreViewModel,
                            communityViewModel = communityViewModel,
                            offlineMapsViewModel = offlineMapsViewModel,
                            profileViewModel = profileViewModel,
                            mainState = mainState,
                            exploreState = exploreState,
                            communityState = communityState,
                            offlineMapsState = offlineMapsState,
                            profileState = profileState,
                            locationState = locationState,
                            isUsingDefaultFallbackLocation = isUsingDefaultFallbackLocation,
                            requestLocationPermission = { permissionActions.requestLocationPermission() },
                            context = context
                        )
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

                // The conditional dialog layer (queue, diagnostics, cache health,
                // language, location permission, region download, clear cache, ...)
                AppDialogLayer(
                    exploreViewModel = exploreViewModel,
                    offlineMapsViewModel = offlineMapsViewModel,
                    profileViewModel = profileViewModel,
                    locationViewModel = locationViewModel,
                    exploreState = exploreState,
                    offlineMapsState = offlineMapsState,
                    profileState = profileState,
                    locationState = locationState,
                    permissionActions = permissionActions
                )
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
                        permissionActions.requestLocationPermission()
                    }
                },
                onDismiss = { exploreViewModel.dismissGlobalCityDialog() }
            )
        }

        // Shared Read-Only Itinerary Dialog
        if (deepLinkHandler.showSharedTripDialog) {
            SharedTripDialog(
                trip = deepLinkHandler.activeSharedTrip,
                errorMessage = deepLinkHandler.activeSharedTripError,
                onDismiss = { deepLinkHandler.dismiss() },
                onNavigateToSpot = { lat, lng ->
                    deepLinkHandler.dismiss()
                    locationViewModel.setManualCoordinates(lat, lng)
                }
            )
        }
        }
    }
}

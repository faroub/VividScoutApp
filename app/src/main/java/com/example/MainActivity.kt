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
import com.example.ui.components.BottomNavigationDock
import com.example.ui.components.VividScoutTopBar
import com.example.ui.dialogs.LanguageSelectionDialog
import com.example.ui.dialogs.LocationPermissionRationaleDialog
import com.example.ui.dialogs.LocationPermissionSettingsDialog
import com.example.ui.dialogs.OfflineQueueDialog
import com.example.ui.dialogs.SpotDetailBottomSheet
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
                // Handle system back navigation to return to explore tab if on another tab
                BackHandler(enabled = currentTab != MainTab.EXPLORE) {
                    viewModel.selectTab(MainTab.EXPLORE)
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
                                requestLocationPermission()
                            },
                            hasLocationPermission = hasLocationPermission
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
                                        syncStatus = syncStatus
                                    )
                                }

                                MainTab.RADAR -> {
                                    RadarScreen(
                                        spots = allSpots,
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
                                        onPlanWalkingTrail = { viewModel.planWalkingTrailForSavedGems() },
                                        onClearWalkingTrail = { viewModel.clearWalkingTrail() }
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
                                            viewModel.planWalkingTrailForSavedGems()
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
                                        }
                                    )
                                }
                            }
                        }
                    }
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
                        onToggleAudio = { viewModel.toggleAudioLore(spot.id) }
                    )
                }

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
                        onDismiss = { viewModel.closeQueueDialog() }
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
            }
        }
    }
}

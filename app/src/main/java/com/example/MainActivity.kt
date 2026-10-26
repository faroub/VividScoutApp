package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.local.ScoutSpotEntity
import com.example.ui.components.BottomNavigationDock
import com.example.ui.components.VividScoutTopBar
import com.example.ui.dialogs.LanguageSelectionDialog
import com.example.ui.dialogs.OfflineQueueDialog
import com.example.ui.dialogs.SpotDetailBottomSheet
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.screens.SpotGemModal
import com.example.ui.theme.AppStrings
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

    var selectedSpotForDetail by remember { mutableStateOf<ScoutSpotEntity?>(null) }

    CompositionLocalProvider(
        LocalLayoutDirection provides (if (appLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr),
        LocalAppLanguage provides appLanguage,
        LocalAppStrings provides AppStrings.forLanguage(appLanguage)
    ) {
        when (currentScreen) {
            is AppScreen.Onboarding -> {
                OnboardingScreen(
                    onExploreClick = { viewModel.navigateToMain(MainTab.EXPLORE) },
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
                            }
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
                                        onCategorySelected = { cat -> viewModel.setCategory(cat) },
                                        onSearchQueryChange = { q -> viewModel.setSearchQuery(q) },
                                        onToggleSave = { spot -> viewModel.toggleSave(spot) },
                                        onUpvote = { spot -> viewModel.upvote(spot) },
                                        onToggleAudioLore = { id -> viewModel.toggleAudioLore(id) },
                                        onSpotClick = { spot -> selectedSpotForDetail = spot },
                                        onAddSpotClick = { viewModel.openSubmitModal() },
                                        onRadarClick = { viewModel.selectTab(MainTab.RADAR) },
                                        onDismissOfflineBanner = { viewModel.dismissOfflineBanner() },
                                        onOpenQueueDialog = { viewModel.openQueueDialog() }
                                    )
                                }

                                MainTab.RADAR -> {
                                    RadarScreen(
                                        spots = allSpots,
                                        onSpotClick = { spot -> selectedSpotForDetail = spot }
                                    )
                                }

                                MainTab.SAVED -> {
                                    SavedScreen(
                                        savedSpots = savedSpots,
                                        discoveredSpots = discoveredSpots,
                                        onToggleSave = { spot -> viewModel.toggleSave(spot) },
                                        onToggleDiscovered = { spot -> viewModel.toggleDiscovered(spot) },
                                        onSpotClick = { spot -> selectedSpotForDetail = spot }
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
                                        onOpenLanguageDialog = { viewModel.openLanguageDialog() }
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

                // Spot a Gem Offline Submission Modal
                if (showSubmitModal) {
                    SpotGemModal(
                        onDismiss = { viewModel.closeSubmitModal() },
                        onSubmit = { name, category, photoUri, notes ->
                            viewModel.submitGem(name, category, photoUri, notes) {
                                // On complete
                            }
                        },
                        draftCount = offlineDrafts.size
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
            }
        }
    }
}

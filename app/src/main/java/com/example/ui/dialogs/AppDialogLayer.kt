package com.example.ui.dialogs

import androidx.compose.runtime.Composable
import com.example.BuildConfig
import com.example.ui.permissions.LocationPermissionActions
import com.example.ui.viewmodel.ExploreUiState
import com.example.ui.viewmodel.ExploreViewModel
import com.example.ui.viewmodel.LocationUiState
import com.example.ui.viewmodel.LocationViewModel
import com.example.ui.viewmodel.OfflineMapsUiState
import com.example.ui.viewmodel.OfflineMapsViewModel
import com.example.ui.viewmodel.ProfileUiState
import com.example.ui.viewmodel.ProfileViewModel

/**
 * Counts behind the clear-cache confirmation. Pure so the dialog's accounting logic
 * is unit-testable: transient spots = everything that is neither saved, discovered,
 * nor an offline draft.
 */
data class ClearCacheCounts(
    val transientCount: Int,
    val savedCount: Int,
    val discoveredCount: Int
)

fun resolveClearCacheCounts(
    allSpotsCount: Int,
    savedCount: Int,
    discoveredCount: Int,
    offlineDraftsCount: Int
): ClearCacheCounts {
    val permanentCount = (savedCount + discoveredCount).coerceAtMost(allSpotsCount)
    val transientCount = (allSpotsCount - permanentCount - offlineDraftsCount).coerceAtLeast(0)
    return ClearCacheCounts(
        transientCount = transientCount,
        savedCount = savedCount,
        discoveredCount = discoveredCount
    )
}

/**
 * The conditional dialog layer of the main app: every dialog driven by the feature
 * ViewModels' UI state. Extracted from [com.example.VividScoutApp] so the activity
 * composes its screens and overlays without a flat list of dialog conditionals.
 */
@Composable
fun AppDialogLayer(
    exploreViewModel: ExploreViewModel,
    offlineMapsViewModel: OfflineMapsViewModel,
    profileViewModel: ProfileViewModel,
    locationViewModel: LocationViewModel,
    exploreState: ExploreUiState,
    offlineMapsState: OfflineMapsUiState,
    profileState: ProfileUiState,
    locationState: LocationUiState,
    permissionActions: LocationPermissionActions
) {
    // Offline Queue Dialog
    if (offlineMapsState.showQueueDialog) {
        OfflineQueueDialog(
            drafts = offlineMapsState.offlineDrafts,
            onSyncAll = { offlineMapsViewModel.syncAllDrafts() },
            onDismiss = { offlineMapsViewModel.dismissQueueDialog() },
            // Developer diagnostics (Overpass JSON tooling) are debug-only, so the
            // release build never wires the action that opens it.
            onOpenDiagnostics = if (BuildConfig.DEBUG) {
                { offlineMapsViewModel.openDiagnosticDialog() }
            } else {
                { }
            }
        )
    }

    // Overpass API Diagnostics Dialog (debug builds only — the action that opens it is
    // gated above and in OfflineQueueDialog; the state setter cannot be reached in
    // release, and this composition guard is defense in depth).
    if (BuildConfig.DEBUG && offlineMapsState.showDiagnosticDialog) {
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
            // Same debug-only diagnostics surface: no dead "open diagnostics" button
            // in release (CacheHealthDialog hides the action when the callback is null).
            onOpenDiagnostics = if (BuildConfig.DEBUG) {
                {
                    offlineMapsViewModel.dismissCacheHealthDialog()
                    offlineMapsViewModel.openDiagnosticDialog()
                }
            } else {
                null
            }
        )
    }

    // Touristic Place Sources Modal Dialog
    if (exploreState.showTouristicSourcesModal) {
        TouristicSourcesModal(
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
                permissionActions.launchSystemRequest()
            },
            onDismiss = { locationViewModel.dismissLocationRationale() }
        )
    }

    // Location Permission Settings Dialog
    if (locationState.showLocationSettingsDialog) {
        LocationPermissionSettingsDialog(
            onOpenSettingsClick = {
                locationViewModel.dismissLocationSettings()
                permissionActions.openAppSettings()
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
        val counts = resolveClearCacheCounts(
            allSpotsCount = exploreState.allSpots.size,
            savedCount = exploreState.savedSpots.size,
            discoveredCount = exploreState.discoveredSpots.size,
            offlineDraftsCount = offlineMapsState.offlineDrafts.size
        )
        ClearCacheConfirmationDialog(
            transientCount = counts.transientCount,
            savedCount = counts.savedCount,
            discoveredCount = counts.discoveredCount,
            onConfirm = { profileViewModel.confirmClearCache() },
            onDismiss = { profileViewModel.dismissClearCacheDialog() }
        )
    }
}

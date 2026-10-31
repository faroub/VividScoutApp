# Known Issues & Repository Status Report

## System Architecture & Refactoring Status

1. **Composable File Sizes**:
   - `ExploreScreen.kt`: Refactored from 1,763 lines to **482 lines** (sub-composables extracted to `ExploreSearchBar.kt`, `ExploreFilterRibbon.kt`, `ExploreSpotCard.kt`, `ExploreRadarCard.kt`, `ExploreHeader.kt`, `ExploreState.kt`).
   - `RadarScreen.kt`: Refactored from 1,032 lines to **362 lines** (sub-composables extracted to `RadarBanners.kt`, `RadarViewModeSelector.kt`, `RadarOsmScraperCard.kt`, `RadarDetectedSpotItem.kt`).
   - `ProfileScreen.kt`: Refactored from 958 lines to **180 lines** (sub-composables extracted to `ProfileGpsTelemetryCard.kt`, `ProfileSettingsSection.kt`).
   - `MapCanvasView.kt`: Refactored from 1,193 lines to **810 lines** (sub-composables extracted to `MapRegionDownloaderBar.kt`, `MapRouteHud.kt`, `MapCanvasControls.kt`).
   - *Note on secondary overlay/sheet components*: `RoutePlannerSheet.kt` (816 lines), `SpotGemModal.kt` (788 lines), `SpotDetailBottomSheet.kt` (737 lines), and `ARNavigationOverlay.kt` (705 lines) remain functional as standalone modal sheets/overlays.

2. **PreScoutEngine Coverage**:
   - `PreScoutEngine` is fully tested by unit tests in `PreScoutEngineTest.kt` covering initial anchor setup, same-city small movement filtering (<10km), city boundary crossing triggers, >10km distance boundary triggers, preference weight calculations, and pre-scout execution flow.

3. **Community Spot Submissions & Cloud Sync**:
   - Community tips and spot submissions are stored locally in Room DB as offline draft entities (`isOfflineDraft = true`).
   - Live cloud synchronization backend (e.g., Firestore or remote API sync endpoint) is pending integration; drafts currently queue locally in the Offline Submission Queue.

4. **Version Control & Repository Hygiene**:
   - `.idea/` directory untracked from Git version control and added to `.gitignore`.
   - `.aistudio/` artifacts untracked from Git version control and added to `.gitignore`.
   - `metadata.json` and `public/assets/aistudio/` untracked from Git version control and added to `.gitignore`.

5. **Automated Verification**:
   - `clean assembleDebug assembleRelease`: Build succeeds for both Debug and Release.
   - `testDebugUnitTest`: 112 / 112 unit tests passing, 0 skipped, 0 failed.

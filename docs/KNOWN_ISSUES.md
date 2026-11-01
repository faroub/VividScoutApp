# Known Issues & Repository Status Report

## System Architecture & Refactoring Status

1. **Composable File Sizes**:
   - `ExploreScreen.kt`: Refactored to **482 lines** (sub-composables modularized in `ExploreSearchBar.kt`, `ExploreFilterRibbon.kt`, `ExploreSpotCard.kt`, `ExploreRadarCard.kt`, `ExploreHeader.kt`, `ExploreState.kt`).
   - `RadarScreen.kt`: Refactored to **362 lines** (sub-composables modularized in `RadarBanners.kt`, `RadarViewModeSelector.kt`, `RadarOsmScraperCard.kt`, `RadarDetectedSpotItem.kt`).
   - `ProfileScreen.kt`: Refactored to **180 lines** (sub-composables modularized in `ProfileGpsTelemetryCard.kt`, `ProfileSettingsSection.kt`).
   - `MapCanvasView.kt`: Refactored to **810 lines** (sub-composables modularized in `MapRegionDownloaderBar.kt`, `MapRouteHud.kt`, `MapCanvasControls.kt`).
   - *Note on secondary overlay/sheet components*: `RoutePlannerSheet.kt`, `SpotGemModal.kt`, `SpotDetailBottomSheet.kt`, and `ARNavigationOverlay.kt` remain functional as standalone modal sheets/overlays.

2. **Route-Corridor Validation (Resolved)**:
   - Added spherical cross-track distance calculations in `MovementVectorUtils.kt` and `OfflineRouteEngine.kt`.
   - `GeminiQueryService` strictly filters AI-suggested corridor spots using `MovementVectorUtils.isPointInCorridor()`, rejecting any spot whose perpendicular cross-track distance from the polyline route corridor exceeds the corridor threshold.

3. **Discovery Radius Validation (Resolved)**:
   - Enforced strict proportional tolerance (`radiusMeters * 1.15f`) in `GeminiSpotService.kt`.
   - Removed previous `maxOf(..., 3000f)` minimum floor that inflated tight queries (e.g. 500m / 1km) to 3km.

4. **Ratings Truthfulness (Resolved)**:
   - Removed artificial rating generation from Gemini prompt schemas in `GeminiSpotService` and `GeminiQueryService`.
   - `rating = null` and `reviewCount = null` are assigned to AI and curated spots unless verified by authentic user reviews.
   - Removed synthetic rating filter in `PreScoutEngine`, ranking instead by proximity and authentic relevance.

5. **City-Independent Proximity Discovery (Resolved)**:
   - Replaced hardcoded Lisbon references in AI queries and fallback generation with dynamic city resolution using `GlobalCityCatalog.findNearestCityName(lat, lng)`.
   - Active fallback coordinates dynamically track the user-selected city in `DefaultLocationConfig`.

6. **Fallback Location Status Visibility (Resolved)**:
   - Added prominent fallback indicators across the app UI:
     - `VividScoutTopBar`: Amber `"Fallback: <City>"` badge when GPS is inactive.
     - `ExploreScreen`: Warning banner with quick action to change city or enable GPS.
     - `RadarScreen`: Warning banner showing fallback status.
     - `ProfileGpsTelemetryCard`: Simulated Fallback GPS badge with annotated coordinates.
     - `SpotGemModal`: Amber tag banner alerting user that submission coordinates are at fallback location.

7. **Community Cloud Synchronization (Resolved)**:
   - Implemented real cloud synchronization with Firebase Firestore via `FirestoreSyncManager.kt`.
   - Bidirectional sync for `community_submissions` and `community_tips`.
   - Zero-trust Firestore security rules deployed and active (`firestore.rules`).

8. **Automated Verification**:
   - `compile_applet`: Compilation succeeds with zero errors.
   - `gradle :app:testDebugUnitTest`: 125 unit and integration tests passing, 0 skipped, 0 failed.
   - Integration tests covering Gemini failures, coordinate validation, corridor filtering, and App Check configuration.

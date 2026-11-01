# Known Issues & Repository Status Report

## System Architecture & Refactoring Status

1. **Composable File Sizes**:
   - `ExploreScreen.kt`: Modularized with sub-composables in `ExploreSearchBar.kt`, `ExploreFilterRibbon.kt`, `ExploreSpotCard.kt`, `ExploreRadarCard.kt`, `ExploreHeader.kt`, `ExploreState.kt`.
   - `RadarScreen.kt`: Modularized with sub-composables in `RadarBanners.kt`, `RadarViewModeSelector.kt`, `RadarOsmScraperCard.kt`, `RadarDetectedSpotItem.kt`.
   - `ProfileScreen.kt`: Modularized with sub-composables in `ProfileGpsTelemetryCard.kt`, `ProfileSettingsSection.kt`.
   - `MapCanvasView.kt`: Modularized with sub-composables in `MapRegionDownloaderBar.kt`, `MapRouteHud.kt`, `MapCanvasControls.kt`.
   - *Note on secondary overlay/sheet components*: `RoutePlannerSheet.kt`, `SpotGemModal.kt`, `SpotDetailBottomSheet.kt`, and `ARNavigationOverlay.kt` remain functional as standalone modal sheets/overlays.

2. **Route-Corridor Validation**:
   - Spherical cross-track distance calculations in `MovementVectorUtils.kt` and `OfflineRouteEngine.kt`.
   - `GeminiQueryService` filters AI-suggested corridor spots using `MovementVectorUtils.isPointInCorridor()`, rejecting spots whose perpendicular cross-track distance from the route corridor exceeds the corridor threshold.

3. **Discovery Radius Validation**:
   - Proportional tolerance (`radiusMeters * 1.15f`) enforced in `GeminiSpotService.kt`.

4. **Ratings Truthfulness**:
   - Artificial rating generation removed from Gemini prompt schemas in `GeminiSpotService` and `GeminiQueryService`.
   - `rating = null` and `reviewCount = null` assigned to AI and curated spots unless verified by authentic user reviews.

5. **City-Independent Proximity Discovery**:
   - AI queries and fallback generation resolve dynamic cities using `GlobalCityCatalog.findNearestCityName(lat, lng)`.
   - Active fallback coordinates dynamically track the user-selected city in `DefaultLocationConfig`.

6. **Fallback Location Status Visibility**:
   - Prominent fallback indicators across UI:
     - `VividScoutTopBar`: Amber `"Fallback: <City>"` badge when GPS is inactive.
     - `ExploreScreen`: Warning banner with quick action to change city or enable GPS.
     - `RadarScreen`: Warning banner showing fallback status.
     - `ProfileGpsTelemetryCard`: Simulated Fallback GPS badge with annotated coordinates.
     - `SpotGemModal`: Amber tag banner alerting user that submission coordinates are at fallback location.

7. **Community Cloud Synchronization**:
   - Bidirectional cloud synchronization with Firebase Firestore configured via `FirestoreSyncManager.kt` and `CommunityTipRepository.kt`.
   - Firestore security rules configured in `firestore.rules` (reported).

8. **Automated Verification**:
   - `./gradlew :app:testDebugUnitTest`: 128 unit and integration tests passing, 0 skipped, 0 failed.

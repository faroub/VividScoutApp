# Implementation Plan - Offline Database Sync Status Visual Indicator

Implement a floating visual status indicator in the UI that displays real-time offline database sync metrics: local Room DB gems count, pending offline drafts count, and last Overpass OSM sync results.

## User Requirements & Clarifications
- **Placement:** Floating sync status badge displayed at the top of the map view (`RadarScreen`/`ScoutMapView`) and the `ExploreScreen` feed.
- **Metrics Displayed:** Total local gems stored in Room DB, number of queued offline drafts pending upload, and the count of newly synced nodes from the latest Overpass API query.

---

## Technical Architecture & Design

### 1. `OfflineSyncStatus` State Representation (`ScoutViewModel.kt` & `ScoutMapViewModel.kt`)
- Create data model:
  ```kotlin
  data class OfflineSyncStatus(
      val totalLocalGems: Int = 0,
      val pendingDraftsCount: Int = 0,
      val lastOsmSyncedCount: Int = 0,
      val isSyncing: Boolean = false
  )
  ```
- Expose `syncStatus: StateFlow<OfflineSyncStatus>` in `ScoutViewModel` by combining `allSpots`, `offlineDrafts`, `isScrapingOsm`, and last Overpass sync count.

### 2. Visual Component (`OfflineSyncStatusBadge.kt`)
- Create a Jetpack Compose `OfflineSyncStatusBadge` in `com.example.ui.components`:
  - **Status Pill:** Dark surface (`#F2080B1E`) with vibrant border (`BrandTeal`/`BrandCoral`).
  - **Live Indicator Dot:** Pulsing green LED when Room DB is synced, amber when drafts are queued, and spinning loader when Overpass scraping is active.
  - **Status Text:** e.g., `💾 24 Local Gems • 📝 2 Queued • 🌐 +12 Live OSM`
  - **Quick Action:** Tapping the badge opens the offline queue or triggers an instant Overpass refresh scan around current GPS coordinates.

### 3. Screen Integration
- Integrate `OfflineSyncStatusBadge` into `ScoutMapView` (floating top banner) and `ExploreScreen` (top header section).
- Connect tap callbacks to trigger `ScoutViewModel.syncAllDrafts()` or `ScoutViewModel.scrapeOsmGems()`.

---

## Verification Plan
1. **Compilation Check:** Run `compile_applet` to confirm clean compilation across all Compose UI files.
2. **Unit & Robolectric Tests:** Run `gradle :app:testDebugUnitTest` to verify state flow emission and repository integration.

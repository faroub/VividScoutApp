# Implementation Plan - Offline Vector Map Packs in Settings

Allow users to download vector map packs for specific cities or custom GPS regions into internal storage (`context.filesDir`), displaying progress bars, storage size indicators, and pack management options in settings.

## User Requirements & Clarifications
- **Available Regions:** Preset popular cities (Lisbon & Cascais, Dresden & Saxony, Berlin, Paris) plus a custom "Current GPS City" download option.
- **UI Components:** Offline map packs manager in Settings showing download progress bar (`0-100%`), formatted file size indicator (e.g., `4.2 MB`), and a delete pack action.
- **Storage & Engine:** Offline map packs saved in internal storage (`context.filesDir/offline_map_packs/`), supplying vector geometry and offline gems to `ScoutVectorEngine` and `ScoutGemRepository` without requiring internet connectivity.

---

## Technical Architecture & Design

### 1. Data Model & Storage (`com.example.data.offline`)
- **`OfflineMapPack` Model:**
  - `id: String` (e.g. `dresden_saxony`, `current_gps`)
  - `cityName: String`
  - `country: String`
  - `lat: Double`, `lng: Double`
  - `radiusMeters: Double`
  - `fileSizeBytes: Long`
  - `isDownloaded: Boolean`
  - `downloadProgress: Int` (0..100)
  - `downloadedAt: Long`
- **`OfflineMapManager`:**
  - Singleton/Repository managing local pack files in `context.filesDir/offline_map_packs/`.
  - Downloads vector map geometry and OSM gems via Overpass QL with chunked progress callbacks.
  - Exposes `packs: StateFlow<List<OfflineMapPack>>` and total storage usage helper.
  - Delete function removes pack files from `context.filesDir` and resets pack state.

### 2. Settings UI (`com.example.ui.settings`)
- **`OfflineMapPacksSection` Composable:**
  - City selection list with preset cards and "Current GPS City".
  - Status indicators: Downloaded badge, file size (e.g., `3.5 MB`), download progress bar (`LinearProgressIndicator`), and download/delete action buttons.
  - Integrates smoothly into the main settings menu/dialog.

### 3. Vector Engine & Repository Integration
- **`ScoutGemRepository` & `ScoutVectorEngine` Integration:**
  - When offline or panning over an offline region, automatically load cached vector features and gems from saved JSON files in `context.filesDir/offline_map_packs/`.

---

## Verification Plan

1. **Compilation Verification:** Run `compile_applet` to ensure zero compilation errors.
2. **Unit & Integration Testing:** Run `gradle :app:testDebugUnitTest` to test pack file creation, size calculation, progress callbacks, and deletion.

# Implementation Plan - Interactive Region Downloader for Offline Navigation

Implement a **Region Downloader** feature that allows users to define custom geographic areas via an interactive bounding box on the vector map canvas and save vector road segments, elevation points, and cached spots to their device, dynamically augmenting the offline A* pathfinding graph in remote areas.

## User Decisions & Architecture
- **Area Selection**: Interactive bounding box rectangle rendered directly on the vector map canvas with adjustable bounds, real-time size estimation, and dimension indicators.
- **Downloaded Data**: Vector road and pedestrian walkway segments (nodes, edges, street names), topographic elevation grid points, and cached gems within the selected bounding box.
- **A\* Graph Integration**: Downloaded road segments are dynamically integrated into `VectorWalkGraph`, allowing offline shortest-path A* routing anywhere within the downloaded regions.

---

## Proposed Changes

### 1. Data & Storage Layer
#### `app/src/main/java/com/example/data/offline/DownloadedRegion.kt` (New File)
- Data structures:
  - `GeoBoundingBox(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double)`: Geographic rectangle.
  - `DownloadedRoadSegment(id: String, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, streetName: String, isPedestrian: Boolean, isStairs: Boolean)`
  - `DownloadedRegionPack(id: String, name: String, bounds: GeoBoundingBox, roadSegments: List<DownloadedRoadSegment>, elevationAnchors: List<ElevationPoint>, spots: List<ScoutSpotEntity>, totalSizeBytes: Long, timestamp: Long)`
- Region persistence:
  - Save/load `DownloadedRegionPack` files to `filesDir/offline_regions/`.
  - Expose `getDownloadedRegions(): List<DownloadedRegionPack>` and `deleteRegion(id)`.

#### `app/src/main/java/com/example/engine/VectorWalkGraph.kt`
- Add dynamic expansion API:
  - `loadCustomRegionSegments(segments: List<DownloadedRoadSegment>)`: Inserts downloaded vertices and edges into the active routing network with collision deduplication.
  - Automatically loads all saved region packs at startup and upon new download completion.

#### `app/src/main/java/com/example/engine/LisbonElevationModel.kt`
- Allow custom elevation anchors from downloaded regions to supplement the interpolation model.

---

### 2. ViewModel & State Management
#### `app/src/main/java/com/example/ui/viewmodel/ScoutViewModel.kt`
- State flows:
  - `isRegionDownloadMode: StateFlow<Boolean>`: Controls whether the interactive bounding box is active on the map.
  - `activeSelectionBounds: StateFlow<GeoBoundingBox?>`: Current selected rectangle coordinates.
  - `downloadedRegions: StateFlow<List<DownloadedRegionPack>>`: All active custom downloaded regions.
  - `isDownloadingRegion: StateFlow<Boolean>`: Progress indicator state.
- Methods:
  - `enterRegionDownloadMode()`, `exitRegionDownloadMode()`
  - `updateSelectionBounds(bounds: GeoBoundingBox)`
  - `downloadCurrentRegion(regionName: String)`
  - `deleteDownloadedRegion(regionId: String)`

---

### 3. UI Layer
#### `app/src/main/java/com/example/ui/components/MapCanvasView.kt`
- **Interactive Bounding Box Overlay**:
  - When `isRegionDownloadMode == true`, render an interactive translucent bounding box rectangle with corner sizing anchors.
  - Display live telemetry badge on the box: estimated dimensions (e.g. `1.8 km × 1.4 km`), estimated storage size (`~2.8 MB`), and road segment count.
  - Floating action pill: "Download Region" and "Cancel".

#### `app/src/main/java/com/example/ui/dialogs/RegionDownloadDialog.kt` (New File)
- Modal dialog confirming download:
  - Custom region label input (defaults to detected neighborhood or "Custom Region").
  - Breakdown of included data: road segments, elevation points, cached spots.
  - "Download & Save Offline" primary CTA with progress bar.

#### `app/src/main/java/com/example/ui/components/OfflineMapPacksSection.kt`
- Add a "Custom Downloaded Regions" tab/section listing user-saved regions with their area size, road count, delete action, and "Focus on Map" button.

---

## Verification Plan

### Automated Tests
1. **`RegionDownloaderTest.kt`** (Robolectric / Unit Test):
   - Test region pack serialization and persistence to disk.
   - Verify road segments within a bounding box are generated and loaded into `VectorWalkGraph`.
   - Test offline A* search routing successfully between two coordinates in a downloaded remote region that previously had no base graph coverage.
2. Execute tests: `gradle :app:testDebugUnitTest`.

### Compilation & Manual Verification
1. Run `compile_applet`.
2. Activate Region Download mode on map canvas.
3. Adjust bounding box rectangle, confirm size estimation, and save region.
4. Verify downloaded region appears in list and A* pathfinding calculates walking routes across the downloaded segments.

# Architecture Refactoring: AppContainer DI & Feature ViewModels

Decompose the 1,036-line `ScoutViewModel` god-object into focused feature ViewModels with manual dependency injection via `AppContainer`, and streamline `MainActivity` to collect unified, immutable screen UI states.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions were confirmed during clarification:
> - **ViewModel Instantiation**: Dedicated `ViewModelProvider.Factory` reading from `AppContainer` via custom `VividScoutApplication`.
> - **Screen State Consumption**: Each screen composable receives a single unified immutable `UiState` data class and action lambdas, eliminating the ~50 separate flow collections in `MainActivity`.

- **Confirmed Decision 1**: Manual DI via `AppContainer` and `DefaultAppContainer` (no Hilt, keeping build times fast and lean).
- **Confirmed Decision 2**: Six feature ViewModels, each under 400 lines: `LocationViewModel`, `ExploreViewModel`, `RadarViewModel`, `CommunityViewModel`, `OfflineMapsViewModel`, and `ProfileViewModel` (plus a lightweight navigation/app shell controller).
- **Confirmed Decision 3**: Location coordinates and tracking are managed by `LocationViewModel` and shared via Flow/StateFlow without cross-repository coupling.
- **Confirmed Decision 4**: All inline fully-qualified imports (`com.example.*`) eliminated in favor of standard Kotlin import statements.

---

## 1. Overview & Core Concept

- **What It Does**: Transitions the app from an anti-pattern monolithic ViewModel to a clean, scalable Clean Architecture/MVVM pattern with single-responsibility feature ViewModels, manual constructor injection, and unidirectional data flow (UDF).
- **Target Audience / Developer Value**: Drastically faster compile and test cycles, eliminated race conditions between unrelated features, improved testability, and adherence to Android architectural best practices.

---

## 2. Technical Architecture & Component Mapping

```
┌────────────────────────────────────────────────────────────────────────┐
│                        VividScoutApplication                           │
│                                   │                                    │
│                         DefaultAppContainer                            │
│    ┌──────────────────┬───────────┴───────────┬──────────────────┐     │
│    ▼                  ▼                       ▼                  ▼     │
│ AppDatabase     LocationTracker      OfflineMapManager   CommunityRepo │
│ (DAOs, Room)    (FusedLocation)      OfflineStorage      ScoutRepo     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         AppViewModelProvider.Factory
                                    │
         ┌──────────────┬───────────┼───────────┬──────────────┐
         ▼              ▼           ▼           ▼              ▼
   LocationVM      ExploreVM     RadarVM   CommunityVM   OfflineMapsVM & ProfileVM
         │              │           │           │              │
    LocationState  ExploreState RadarState CommunityState OfflineState & ProfileState
         └──────────────┴───────────┼───────────┴──────────────┘
                                    │ (Collected once per screen)
                                    ▼
                               MainActivity
                   (VividScoutApp & Screen Composables)
```

### Module & Class Structure

1. **`com.example.di.AppContainer` & `DefaultAppContainer`**:
   - Owns `AppDatabase` singleton.
   - Lazy instantiations of `ScoutRepository`, `ScoutGemRepository`, `LocationTracker`, `OfflineMapManager`, `OfflineRegionStorage`, `CommunityTipRepository`, `PreScoutEngine`, and `CompassSensorManager`.
2. **`com.example.VividScoutApplication`**:
   - Custom `Application` subclass holding `lateinit var container: AppContainer`.
   - Registered in `AndroidManifest.xml` via `android:name=".VividScoutApplication"`.
3. **Feature ViewModels & UiState classes (`com.example.ui.viewmodel.*`)**:
   - **`LocationViewModel`** (`LocationUiState`): GPS coordinates, telemetry, accuracy status, permission requests/rationale.
   - **`ExploreViewModel`** (`ExploreUiState`): Spots catalog, text search, category filters, recent searches, global city selector, pre-scout banner, OSM scraper status.
   - **`RadarViewModel`** (`RadarUiState`): Proximity radius filtering, orientation/azimuth, nearby pings, sensor status.
   - **`CommunityViewModel`** (`CommunityUiState`): Community tips, live spot alerts, peer sync state, tip posting modal state.
   - **`OfflineMapsViewModel`** (`OfflineMapsUiState`): Region downloads, map packs, bounding box selection, offline queue drafts, cache health diagnostics.
   - **`ProfileViewModel`** (`ProfileUiState`): Achievements, badges, points, language selection, prefetch telemetry, cache clear dialogs.
   - **`AppViewModelProvider`**: Companion factory creating ViewModels via `AppContainer`.
4. **`MainActivity.kt`**:
   - Replaces ~50 `collectAsState()` lines with one `collectAsState()` per screen UI state.
   - Passes clean immutable UI state objects and action lambdas to screens.
   - Replaces all inline FQCNs with imports.

---

## 3. Key Product & Architectural Decisions

### Decision 1: Manual Constructor Injection via AppContainer
- **Chosen Approach**: `AppContainer` interface with `DefaultAppContainer` implementation.
- **Why**: Standard Android recommendation for medium-sized apps without Hilt overhead; no reflection or annotation processing delay during incremental compilation.

### Decision 2: Location Sharing Pattern
- **Chosen Approach**: `LocationViewModel` exposes `userCoordinates: StateFlow<Pair<Double, Double>>`. Other ViewModels that need proximity calculations (e.g. `ExploreViewModel`, `RadarViewModel`) accept coordinates as parameters to their query/filtering functions or observe the flow.
- **Why**: Keeps ViewModels loosely coupled and independently testable without circular dependencies.

### Decision 3: Backward Compatibility & Preservation of ScoutViewModel
- **Chosen Approach**: Retire `ScoutViewModel` in favor of feature ViewModels. Any existing references (e.g. in tests) are replaced or cleanly migrated.

---

## 4. Acceptance Criteria & Verification Plan

1. **Acceptance Criteria**:
   - No ViewModel file exceeds 400 lines (verified via `wc -l`).
   - No ViewModel constructs its own repositories or database instances.
   - Every ViewModel receives dependencies via constructor injection.
   - `MainActivity.kt` uses single `UiState` collections per screen.
   - All inline FQCN references removed.
2. **Testing**:
   - Unit tests: Add smoke tests for each new ViewModel:
     - `LocationViewModelTest`
     - `ExploreViewModelTest`
     - `RadarViewModelTest`
     - `CommunityViewModelTest`
     - `OfflineMapsViewModelTest`
     - `ProfileViewModelTest`
   - Run full unit test suite: `gradle :app:testDebugUnitTest`.
   - Build verification: `compile_applet`.

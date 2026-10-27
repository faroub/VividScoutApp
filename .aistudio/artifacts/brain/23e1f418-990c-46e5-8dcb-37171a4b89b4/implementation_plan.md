# ScoutMapViewModel & Lifecycle-Aware Map Canvas Host

Architecture for `ScoutMapViewModel` managing a unified `StateFlow<ScoutMapUiState>` and a dedicated lifecycle-aware Composable (`ScoutMapLifecycleHost`) for `ScoutVectorEngine` canvas rendering in `MainActivity`.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Decision**: State inside `ScoutMapViewModel` will be managed using a single, immutable **StateFlow holding a unified `ScoutMapUiState`** data class (pan offset, zoom level, selected spot, and engine status). Map rendering lifecycle (`ON_START`, `ON_STOP`, `ON_DESTROY`) will be bound to the `ScoutVectorEngine` via `DisposableEffect` in `ScoutMapLifecycleHost`.

---

## 1. Overview & Core Concept

- **Goal**: Cleanly decouple vector map state and canvas lifecycle from the UI layer by introducing:
  1. **`ScoutMapViewModel.kt`**: Manages map viewport state (`ScoutMapUiState`), handles gesture inputs (pan, zoom, reset to GPS), and coordinates selected gem spots.
  2. **`ScoutMapLifecycleHost.kt`**: A dedicated Composable host observing `LocalLifecycleOwner.current.lifecycle` to automatically pause/resume telemetry and lifecycle observers on the `ScoutVectorEngine`.
  3. **`MainActivity.kt`**: Obtains `ScoutMapViewModel` and renders `ScoutMapLifecycleHost`.

---

## 2. User Experience & Visual Design

- **Lifecycle & State Flow**:
  - `ScoutMapViewModel` exposes `uiState: StateFlow<ScoutMapUiState>`.
  - On app start (`ON_START`), `ScoutMapLifecycleHost` binds active telemetry and projection pre-warming.
  - On app pause/stop (`ON_STOP`), vector map telemetry observers are safely paused to conserve battery.
  - Users interact with pan/zoom gestures and tap gem pins on the canvas smoothly without state loss across orientation or tab switches.

---

## 3. Key Product Decisions & Trade-Offs

- **Chosen Approach**:
  - `ScoutMapUiState`: Data class holding `panOffset: Offset`, `zoomScale: Float`, `selectedSpot: ScoutSpotEntity?`, `isEngineReady: Boolean`, and `lastGpsLat/Lng`.
  - `ScoutMapLifecycleHost`: Binds `LifecycleEventObserver` to `ScoutVectorEngine` lifecycle hooks.
- **Why**: Follows modern Android Architecture guidelines (UDF - Unidirectional Data Flow), eliminates memory leaks, and ensures full lifecycle safety during background transitions.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                       MainActivity                          │
│   onCreate() ──> viewModel: ScoutMapViewModel by viewModels│
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    ScoutMapViewModel                        │
│   • uiState: StateFlow<ScoutMapUiState>                    │
│   • updatePan(), updateZoom(), selectSpot(), resetCenter() │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 ScoutMapLifecycleHost                       │
│   • DisposableEffect(lifecycleOwner)                        │
│   • Observes ON_START, ON_STOP, ON_DESTROY                 │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     MapCanvasView                           │
│   • Renders Lisbon Vector Grid, River, Beacon & Pins        │
└─────────────────────────────────────────────────────────────┘
```

### Implementation Checklist
1. **`ScoutMapViewModel.kt`**: Create ViewModel with `ScoutMapUiState` StateFlow and action methods.
2. **`ScoutMapLifecycleHost.kt`**: Create Composable host with `DisposableEffect` observing `Lifecycle.Event`.
3. **`MainActivity.kt` & `ScoutMapView.kt`**: Bind `ScoutMapViewModel` and `ScoutMapLifecycleHost`.

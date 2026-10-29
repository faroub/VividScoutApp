# Implementation Plan: Proximity Prefetch Background Service

## 1. Goal Analysis
Implement a **Proximity Prefetch** background service in *Scout Lisbon* that calculates the user's 5km predicted movement vector based on recent GPS heading/speed, fetches POIs along that corridor using `GeminiQueryService` / `ScoutGemRepository`, stores them in the Room database, and displays a prefetch telemetry indicator pill in the UI.

---

## 2. Architecture & Design

### Movement Vector Prediction (`MovementVectorUtils.kt`)
- **Rolling Sample Window**: Tracks recent GPS locations `(lat, lng, speed, timestamp)`.
- **Bearing & Target Extrapolation**:
  Calculates average bearing $\theta$ and predicts target coordinates $(Lat_2, Lng_2)$ at distance $d = 5000\text{m}$:
  $$Lat_2 = \arcsin(\sin(Lat_1)\cos(d/R) + \cos(Lat_1)\sin(d/R)\cos(\theta))$$
  $$Lng_2 = Lng_1 + \arctan2(\sin(\theta)\sin(d/R)\cos(Lat_1), \cos(d/R)-\sin(Lat_1)\sin(Lat_2))$$
- **Corridor Bounding Box**: Generates a 5km vector corridor bounding box for POI query filtering.

### Android Foreground Service (`ProximityPrefetchService.kt`)
- **Manifest Declaration**: `<service android:name=".service.ProximityPrefetchService" android:foregroundServiceType="dataSync" android:exported="false" />`.
- **Ongoing Notification**: Displays persistent status notification (`"prefetch_channel"`) informing the user of active vector trajectory caching.
- **Background Execution**: Periodically recalculates movement vector on location changes, triggers POI queries via `GeminiQueryService` / `ScoutGemRepository`, and inserts prefetched gems into Room database with `badgeLabel = "PREFETCHED 5KM VECTOR"`.

---

## 3. UI Integration & Telemetry

### Top Bar & Profile Integration
- **Vector Telemetry Pill**: Displays live prefetch status in `VividScoutTopBar` (e.g. `5km Vector Active • 12 Cached`).
- **Service Toggle**: Added toggle switch in `ProfileScreen` to enable or disable background proximity prefetching.

---

## 4. Step-by-Step Action Plan

1. **Step 1**: Create `MovementVectorUtils.kt` (`com.example.util`).
2. **Step 2**: Create `ProximityPrefetchService.kt` (`com.example.service`).
3. **Step 3**: Update `AndroidManifest.xml` with service entry and notification channel permissions.
4. **Step 4**: Update `ScoutViewModel.kt` to bind to `ProximityPrefetchService` state.
5. **Step 5**: Update `VividScoutTopBar.kt` and `ProfileScreen.kt` with vector status indicator.
6. **Step 6**: Create `MovementVectorUtilsTest.kt` unit tests.
7. **Step 7**: Verify with `compile_applet` and `gradle :app:testDebugUnitTest`.

---

## 5. Verification Plan
- Run `compile_applet` to confirm Foreground Service declaration and Room DB integrations compile cleanly.
- Run `gradle :app:testDebugUnitTest` to test 5km movement vector projection math.
- Verify prefetch status notification channel creation and UI telemetry pill updates.

# Implementation Plan: Adaptive Battery & Speed Policy for Proximity Prefetch

## 1. Goal Analysis
Implement an intelligent adaptive battery and speed policy for the `ProximityPrefetchService` in *Scout Lisbon*. The policy dynamically adjusts location polling and prefetch behavior based on real-time device battery metrics (level & power-save mode) and movement velocity, preserving battery life and avoiding inefficient gem prefetching during transit.

---

## 2. Core Policy Architecture

### 2.1 Adaptive Power & Motion Profiles (`AdaptivePrefetchPolicy.kt`)
We will create a domain model and evaluator:
- **`PrefetchPowerMode`**:
  - `OPTIMAL_WALKING`: Battery > 20%, speed $\le 6.9\text{ m/s}$ ($\approx 25\text{ km/h}$). Polling: 15s interval, 25m distance threshold, 400m displacement to trigger 5km prefetch.
  - `VEHICLE_SPEED_PAUSED`: Speed $> 6.9\text{ m/s}$ ($\approx 25\text{ km/h}$, e.g. trains, metro, buses, cars). Prefetching paused until walking speed resumes; location tracking throttled to 60s low-power polling.
  - `LOW_BATTERY_ECO`: Battery $\le 20\%$ or Android Power Saver active. Polling: 3 minutes (180s) interval, 100m distance threshold, 1000m displacement required to trigger prefetch.
  - `CHARGING_UNRESTRICTED`: Device plugged into power. Standard optimal walking polling.
- **`BatteryMonitor`**:
  - Listens to `Intent.ACTION_BATTERY_CHANGED` (battery level %, plugged status) and `PowerManager.isPowerSaveMode`.
  - Exposes reactive `StateFlow<BatteryState>`.

### 2.2 Foreground Service Integration (`ProximityPrefetchService.kt`)
- Monitor battery broadcasts and GPS velocity in real time.
- Dynamically update the active `LocationRequest` via `fusedLocationClient.requestLocationUpdates(...)` when the profile transitions between `OPTIMAL_WALKING`, `VEHICLE_SPEED_PAUSED`, and `LOW_BATTERY_ECO` without stopping the service.
- When `VEHICLE_SPEED_PAUSED` is active:
  - Skip AI corridor queries.
  - Update status message: `"Vehicle speed detected (X km/h) • Prefetch paused until walking resumes"`.
- When `LOW_BATTERY_ECO` is active:
  - Require 1000m displacement before triggering a corridor prefetch.
  - Update status message: `"Eco Mode (Battery X%) • Polling every 3m"`.
- Expose `activePowerMode: StateFlow<PrefetchPowerMode>` in the companion object.
- Update the persistent notification with the active mode badge (e.g. `[Eco Mode: 18% Battery]` or `[Transit Speed: Paused]`).

### 2.3 UI & Telemetry Enhancements
- **Top Bar (`VividScoutTopBar.kt`)**:
  - Update the `⚡ 5km Vector` indicator pill to reflect current mode (e.g., green for active, amber/orange for Eco Mode, yellow/slate for Vehicle Paused).
- **Profile Screen (`ProfileScreen.kt`)**:
  - Display an **Adaptive Battery & Speed Policy** card/chip:
    - Current device battery percentage and power profile tag.
    - Active GPS speed in km/h with mode label (Walking vs. Transit).
    - Polling interval readout (e.g. "15s polling" vs "3m polling").
    - Visual status badges for Eco Mode and Transit Pause.

---

## 3. Step-by-Step Action Plan

1. **Step 1**: Create `AdaptivePrefetchPolicy.kt` (`com.example.util`) containing power profiles, battery evaluation models, and policy thresholds.
2. **Step 2**: Enhance `ProximityPrefetchService.kt` to monitor battery status via broadcast receiver, evaluate velocity thresholds, dynamically update fused location requests, and update ongoing notifications with power mode labels.
3. **Step 3**: Expose `activePowerMode` and battery metrics in `ScoutViewModel.kt`.
4. **Step 4**: Update `VividScoutTopBar.kt` and `ProfileScreen.kt` to render the active power profile pill and telemetry.
5. **Step 5**: Create unit tests in `AdaptivePrefetchPolicyTest.kt` verifying policy transitions, speed threshold checks, and battery level rules.
6. **Step 6**: Verify the build with `compile_applet` and execute unit tests with `gradle :app:testDebugUnitTest`.

---

## 4. Verification Plan
- Unit tests validating transitions:
  - Battery 80%, Speed 1.4 m/s -> `OPTIMAL_WALKING` (15s polling, 400m prefetch trigger)
  - Battery 80%, Speed 12.0 m/s -> `VEHICLE_SPEED_PAUSED` (prefetch paused, low-power monitoring)
  - Battery 15%, Speed 1.4 m/s -> `LOW_BATTERY_ECO` (180s polling, 1km displacement trigger)
  - Battery 15%, Plugged in -> `CHARGING_UNRESTRICTED`
- Verify clean compilation with `compile_applet`.
- Verify test suite pass with `gradle :app:testDebugUnitTest`.

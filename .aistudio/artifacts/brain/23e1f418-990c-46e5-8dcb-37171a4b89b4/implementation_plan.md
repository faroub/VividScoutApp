# Implementation Plan - Overpass API Diagnostic Mode & JSON Inspector

Add a diagnostic inspection tool to analyze Overpass QL query results, view raw JSON responses from OSM mirrors, examine entity mapping success rates, copy raw logs, and manually test query execution.

## Proposed Changes

### Data & Network Layer
- **`OverpassRetrofitClient.kt`**:
  - Add `OverpassDiagnosticLog` data class holding:
    - Mirror URL used
    - HTTP status code & execution duration (ms)
    - Full raw JSON response payload string
    - Total raw `elements` array count
    - Count of parsed `ScoutSpotEntity` objects mapped to Room DB
    - Mapping error details (if any)
  - Retain the latest diagnostic log state in `OverpassRetrofitClient` so UI can inspect the exact payload returned by OpenStreetMap.
  - Add `fetchDiagnosticLog(lat, lng, radiusMeters)` to capture raw JSON string and telemetry without mutating main app state.

### Repository & ViewModel Layer
- **`ScoutGemRepository.kt`**:
  - Expose `lastDiagnosticLog: StateFlow<OverpassDiagnosticLog?>` and `runDiagnosticQuery(lat, lng)`.
- **`ScoutViewModel.kt`**:
  - Add `_showDiagnosticDialog: MutableStateFlow<Boolean>` and `_diagnosticLog: MutableStateFlow<OverpassDiagnosticLog?>`.
  - Expose `openDiagnosticDialog()`, `closeDiagnosticDialog()`, `copyDiagnosticLogToClipboard()`, and `executeDiagnosticQuery()`.

### UI Layer
- **`OverpassDiagnosticDialog.kt`** (New Composable):
  - Material 3 Dialog styled with dark code surface for raw JSON payload.
  - Displays mirror endpoint URL, response timing (ms), raw elements count vs mapped Room spots.
  - Includes a "Copy Raw JSON" button using Android `ClipboardManager`.
  - Includes a "Run Test Query" action button to execute real-time Overpass diagnostic fetch for user's current GPS coordinates.
- **UI Triggers**:
  - Add "Overpass API Diagnostics" button in `Settings` screen.
  - Add diagnostic trigger icon on `OfflineSyncStatus` card in Explore tab.

## Verification Plan

### Automated Tests
- Add unit test to `OverpassRetrofitClientTest.kt` verifying `OverpassDiagnosticLog` accurately counts elements and captures raw JSON string.
- Execute unit test suite: `gradle :app:testDebugUnitTest`.

### Manual Verification
- Compile app via `compile_applet`.
- Open settings or sync status badge to trigger Diagnostic Mode.
- Verify raw JSON is displayed with copy action and manual re-query works cleanly.

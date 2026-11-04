# VividScout

VividScout is a native Android application built with Jetpack Compose, Room, and Firebase that enables travelers to discover authentic local hidden gems, historical landmarks, dining spots, and shortcuts around their live GPS location or selected global city. It features an offline-first vector map engine, on-foot 360° radar tracking, AI-powered trajectory corridor prefetching, and crowd-sourced community notes.

---

## Technical Requirements

- **Android Studio**: Android Studio 2026.1.4 or newer
- **JDK Version**: JDK 25 (required to run Gradle daemon) / Java 11 (app compilation language level)
- **Minimum SDK (`minSdk`)**: 24 (Android 7.0 Nougat)
- **Compile SDK (`compileSdk`)**: 36 (Android 15 / Android 16 Preview)
- **Target SDK (`targetSdk`)**: 36

---

## Firebase Setup & Architecture

### Configuration File
Place your project's `google-services.json` file in the module directory:
```
app/google-services.json
```

### Firebase Services Used
- **Firebase Authentication (`firebase-auth`)**: Provides anonymous user sign-in (`signInAnonymously()`) to securely authenticate users before performing Firestore database writes.
- **Cloud Firestore (`firebase-firestore`)**: Enables bidirectional cloud synchronization for user-contributed spots (`community_submissions`) and live community notes (`community_tips`).
- **Firebase App Check (`firebase-appcheck-*`)**: Enforces client attestation using `PlayIntegrityAppCheckProviderFactory` in release builds and `DebugAppCheckProviderFactory` in debug builds.
- **Firebase AI Logic (`firebase-ai`)**: Uses the Google Gemini 3.8 Flash model (`gemini-3.8-flash`) to generate structured, real-time spot recommendations and pre-cache 5km trajectory corridor POIs.

### Firestore Database ID
The database ID is dynamically retrieved from string resource `R.string.firestore_database_id` (defined in `app/src/main/res/values/strings.xml`). If the string resource is blank or undefined, `FirestoreSyncManager.kt` uses the default FirebaseFirestore instance (`FirebaseFirestore.getInstance()`) and logs a warning in Logcat.

---

## Build & Test Commands

### Debug Build
Build the debug APK:
```bash
./gradlew :app:assembleDebug
```

### Debug Build Setup
- **Project-Local Keystore**: The debug build configuration (`debugConfig`) references `${rootDir}/debug.keystore` with default credentials (`storePassword = "android"`, `keyAlias = "androiddebugkey"`, `keyPassword = "android"`).
- **Fresh Clone Behavior**: On a fresh clone where `${rootDir}/debug.keystore` is absent, the Android Gradle Plugin (AGP) automatically falls back to generating or using the default user-level Android debug keystore at `~/.android/debug.keystore`.
- **Zero Configuration**: Developers can run `./gradlew :app:assembleDebug` or deploy directly from Android Studio immediately on a fresh clone without setting any environment variables.

### Unit Tests
Run the local unit test suite (Robolectric & Room tests):
```bash
./gradlew :app:testDebugUnitTest
```

---

## Release Signing & Environment Variables

To build and sign production release artifacts (`assembleRelease` or `bundleRelease`), release signing parameters must be provided via environment variables.

### Required Environment Variables

| Variable | Description |
| :--- | :--- |
| `KEYSTORE_PATH` | Absolute path to the release `.jks` or `.keystore` file. |
| `STORE_PASSWORD` | Keystore store password. |
| `KEY_ALIAS` | Key alias in the keystore. |
| `KEY_PASSWORD` | Key password for the specified alias. |

### Release Build Example

```bash
export KEYSTORE_PATH="/path/to/release-key.jks"
export STORE_PASSWORD="your_store_password"
export KEY_ALIAS="your_key_alias"
export KEY_PASSWORD="your_key_password"

./gradlew :app:assembleRelease
```

> **Fail-Closed Protection:** Executing release packaging tasks (`assembleRelease`, `bundleRelease`, `packageRelease`) without configuring all four required environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) or if the keystore file at `KEYSTORE_PATH` does not exist causes the build to fail closed immediately with an explicit `GradleException` error message pointing to this README section. Release builds never fall back to debug keys.

---

## Known Limitations

- **Prototype Feature — Peer Mesh Sync**: The Peer-to-Peer (P2P) mesh synchronization feature in `PeerMeshSyncEngine.kt` is a **simulated UI prototype**. It uses in-memory demo peers to demonstrate UI workflows. No Bluetooth, BLE, or Wi-Fi Direct permissions or hardware socket APIs are declared or used in production mode (`DemoModeProvider.isDemoPeerSyncEnabled` defaults to `false`).

---

## Community Voting & Account Identity Policy

VividScout enforces a strict **One Vote Per Firebase Account** policy on all community tips and status updates:
- **Per-Account Vote Tracking**: Firestore security rules enforce unique subcollection documents (`community_tips/{tipId}/votes/{authenticatedUid}`). Each authenticated Firebase account can submit at most 1 vote per tip.
- **Anonymous Account Scope**: Firebase UIDs represent individual Firebase Authentication accounts. To preserve voting history across device resets, users can link their anonymous account to a Google account or Credential Manager credential.
- **Zero Invasive PII Collection**: VividScout never requests or collects phone numbers, national identity documents, or invasive biometric identifiers.

---

## Privacy-Preserving Logging & Telemetry

VividScout enforces privacy-first logging using `com.example.util.SafeLog`:
- **Debug-Only Emission**: Log output is emitted strictly in debug builds (`BuildConfig.DEBUG == true`) and suppressed in production release builds.
- **Anonymized User Identifiers**: Firebase Authentication UIDs are truncated to the first 4 characters (e.g. `a1b2***`), preventing user tracking across Logcat output.
- **Sanitized GPS Coordinates**: Precise user coordinates and AI spot location coordinates are rounded to 2 decimal places (approx. 1.1 km accuracy) in log outputs, protecting precise location history.
- **Shared Trips Privacy Policy**: Read-only shared trip links store ONLY public spot titles, coordinates, and route waypoints under random 128-bit IDs. They contain zero user UIDs, zero device IDs, and zero location tracking history, and automatically expire after 30 days (`expireAt` TTL timestamp).
- **Non-Sensitive Operational Telemetry**: General operational metadata (spot titles, category labels, count totals, and status codes) is preserved for debugging.

---

## Documentation & Known Issues

For detailed architectural notes, lint baseline details, and refactoring history, refer to:
- [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)

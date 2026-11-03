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
Build the debug APK using the local debug keystore:
```bash
./gradlew :app:assembleDebug
```

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

> **Fail-Closed Protection:** Executing release build tasks without configuring these environment variables or if the keystore file does not exist causes the Gradle build to fail closed immediately with a clear error message. Debug builds continue using the default debug keystore automatically.

---

## Known Limitations

- **Prototype Feature — Peer Mesh Sync**: The Peer-to-Peer (P2P) mesh synchronization feature in `PeerMeshSyncEngine.kt` is a **simulated UI prototype**. It uses in-memory demo peers to demonstrate UI workflows. No Bluetooth, BLE, or Wi-Fi Direct permissions or hardware socket APIs are declared or used in production mode (`DemoModeProvider.isDemoPeerSyncEnabled` defaults to `false`).

---

## Documentation & Known Issues

For detailed architectural notes, lint baseline details, and refactoring history, refer to:
- [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)

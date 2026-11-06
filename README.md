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
Debug APKs are always signed with a standard Android debug key (well-known credentials: `storePassword = "android"`, `keyAlias = "androiddebugkey"`, `keyPassword = "android"` — not a secret). Two modes are supported:
- **Project-Local Keystore (AI Studio convention)**: When `debug.keystore` exists in the repository root, the `debugConfig` signing configuration signs debug builds with it. The file is gitignored, so this applies only to machines where it has been provided or generated.
- **Fresh Clone Behavior**: When no `debug.keystore` is present (fresh clone, CI runner), no custom signing configuration is assigned and AGP falls back to its standard debug signing: the user-level debug keystore at `~/.android/debug.keystore`, which AGP generates automatically on first use. No committed keystore and no environment variables are required.
- **Zero Configuration**: Developers can run `./gradlew :app:assembleDebug` or deploy directly from Android Studio immediately on a fresh clone without setting any environment variables. CI builds the debug APK the same way.

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

## Continuous Integration & Local Regression Suite

VividScout includes an automated GitHub Actions CI workflow in `.github/workflows/ci.yml` that triggers on `push` to `main` and on pull requests:
- **JDK 25 & Node.js 20 Setup**: Environment configured with Gradle dependency caching. JDK 25 is required to run the Gradle daemon (see `gradle/gradle-daemon-jvm.properties`) and also drives the Firebase Emulators.
- **Cloud Function Syntax Guard**: Runs `node --check functions/index.js` to catch entry-point syntax regressions.
- **Cloud Function Validation Tests**: Runs `node functions/nearbyPlaces.test.js`.
- **Firebase Security Rules Emulator Tests**: Installs `functions/package.json` dev dependencies (`@firebase/rules-unit-testing` + `firebase-tools`), then starts the **real Firestore and Storage emulators** (`firebase emulators:exec --only firestore,storage`) and executes the backend test suites against them. The exact production `firestore.rules`, `storage.rules`, and `firestore.indexes.json` files are loaded and evaluated by Firebase's own engine; the suites abort if the emulator host environment variables are missing, so they can never touch a production project.

### Firestore Composite Indexes

The community photo stream queries `spot_photos` with `whereEqualTo("spotId", spotId)` followed by `orderBy("timestamp", Query.Direction.DESCENDING)`, bounded to the newest 20 photos (`PhotoModerationRepository.PHOTOS_PAGE_SIZE`). Opening a spot therefore never downloads its full photo history; older photos are loaded on demand through the "Show older photos" action, which issues the same ordering continued with `startAfter(<oldest loaded timestamp>)`. Both queries use the same composite index declared in `firestore.indexes.json` and wired into `firebase.json`; a fresh Firebase project without it rejects the listener with `FAILED_PRECONDITION` (the app surfaces this as an explicit photo-stream error, never as an empty photo grid, and a failed older-photos page surfaces as an error message rather than being mistaken for "no older photos exist").

Deploy all declared indexes to your Firebase project:

```bash
firebase deploy --only firestore:indexes
```

Index building is asynchronous — a query executed immediately after deployment may still report missing-index until the index finishes building (usually a few minutes; watch the Firebase Console → Firestore → Indexes tab). During that window the app shows the listener error state and retries automatically on the next snapshot, so no manual action is required.

### Firestore Database ID

The Android app may target a named Firestore database through the `firestore_database_id` string resource (`app/src/main/res/values/strings.xml`, empty by default): when non-blank, `SharedTripManager` and `FirestoreSyncManager` open `FirebaseFirestore.getInstance(app, databaseId)` instead of the default database. Cloud Functions are configured to the **same** database through the `FIRESTORE_DATABASE_ID` parameter (`functions/firestoreDatabase.js`): it accepts the same value at deploy time or via the environment variable, and canonicalizes both the empty string and `"(default)"` to the default database — so the shipped default configuration addresses one consistent database on both sides. If the two ends are ever configured with different IDs, publishing a shared trip would write to one database while the app reads from another — keep them in sync. The contract is enforced by `functions/firestoreDatabase.test.js`, and `functions/index.js` resolves its single Firestore instance (`getFirestore(app, firestoreDatabaseId())`) exclusively through it.

### Shared-Trip TTL Policy (Physical Deletion)

Published shared trips live for exactly 30 days: the trusted `publishSharedTrip` Cloud Function generates `createdAt` and `expireAt` from the server clock (client-supplied timestamps are rejected), and Firestore security rules deny public reads as soon as `expireAt <= request.time`. **Access expiration is enforced by the rules at the exact expiry time and does not wait for physical deletion.**

Physical removal of expired documents is handled by the Firestore TTL policy on `shared_trips.expireAt`. TTL policies are a server-side Firestore feature — they are **not** part of `firebase.json` (which deploys only rules and indexes) — so the policy must be configured once per Firebase project. The repository ships an idempotent script that configures the policy and verifies it is active:

```bash
./scripts/configure-shared-trips-ttl.sh [PROJECT_ID]
```

It runs `gcloud firestore fields ttl update expireAt --collection-group shared_trips` followed by `gcloud firestore fields ttl describe expireAt --collection-group shared_trips` to verify, and requires the gcloud CLI with access to the project. Equivalent configuration is also available in the Google Cloud Console under Firestore → TTL policy.

Firestore TTL deletion is **asynchronous and best-effort**: expired documents are typically removed within 24 hours of expiry, but deletion is not guaranteed to happen at the exact expiration timestamp. This is safe by design — expired links are already unreadable through the security rules at expiry, so the app never depends on immediate deletion for access control. Deleting a document with an active TTL policy is not supported; the `update` command is re-runnable and reflects the current policy.

### Local Regression Test Commands

To execute the complete regression test suite locally in your terminal:

```bash
# 1. Run Node.js Cloud Function Validation Tests
node functions/nearbyPlaces.test.js

# 2. Run Firebase Security Rules Emulator Tests (requires Java 11+ and one-time
#    npm install:  npm --prefix functions ci )
./functions/node_modules/.bin/firebase emulators:exec --only firestore,storage \
  "npm --prefix functions test"

# 3. Run Android Unit Test Suite
./gradlew testDebugUnitTest

# 4. Run Android Lint Checks
./gradlew lintDebug

# 5. Build Android Debug Variant
./gradlew assembleDebug
```

---

## Documentation & Known Issues

For detailed architectural notes, lint baseline details, and refactoring history, refer to:
- [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)

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

## Private Scout Notes (Public/Private Data Boundary)

Private scout notes are **never** written to the publicly readable `community_submissions` collection: Firestore has no field-level access control, so every field of a public document is world-visible. The client (`FirestoreSyncManager`) now persists notes only under the owner-scoped path `users/{uid}/private_spot_notes/{spotId}`, using the authenticated UID as the collection path key, and `firestore.rules` denies submissions containing a `userNotes` field entirely (create and update), so retries and legacy clients cannot smuggle notes back into a public document.

Access rules for `users/{uid}/private_spot_notes/{spotId}`:
- **Read (get and list)**: only the authenticated owner (`uid == request.auth.uid`). Other users and unauthenticated clients are denied, so no cross-user reads or enumeration are possible.
- **Create/Update/Delete**: owner only; the embedded `ownerUid` must equal the path key and may never change; fields are type-checked with a 2000-character note limit.
- On every draft sync, the note is written to the private path and the public submission is written without it — both writes are idempotent, and a failed private-note write fails the whole sync so nothing is silently dropped.
- Local Room notes are unchanged; the owner's own private notes are restored into local spots whose Room note is still blank (local edits always win).

### Legacy Private-Note Migration (one-time, per production Firebase project)

Existing public submissions created by older app versions may still contain a `userNotes` field. The migration script moves each note into the owner's private document and strips the public field inside a single Firestore transaction, so the copy and the removal commit atomically:

```bash
# 1. Dry-run (default): reads production, prints the plan and counts, writes nothing
node scripts/migrate-private-notes.js

# 2. Live execution (requires credentials, e.g. GOOGLE_APPLICATION_CREDENTIALS or
#    gcloud auth application-default login, plus --execute):
node scripts/migrate-private-notes.js --execute
```

Safety properties: dry-run is the default; pagination is by document-id cursor and resumable; re-running a completed migration performs no writes (idempotent); a note is only ever moved into the account named by the submission's own `ownerUid`; an existing private note is never overwritten (identical content completes the copy, differing content is logged as a conflict and left for manual review); submissions with a missing/invalid `ownerUid` are logged for manual review and left untouched; logs contain document IDs and counts only — note contents are never printed. The migration logic is verified against the Firestore emulator by `functions/migrate-private-notes.test.js`.

---

## Community Photo Immutability & Authoritative Moderation

Community photo objects and their metadata are treated as **published, immutable records**:

- **Storage objects are immutable to clients.** `storage.rules` allows each authenticated user to create a *new* object under their own ownership path `spot_photos/{uid}/{spotId}/{filename}` (image/jpeg or image/png, under 5 MB). Once written, the object cannot be changed or removed by any client: byte-level overwrite is rejected by the `resource == null` clause of the create rule (the Storage rules runtime evaluates a client re-upload of an existing object against the create rule), and both `update` and `delete` are denied outright. All removal happens through trusted backend functions, so Storage and Firestore are always changed consistently.
- **Photo metadata is trusted-writer-only.** `firestore.rules` denies every client write to `spot_photos`; documents are created only by the `finalizePhotoUpload` Cloud Function after it has verified the object, and only updated by backend sweeps. Registration is atomic: the photo document id is the SHA-256 digest of the canonical Storage path, and the document and its rate-limit increment are committed in one Firestore transaction, so concurrent finalizations of the same object resolve to exactly one registration (no duplicate metadata, no partial publication), and a replay of a completed upload returns the published photo without consuming rate-limit budget.
- **Moderation is authoritative, not a UI filter.** Reaching the 3-report threshold used to only *hide* a photo in the app — anyone who already held the object's public download URL could still fetch the image (Storage download tokens stay valid until the object is deleted). The scheduled `moderateReportedPhotos` function (every 15 minutes) now deletes the Storage object — which revokes the download URL with it — and marks the Firestore record `moderationState: "removed"` (with a server-side `moderatedAt` timestamp) only after the object is confirmed gone; a failed delete leaves the record unmarked so the next sweep retries. The sweep is idempotent and tolerates objects already removed by a concurrent reaper.
- **Client uploads orphan nothing the backend cannot clean.** The Android orchestrator no longer deletes uploaded objects on failure or cancellation (clients have no deletion permission at all): ambiguous outcomes — where the server may have registered the photo after the client lost the response — always keep the object, and the scheduled orphan reaper removes confirmed unregistered uploads (age- and ownership-gated) instead.

Deployment notes: the moderation sweep runs on the Cloud Functions scheduler and needs no extra configuration beyond deploying `functions/index.js`; photo reporting (the 3-report threshold) requires no index beyond the automatic single-field index on `reportsCount`. Existing photo records published before this change have no `moderationState` field and are treated as active until the sweep first moderates them.

### Ambiguous Upload Outcomes and Idempotent Retries

A photo registration can fail *after the server committed it* — the response is lost on the network, and the client cannot distinguish "registration never happened" from "registration happened but its reply was lost". The Android upload flow handles this without ever deleting or guessing:

- **Deterministic object identity.** Each photo is uploaded to `spot_photos/{uid}/{spotId}/{sha256(content)}.jpg` — the Storage path is derived from the photo content — and the backend derives its registration document id from the same path. Uploading or retrying the same image always addresses the exact same registration.
- **Reconciliation, not re-publication.** When the object is already in place, the upload step is skipped and finalization replays the already-published photo; a bounded retry of the same finalization (transient failures only, definitive rejections fail immediately) resolves a lost response by re-calling the backend, which replays the committed registration instead of duplicating it.
- **Honest failure reporting.** A failure that occurred after the trusted registration was attempted surfaces with `PhotoUploadException.registrationAttempted = true` ("the photo may already be published — retrying reconciles"), while pre-registration failures report the underlying cause; coroutine cancellation always propagates and never becomes an ordinary failure.
- **Cancellation safety.** No `runCatching` wraps the suspending upload path anymore — cancellation is rethrown, and the interrupted upload leaves its object for the backend reaper (clients have no deletion capability to misuse).

### Trusted Byte-Level Image Validation

The declared content type and the app-side processing are **not** a security boundary — a client can upload arbitrary bytes while claiming `image/jpeg`. The `finalizePhotoUpload` Cloud Function therefore validates the actual object content server-side with [`sharp`](https://sharp.pixelplumbing.com/) before registering anything:

- **Magic bytes + real decode**: the object must genuinely be a JPEG or PNG. Random binary data, HTML, SVG, scripts, and corrupted or truncated images are rejected — no photo metadata document is ever created for them (verified by `functions/photos.test.js` against real payloads, including decompression-bomb-style files whose header claims huge dimensions).
- **Safety bounds** mirroring the Android `PhotoProcessor`: at most 5 MB, 12 000 px per dimension, and 50 megapixels total.
- **Canonical re-encode**: accepted images are re-encoded in place at the canonical path as auto-oriented JPEG (quality 85, long edge capped at 1600 px) with **all metadata stripped**, so EXIF/GPS data never reaches a published photo; the object's content type and ownership metadata are rewritten canonically.
- **Ownership agreement**: the object's `ownerUid` and `spotId` custom metadata must exactly match the caller and the canonical Storage path.

Deployment note: `sharp` is a regular dependency of the Cloud Functions (`functions/package.json`) and installs prebuilt binaries for the Node 20 linux-x64 Functions runtime; no extra build tooling or Firebase Console configuration is required.

---

## Privacy-Preserving Logging & Telemetry

VividScout enforces privacy-first logging using `com.example.util.SafeLog`:
- **Debug-Only Emission**: Log output is emitted strictly in debug builds (`BuildConfig.DEBUG == true`) and suppressed in production release builds.
- **Anonymized User Identifiers**: Firebase Authentication UIDs are truncated to the first 4 characters (e.g. `a1b2***`), preventing user tracking across Logcat output.
- **Sanitized GPS Coordinates**: Precise user coordinates and AI spot location coordinates are rounded to 2 decimal places (approx. 1.1 km accuracy) in log outputs, protecting precise location history.
- **Shared Trips Privacy Policy**: Read-only shared trip links store ONLY public spot titles, coordinates, and route waypoints under random 128-bit IDs. They contain zero user UIDs, zero device IDs, and zero location tracking history, and automatically expire after 30 days (`expireAt` TTL timestamp).
- **Non-Sensitive Operational Telemetry**: General operational metadata (spot titles, category labels, count totals, and status codes) is preserved for debugging.

## Location Data Flows (Discovery)

Location-based discovery transmits the user's coordinates off the device. The flows, verified against the implementation:

**Device → VividScout Cloud Function → external providers**
- `nearbyPlaces` (callable, App Check + auth enforced): the request carries `lat`, `lon`, `radiusMeters`. The function forwards them to:
  - **OpenTripMap API** (`api.opentripmap.com`, ODbL) — `places/radius` query.
  - **GeoNames / Wikipedia** (`secure.geonames.org`, CC BY 4.0) — `findNearbyWikipedia` query.
  - The function stores nothing; coordinates appear in its logs rounded to 2 decimals (~1.1 km).

**Device → Firebase AI Logic**
- `GeminiSpotService` sends the user's coordinates inside the discovery prompt to Firebase AI Logic (Gemini) to generate candidate spots.

**Device → external providers directly (no VividScout server in between)**
- **OpenStreetMap Overpass** (`overpass-api.de` and mirrors) — `OverpassScraperService` posts a bounding-box query built from the user's location.
- **Wikipedia API** — `WikipediaPhotoService` geosearch (`ggscoord=$lat|$lon`) for spot imagery.

**What is stored**
- Spot documents (saved, discovered, and community submissions) contain the coordinates **of the spots themselves** — user-generated content that is public by design, no different from sharing a pin.
- User profiles contain no location history. The proximity prefetch service (user-started, foreground-only, with a visible notification) keeps coordinates in memory/Room cache only.
- The app never requests the `ACCESS_BACKGROUND_LOCATION` permission.

**User controls**
- Denying the location permission or disabling GPS makes every flow above impossible: discovery falls back to the default Lisbon area. The privacy note in the location dialog describes this behavior honestly (EN/FR/AR strings stay in sync: `location_privacy_note` / `AppStringsData.locationPrivacyNote`).

---

## Continuous Integration & Local Regression Suite

VividScout includes an automated GitHub Actions CI workflow in `.github/workflows/ci.yml` that triggers on `push` to `main` and on pull requests:
- **JDK 25 & Node.js 20 Setup**: Environment configured with Gradle dependency caching. JDK 25 is required to run the Gradle daemon (see `gradle/gradle-daemon-jvm.properties`) and also drives the Firebase Emulators.
- **Cloud Function Syntax Guard**: Runs `node --check functions/index.js` to catch entry-point syntax regressions.
- **Cloud Function Validation Tests**: Runs `node functions/nearbyPlaces.test.js`.
- **Firebase Security Rules Emulator Tests**: Installs `functions/package.json` dev dependencies (`@firebase/rules-unit-testing` + `firebase-tools`), then starts the **real Firestore and Storage emulators** (`firebase emulators:exec --only firestore,storage`) and executes the backend test suites against them. The exact production `firestore.rules`, `storage.rules`, and `firestore.indexes.json` files are loaded and evaluated by Firebase's own engine; the suites abort if the emulator host environment variables are missing, so they can never touch a production project.
- **Android ↔ Functions Firebase Configuration Contract**: `functions/androidFirebaseConfig.test.js` reads the actual committed `strings.xml`, `AndroidManifest.xml` and `build.gradle.kts` and verifies both ends agree on the Firestore database ID and the App Link domain.
- **Release Variant Validation** (separate `release-validation` job): validates the release variant with R8 minification and resource shrinking enabled. It requires two secret groups because the project gates release packaging on signing by design (see "Release Signing & Environment Variables"):
  - `FIREBASE_CONFIG` — base64-encoded `app/google-services.json` of the intended Firebase project (decoded onto the ephemeral runner, never committed; the job fails immediately when it is missing, since a release build cannot otherwise validate its Firebase configuration).
  - `SIGNING_KEYSTORE` (base64 keystore), `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — decoded to `"$RUNNER_TEMP/release.keystore"` and injected through the same environment variables as a local release build. Without them the job fails clearly instead of claiming an unsigned build was verified. Signing material is removed in a final `always()` step.
  The job then runs `:app:assembleRelease` and `:app:lintRelease` against the signed, configured release variant.

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

Publishing is atomic: after schema validation, a single Firestore transaction reads the client-supplied `requestId` idempotency marker (replaying the already-published trip for the same owner, rejecting reuse of a marker by a different owner), reads and increments the per-UID publishing rate-limit counter, and creates the trip document and its idempotency record in one commit. Concurrent duplicates of the same share intent therefore resolve to exactly one trip — an interrupted client can safely retry without publishing a second trip — and a rate-limit slot is consumed only when the publish actually commits.

Physical removal of expired documents is handled by two Firestore TTL policies (a server-side feature — TTL policies are **not** part of `firebase.json`, which deploys only rules and indexes — so they must be configured once per Firebase project). The repository ships an idempotent script that configures both policies and verifies they are active:

```bash
./scripts/configure-shared-trips-ttl.sh [PROJECT_ID]
```

The script configures:

- `shared_trips.expireAt` (no offset — documents expire at `expireAt`), and
- `shared_trip_publish_requests.createdAt` with a **90-day offset** (`--expiration-offset=2160h`). The publish-request markers are the idempotency records that make retries replay instead of re-publishing, so they must outlive any plausible client retry window — far beyond the 30-day trip lifetime — while still not accumulating forever.

It uses the currently supported `gcloud firestore fields ttls update <field> --collection-group=... --enable-ttl` command and verifies with `gcloud firestore fields ttls list --format=json`, failing with a clear error unless the policy reaches the `ACTIVE` state. The Firestore database is selected explicitly via `--database` and can be overridden with the `FIRESTORE_DATABASE_ID` environment variable (same convention as the Cloud Functions and the app; unset/empty means `(default)`), so the policy is never configured against a different database than the one the app uses. gcloud prerequisites: install the CLI, run `gcloud auth login`, and hold `roles/datastore.indexes.admin` (to update) plus `roles/datastore.viewer` (to verify) on the project. Equivalent configuration is also available in the Google Cloud Console under Firestore → TTL policy.

Firestore TTL deletion is **asynchronous and best-effort**: expired documents are typically removed within 24 hours of expiry, but deletion is not guaranteed to happen at the exact expiration timestamp. This is safe by design — expired links are already unreadable through the security rules at expiry (`expireAt <= request.time`), so the app never depends on immediate deletion for access control. The script is re-runnable and reflects the current policy.

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

# 6. Run scripts tests (TTL policy with mocked gcloud, asset links validation)
bash -c 'node --test scripts/__tests__/*.test.js'
```

---

## Android App Links & Digital Asset Links

The manifest declares an `autoVerify="true"` App Link for `https://vividscout.app/trip/{tripId}` (shared itineraries), plus the `vividscout://trip/...` custom scheme as a fallback. The App Link association is proven **only** by the Digital Asset Links file deployed at the domain:

```text
https://vividscout.app/.well-known/assetlinks.json
```

That file is maintained by the **web hosting deployment of vividscout.app, which lives outside this repository** — it is an external deployment dependency. It must declare:

- the production Android application id (`com.aistudio.vividscout.trvjxq`, read from `app/build.gradle.kts`), and
- the SHA-256 fingerprint of the **release** signing certificate (see "Release Signing & Environment Variables" — the fingerprint comes from the keystore configured for release builds; it is never invented or assumed here).

Verify the deployed file at any time:

```bash
# Checks the deployed file against the application id in build.gradle.kts
node scripts/verify-assetlinks.js

# Also compare the release certificate fingerprint
node scripts/verify-assetlinks.js --expect-fingerprint AB:CD:...:12
```

The script exits non-zero when the file is unreachable, malformed, targets another package, lacks certificate fingerprints, or omits the `android.intent.action.VIEW` relation. Deep-link failures (malformed links, nonexistent or expired trips) are covered by unit tests in `SharedTripManagerTest` and by the `shared_trips` rules tests; expired trips are additionally denied server-side by Firestore rules (`expireAt <= request.time`) independent of client checks.

---

## Documentation & Known Issues

For detailed architectural notes, lint baseline details, and refactoring history, refer to:
- [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)

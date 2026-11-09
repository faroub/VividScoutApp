# VividScoutApp — Verification Baseline

- **Date:** 2026-11-09 (pre-change baseline for the P1–P17 program)
- **Commit:** `d7c47f5` (P1 action pins + functions test glob; detekt baseline regenerated)
- **Working tree:** clean

## Measurements

| Check | Command | Result |
|---|---|---|
| Android unit tests | `./gradlew :app:testDebugUnitTest` | total **527**, passed **527**, failed **0**, skipped **0**, errors **0** |
| Firebase emulator rules + functions tests | `firebase emulators:exec --only firestore,storage "npm --prefix functions test"` | tests **299**, suites **34**, passed **299**, failed **0**, skipped **0** |
| nearbyPlaces standalone validation | `node functions/nearbyPlaces.test.js` | **25/25** passing |
| Android lint (debug) | `./gradlew :app:lintDebug` | errors **0**, warnings **0** (**185** pre-existing findings filtered by `app/lint-baseline.xml`) |
| detekt | `./gradlew :app:detekt` | **0** weighted issues (all acceptable-style findings recorded in `app/config/detekt/baseline.xml`) |
| Cloud Functions syntax | `node --check functions/index.js` | OK |

## Discrepancies vs docs/KNOWN_ISSUES.md section 8 (not edited in this task)

- KNOWN_ISSUES.md records **298** emulator-suite tests; the actual count is now **299**:
  P1 replaced the hand-maintained test-file list in `functions/package.json` with a
  glob, which now also includes `functions/nearbyPlaces.test.js` (1 test) that the old
  list excluded. CI's separate `node functions/nearbyPlaces.test.js` step is now
  redundant with the suite but was left unchanged (P1 changed no job logic).
- KNOWN_ISSUES.md records **527** Android tests — matches.
- KNOWN_ISSUES.md does not mention detekt; it is recorded here for the first time.

## Notes

- detekt only runs with the Gradle daemon on JDK 21 (`./gradlew updateDaemonJvm
  --jvm-version 21`), exactly as the CI detekt step does; the committed
  `gradle/gradle-daemon-jvm.properties` pin (JDK 25) is restored afterwards so the
  repository is unchanged (CI's copy is ephemeral on the runner).
- The regenerated detekt baseline absorbed 31 weighted findings introduced by the
  F1–F16 signatures (Compose LongMethod/LongParameterList/FunctionNaming and style
  items) — same acceptable styles the baseline already records.
- The emulator suite is serialized (`--test-concurrency=1`); the local emulator
  config uses isolated ports (Firestore 8180, Storage 9198) because port 8080 is
  occupied on the development machine.
- Any later change must not regress these numbers unless explicitly justified.

---

# VividScoutApp — Verification Baseline

- **Date:** 2026-11-09
- **Commit:** end of the F1–F16 fix batch (F11 pending design approval; F5 and F12 are operational checks requiring the maintainer's Firebase credentials)
- **Working tree:** clean

## Measurements

| Check | Command | Result |
|---|---|---|
| Android unit tests | `./gradlew :app:testDebugUnitTest` | total **512**, passed **512**, failed **0**, skipped **0**, errors **0** |
| Firebase emulator rules + functions tests | `firebase emulators:exec --only firestore,storage "npm --prefix functions test"` | tests **267**, suites **31**, passed **267**, failed **0**, skipped **0** |
| nearbyPlaces standalone validation | `node functions/nearbyPlaces.test.js` | **25/25** passing |
| Android lint (debug) | `./gradlew :app:lintDebug` | errors **0**, warnings **0** (**185** pre-existing findings filtered by `app/lint-baseline.xml`) |
| Cloud Functions syntax | `node --check functions/index.js` | OK |

## Changes relative to the 2026-11-07 baseline

- +53 Android tests (459 → 512) and +66 emulator-backed tests (201 → 267): report/vote
  callables and soft-hide moderation (F1), community write validation (F3/F6/F7),
  seed-tip migration (F4), shared-trip image-host allowlist (F8), Overpass egress (F9),
  deep-link dedupe (F13), backend hygiene (F14/F15), and the Europeana trusted
  boundary (F10).
- Lint: 0 errors and 0 warnings remain unfiltered; the baseline file covers 185
  pre-existing findings.
- The emulator suite is serialized (`--test-concurrency=1` in `functions/package.json`):
  serialized execution removed a cross-file fixture race in the photo-cleanup tests.
- The emulator alternates configuration uses isolated ports (Firestore 8180, Storage
  9198) because port 8080 is occupied on the development machine.

## Notes

- The emulator run uses the isolated emulator project configuration and loads the exact `firestore.rules` / `storage.rules` from the repository root.
- Lint warnings count comes from `app/build/reports/lint-results-debug.xml` (lint 9.1.1). `app/lint-baseline.xml` is committed and covers pre-existing findings.
- Any later change must not regress these numbers unless explicitly justified.

---

# VividScoutApp — Verification Baseline (2026-11-07)

Recorded before starting the prompt-1..11 change batch. All measurements were taken on a clean working tree at the commit below.

- **Date:** 2026-11-07
- **Commit:** `6125bf5d0ec8444f83077921c2c9ca71e13ce3b3` (`6125bf5` — "Add comprehensive tests for repository and synchronization logic")
- **Working tree:** clean

## Measurements

| Check | Command | Result |
|---|---|---|
| Android unit tests | `./gradlew :app:testDebugUnitTest` | total **459**, passed **459**, failed **0**, skipped **0**, errors **0** |
| Firebase emulator rules tests | `firebase emulators:exec --only firestore,storage "npm --prefix functions test"` | tests **201**, suites **20**, passed **201**, failed **0**, skipped **0** |
| Android lint (debug) | `./gradlew :app:lintDebug` | errors **0**, warnings **8** |

## Notes

- The emulator run uses the isolated emulator project configuration (`/tmp/firebase-emulator-config.json`, Firestore on 8280, Storage on 9299) and loads the exact `firestore.rules` / `storage.rules` from the repository root.
- Lint warnings count comes from `app/build/reports/lint-results-debug.xml` (lint 9.1.1). `app/lint-baseline.xml` is committed and covers pre-existing findings.
- Any later change in this batch must not regress these numbers unless explicitly justified.

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

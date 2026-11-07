# VividScoutApp — Verification Baseline

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

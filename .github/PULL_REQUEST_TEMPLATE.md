# Pull Request Template

## Summary

<!-- One paragraph: what changes, and why. -->

## Related issue

<!-- Link or "none". -->

## Checklist

- [ ] Unit tests run — `./gradlew :app:testDebugUnitTest` — count: `___` passed / `___` failed (must match `docs/BASELINE.md` or improve)
- [ ] Lint run — `./gradlew :app:lintDebug` — result: `___` errors / `___` warnings (no new findings)
- [ ] Detekt run — `./gradlew :app:detekt` — no findings beyond `app/config/detekt/baseline.xml`
- [ ] **For security rule changes (`firestore.rules`, `storage.rules`)**: emulator suite run —
        `./functions/node_modules/.bin/firebase emulators:exec --only firestore,storage "npm --prefix functions test"` — count: `___` passed / `___` failed
- [ ] **For Cloud Function changes (`functions/`)**: unit tests run (`npm --prefix functions test`) and emulator suite run
- [ ] No direct client writes to `community_tips`, `community_submissions`, or `spot_photos` (all go through the ownership-indexed Cloud Functions)
- [ ] If this PR changes an **existing test assertion**, the reason is stated in the PR description (see `CONTRIBUTING.md`)
- [ ] If this PR changes Firestore schema or rules, database ID consistency (`functions/firestoreDatabase.js` and the app config) was checked
- [ ] No secrets, API keys, signing credentials, or private user data added to the diff

## Verification performed

<!-- What was actually run, on which branch, and the observed result. Do not paste secrets. -->

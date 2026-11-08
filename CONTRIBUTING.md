# Contributing to VividScoutApp

Thanks for working on VividScoutApp. This document records the conventions that
keep this repository reviewable, especially around security-critical and
release-critical code.

## Pull request scope

Three categories of change must always be submitted as **separate pull requests**:

1. **Security rule changes** — anything that edits `firestore.rules`, `storage.rules`,
   or the tests in `functions/firebase-security-rules.test.js`. A security PR must
   contain no unrelated production or UI changes.
2. **Cloud Function changes** — anything under `functions/` that changes callable
   behavior, validation, rate limiting, or deployment configuration.
3. **UI features and Android changes** — Compose screens, ViewModels, repositories,
   and anything under `app/src/main`.

Bundling categories together (as happened in commit `df911d7`, which mixed backend
security changes, lint/CI changes, and new UI features) makes it impossible to review
the security impact of a change in isolation and makes targeted rollback impossible.

## Changing existing test assertions

Existing tests document intended behavior. If a PR changes what an existing test
asserts (rather than only adding new tests), the PR description **must state the
reason** and identify the assertion as one of:

- The expected behavior changed intentionally (e.g. a rule change moved enforcement
  from the client to the server), or
- The test was weak or wrong and now checks more.

Reviewers should reject assertion changes with no stated reason. Never weaken an
assertion to make a test pass — instead, fix the behavior or document the gap.

## Required checks before requesting review

- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug` must pass.
- `./gradlew :app:detekt` must report no new findings beyond `app/config/detekt/baseline.xml`.
- For any change to `firestore.rules`, `storage.rules`, or Cloud Functions:
  run the emulator suite —
  `./functions/node_modules/.bin/firebase emulators:exec --only firestore,storage "npm --prefix functions test"`.
- No direct client writes to protected collections (`community_tips`,
  `community_submissions`, `spot_photos`). Writes go through the ownership-indexed
  Cloud Functions; see `functions/community.js` and `docs/IDENTIFIER_POLICY.md`.

## Further reading

- `docs/BASELINE.md` — the verified test/lint/detekt counts at the last baseline.
- `docs/KNOWN_ISSUES.md` — open issues and their current mitigations.
- `docs/IDENTIFIER_POLICY.md` — the owner-identifier design for community data.

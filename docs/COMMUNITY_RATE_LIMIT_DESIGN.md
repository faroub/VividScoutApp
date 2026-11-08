# Community post rate limiting — design decision

**Status: PROPOSAL — awaits approval. No rules or code were changed.**

Task: limit `community_tips` to 20 and `community_submissions` to 10 creates per UID per
24 hours. The model to follow is `functions/rateLimits.js`.

## Current state

- `rateLimits.js` implements a transactional per-UID counter: `rate_limits/{key}` holds
  `{ count, windowStart }`; `decideRateLimitInTransaction` reads and increments the
  counter **inside the caller's Firestore transaction**, so a slot is consumed exactly
  when the protected operation commits. Used today by `nearbyPlaces` (60/h), the
  `publishSharedTrip` callable (10/h), and the `finalizePhotoUpload` callable.
- `community_tips` and `community_submissions` are created **directly by the Android
  client** through Firestore rules (`CommunityTipRepository`,
  `FirestoreSyncManager.syncDraftSpotToCloud`). They have **no per-UID limit today** —
  only App Check (Console-enforced), rule schema validation, and the atomic vote/report
  guards downstream.

## Options considered

**Rules-side counter.** The client writes its tip in a batch that also increments a
`rate_limits/{uid}_tips` doc, and the create rule verifies via
`get(...)`/`getAfter(...)` that the counter advanced by exactly one. Decisive flaw: the
**window is client-chosen**. The counter's `windowStart` would be written by the client,
and a hostile client simply submits a fresh `windowStart` with every request — unlimited
budget. Rules have no server clock other than `request.time`, and `request.time` cannot
be compared against client-written `windowStart` fields in a way the client can't reset at
will; there is also no rules primitive for "this is the client's Nth write this hour"
without a client-maintained counter. A rules counter therefore cannot be made
un-cheatable for *creates* (the atomic vote pattern works only because the *server* is the
source of truth in the parent document).

**Cloud Function callable.** Add `postCommunityTip` and `submitCommunitySpot` callables
(enforceAppCheck: true) that authenticate, validate, run `decideRateLimitInTransaction`
with a 24-hour window, and write with the Admin SDK. The Firestore rules then deny direct
client `create` on both collections, exactly as `spot_photos` and `shared_trips` already
do for their trusted writers.

## Recommendation

**Recommend the Cloud Function callable.** Rate limiting is only meaningful when the
counter and the clock are both server-owned; a rules counter lets the client define its
own window, so the limit would be cosmetic. The callable reuses the existing
transactional rate-limit helper verbatim (same atomicity guarantees: budget consumed only
by a committed write, retries and replays free), and the repo already has the identical
pattern deployed and emulator-tested for shared-trip publishing and photo finalization,
including the rules-side `allow create: if false` hardening that makes the callable the
only write path.

## After approval — implementation sketch (not started)

1. Extend `rateLimits.js` to accept a window duration (constant today: 1 hour) so the
   24-hour windows for tips (20) and submissions (10) are first-class; keep existing
   1-hour callers unchanged.
2. New `functions/community.js` with the two callables: auth → App Check
   (`enforceAppCheck: true`) → reuse the existing validators (`functions/validation.js`
   if applicable) → transactional limit → Admin SDK write. Server-generate `timestamp`
   and `id`; keep `ownerUid` server-authoritative from `auth.uid`.
3. Tighten `firestore.rules`: `allow create: if false` on both collections with a comment
   pointing at the callables (same as `spot_photos`/`shared_trips`); keep owner-scoped
   update/delete in the rules so existing edit flows work unchanged. Keep `update` rules
   below the per-UID creation limit (they already exist and are owner-checked).
4. Android: route `CommunityTipRepository.postTip` and the draft-sync path through
   Firebase Functions `.httpsCallable(...)`, mapping `resource-exhausted`/`429`-style
   `HttpsError` to a clear user-facing "post limit reached, try again later" message.
   Offline drafts remain queued locally exactly as today; only the network write moves
   behind the callable. Emulator tests: 20th tip succeeds / 21st fails, 10th submission
   succeeds / 11th fails, window reset, and cross-UID independence; plus rules tests that
   direct client creation is now denied.
5. Rollout: ship the callable + app update first; only tighten the rules once the app
   version using direct writes is no longer significant (or coordinate an instant
   cutover, since community writes require network anyway).

## 6. Approval

Per the task instructions, this document stops here. **Awaiting approval** on the
callable-based approach before implementation.

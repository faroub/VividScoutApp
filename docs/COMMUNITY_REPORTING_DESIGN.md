# Community Reporting — Design for Text Tips and Submissions

**Status:** Design only, awaiting approval. No code or rules changes yet.
**Scope:** Public text content that anyone can read but nobody can flag today:
`community_tips` (up to 500 characters, plus status updates) and
`community_submissions` (community-proposed spots). Photo reporting already shipped
(following the F1 decision) and is the model this design mirrors.

## 1. Problem

Photos can be reported through the trusted `reportPhoto` Cloud Function, and the
hourly moderation sweep hides a photo when 3 distinct accounts report it. Public text
has no equivalent: a tip or submission stays visible no matter how many people flag it,
and there is no path to remove abusive or dangerous user-submitted text other than
finding the document in the Firebase Console by hand.

## 2. Governing decisions already in force (from the F1 approval)

- **Who may report:** a signed-in, **non-anonymous** account. Anonymous sign-in is free
  and unverified; allowing it would let one person mint UIDs until the threshold trips.
  Reports from anonymous accounts are rejected with `unauthenticated`.
- **Threshold action:** **hide, do not delete.** A report count alone never makes content
  unreachable. Hiding sets an authoritative `moderationState`; the content is preserved
  for review and can be restored after a mistaken flagging campaign. Permanent removal
  stays a manual-review-only decision.
- **Votes keep any authenticated user** (self-votes refused); reporting carries stricter
  authentication because flag weight is what takes content down.

## 3. Data model

Same shape as the shipped photo reports — reporter identity never leaves the
subcollection and no UID is exposed to any reader.

### 3.1 Report documents

```
community_tips/{tipId}/reports/{reporterUid}
community_submissions/{submissionId}/reports/{reporterUid}
```

- Fields, exactly as photo reports: `{ reporterUid, timestamp }`.
- `reporterUid == request.auth.uid` enforced server-side; the document id is the
  reporter's UID, so one account can report a given document exactly once.
- **`allow read: if false`** — reporter identities are never exposed to anyone, matching
  `spot_photos/{photoId}/reports/{userId}` in `firestore.rules`. The parent document's
  public data (author name, content) stays unchanged; a report never reveals the
  reporter to the author or to other readers.
- `allow update, delete: if false` — a report is immutable once cast.

### 3.2 Parent-document fields

Server-owned, added by the trusted callable exactly as photo reports add them:

- `reportsCount: number` — incremented by exactly 1 per accepted report.
- `moderationState: string | null` — `"hidden"` when the sweep acts; `"removed"` only
  by a human moderator; absent/null otherwise.
- `moderatedAt: number` — set by the sweep.

Legacy documents (written before this feature) have neither field; the first report
**creates** `reportsCount` (CEL guard in §4.2) and the sweep adds the state fields, so
no migration is needed and no existing document changes meaning.

### 3.3 Moderation sweep

A new scheduled Cloud Function, `moderateReportedCommunityContent`, run with the exact
semantics of `moderateReportedPhotos` in `functions/photos.js`:

- Scans `community_tips` and `community_submissions` where `reportsCount >= 3`
  (the production threshold, same as photos) and `moderationState` is unset.
- Sets `moderationState: "hidden"` (+ `moderatedAt`). **Never deletes anything.**
- Skips records already `hidden` or `removed` (idempotent) and counts a failed write as
  `failed` so the next run retries.
- Counts `{ processed, hidden, kept, failed }` and logs only aggregate metrics.

### 3.4 Ownership and UIDs

Reporter UIDs stay in the unreadable subcollection; parent documents already carry no
`ownerUid` (ownership lives in `ownership_index/...`). Reporting adds no new public
field, so the identifier-policy constraints (docs/IDENTIFIER_POLICY.md) are unchanged.

## 4. Server implementation

### 4.1 Callables (new, in `functions/community.js`)

Two callables, following the `castTipVote` / `reportPhotoDocument` precedent:

- `reportCommunityTip(db, reportingUid, data, options)` → exported as the
  `reportCommunityTip` callable (`enforceAppCheck: true`).
- `reportCommunitySubmission(db, reportingUid, data, options)` → exported as the
  `reportCommunitySubmission` callable (`enforceAppCheck: true`).

Each rejects, in this order, inside **one Firestore transaction**:

1. missing/anonymous authentication → `unauthenticated`;
2. malformed payload (exactly one key, `tipId`/`submissionId`, document-id-shaped,
   ≤128 chars) → `invalid-argument`;
3. target document does not exist → `not-found`;
4. caller is the document's **owner** (resolved through `ownerOfExistingDocument`:
   ownership index first, legacy `ownerUid` field second) → `permission-denied`
   ("You cannot report your own tip — edit or delete it instead");
5. document already `hidden` or `removed` → `failed-precondition`;
6. an existing `reports/{callerUid}` document → `already-exists`;
7. the per-UID rate limit — `rate_limits/community_tip_report_{uid}` and
   `rate_limits/community_submission_report_{uid}`, **20 per hour per UID** (the same
   budget photo reports use) → `resource-exhausted`.

Then it writes `reports/{callerUid}` and increments the parent's `reportsCount` by
exactly one, returning `{ tipId|submissionId, reportsCount }`.

### 4.2 Rules (firestore.rules, direct-write parity)

Mirroring the proven `spot_photos` atomic pattern so older app versions keep working
(the callables are the primary path; the rules denial of direct writes is a later
release, exactly as decided for F1):

```cel
// community_tips/{tipId} update: allow ONLY the report-counter increment
// (legacy documents have no reportsCount yet — the first report creates it)
allow update: if isAuthenticated()
  && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['reportsCount'])
  && (resource.data.keys().hasAny(['reportsCount'])
        ? request.resource.data.reportsCount == resource.data.reportsCount + 1
        : request.resource.data.reportsCount == 1)
  && !exists(/databases/$(database)/documents/community_tips/$(tipId)/reports/$(request.auth.uid))
  && existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/reports/$(request.auth.uid))
  // owner may not report their own tip (owners edit/delete instead)
  && request.auth.uid != communityOwnerUid('community_tips', tipId, resource.data);

match /reports/{userId} {
  allow read: if false; // Reporter UIDs are never exposed to anyone
  allow create: if isAuthenticated()
    && userId == request.auth.uid
    && request.resource.data.keys().hasOnly(['reporterUid', 'timestamp'])
    && request.resource.data.reporterUid == request.auth.uid
    && request.resource.data.timestamp is number
    && (resource... /* parent reportsCount is number, +1 exactly, as above */)
    && !exists(/databases/$(database)/documents/community_tips/$(tipId)/reports/$(userId))
    && existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/reports/$(userId));
  allow update, delete: if false;
}
```

The owner-update branch (the existing `isOwner(...)` allow on `community_tips`) must
**not** gain the ability to write `reportsCount`, `moderationState`, or `moderatedAt`:
those keys stay outside every client-side `affectedKeys` allowlist. The sweep and
moderation use the Admin SDK, which bypasses the rules.

### 4.3 What the rules deliberately cannot express

Non-anonymous sign-in and the 20/hour per-UID limit are enforced only by the callables
(the rules language has no auth-provider or rate-limit primitives). That is why the
callables are the primary client path; the direct-write parity exists solely so an
older installed app version does not break before the rules are tightened in a later
release.

## 5. Review, restore, and takedown

- **Who reviews:** a human moderator with Console access (a dedicated moderation UI is
  out of scope here — the data model supports one later: query
  `where reportsCount >= 3` / `where moderationState == 'hidden'`).
- **How a hidden tip is restored:** set `moderationState` back to `null` (and, if
  desired, reset `reportsCount` to 0 and delete the stale `reports/{uid}` documents).
  Because nothing was deleted, the tip, its votes, and its reports all survive intact.
- **Permanent removal:** a moderator sets `moderationState: "removed"` — the state the
  photo path already uses for an irreversible, reviewed takedown. Clients treat both
  `"hidden"` and `"removed"` as not shown.
- **Client display:** hidden/removed documents are filtered where tips and submissions
  are mapped from Firestore, mirroring `SpotPhotoData.isHidden`; the map function must
  read `moderationState` so local Room caches refresh on the next listener snapshot.

## 6. Client changes (Android)

- **`CommunityTipCard.kt`:** add a flag action in the footer row next to the upvote
  button — `Icons.Outlined.Flag` with a "Report" content description, exposed as
  `onReportClick: (tipId: String) -> Unit` (default null = hidden, same pattern as
  `onSpotClick`). The card does not perform the report itself; it invokes the callback.
- **`SpotDetailCommunitySection.kt`:** passes `onReportClick` through to the card list.
- **ViewModel/Repository:** a `reportCommunityTip(tipId)` path in the community sync
  layer calling the callable, mapping `already-exists` → "You already reported this
  tip", `permission-denied` (owner) → "You cannot report your own tip",
  `failed-precondition` → "This tip is no longer available", `resource-exhausted` →
  the rate-limit message — the same mapping shape as `photoReportFailureFor`.
- **Confirmation dialog** before the call (a report is a moderation action, not a tap).
- **Localized strings** in EN/FR/AR following the `location_privacy_note` pattern.
- Sample/demo tips (F4) never show the flag action and are never syncable, so they
  are never reportable.

## 7. Test plan

**Callable tests (node, emulator-backed, in `functions/community.test.js`):**

- one report creates the report document and increments `reportsCount` (creating it on
  a legacy document that lacks the field);
- duplicate report by the same account → `already-exists`, counter unchanged, no
  rate-limit budget consumed;
- two distinct accounts → counter is 2, exact;
- the document's **owner** is rejected with `permission-denied` (both ownership-index
  and legacy-`ownerUid` resolution);
- reporting a `hidden` and a `removed` document → `failed-precondition`;
- missing document → `not-found`; missing/anonymous caller → `unauthenticated`;
- malformed payload (non-object, unexpected field, empty/oversized/malformed id) →
  `invalid-argument`;
- 20th report in the window is accepted, the 21st → `resource-exhausted`; a rejected
  report consumes no budget; the window resets after an hour;
- a report that lands the counter on the threshold is visible to the sweep: the sweep
  marks the document `hidden`, and a subsequent report attempt on it fails with
  `failed-precondition`.

**Sweep tests:** the sweep marks `hidden` at threshold 3, keeps content and the record
intact, is idempotent, skips already-`removed`, retries a failed write, never touches
data beyond Firestore.

**Rules tests (emulator, `functions/firebase-security-rules.test.js`):**

- an authenticated client CAN create its own `reports/{uid}` with exactly
  `{ reporterUid, timestamp }` and the parent counter incremented atomically;
- the same client CANNOT report twice (second create denied);
- another user CANNOT read or write anyone else's report document;
- the owner of the tip CANNOT file a report against it;
- a report that does not increment the parent counter (or increments it twice) is
  denied — the atomic parent/child invariant;
- CANNOT set `moderationState`/`reportsCount` through the owner update branch (the
  owner's edited document must be identical apart from the allowed edit keys).

**Android tests:** the call-site mapping (typed failures), and a `CommunityTipCard`
composable test asserting the flag button exists, invokes the callback with the tip id,
and is absent for sample tips.

## 8. Open questions for approval

1. Is 3 the right threshold for text tips and submissions (same as photos), or should
   text wait for 5 given it is less visually damaging and the content is short?
2. Should the two callables be separate (`reportCommunityTip`,
   `reportCommunitySubmission`) or one callable taking a collection discriminator
   (fewer entry points, one more validation dimension)?
3. Rate-limit budget: 20 reports/hour per collection per UID (photo-report parity), or
   one shared budget across all report kinds?
4. Should owners be blocked from reporting their own content (as designed above, matching
   the self-vote refusal), or allowed on the grounds that self-reporting is harmless?

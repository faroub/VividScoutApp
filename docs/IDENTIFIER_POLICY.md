# Identifier Policy — `ownerUid` exposure in public Firestore documents

**Status: PROPOSAL — awaits approval. No rules or code were changed for this document.**

Scope: Firestore documents, Firestore/Storage rules, Cloud Functions, and the Android app.
Authored as part of the security review batch; this document only analyzes and recommends.

## 1. Problem statement

`community_tips`, `community_submissions`, and `spot_photos` are **world-readable**
(`allow read: if true`) and each document stores the contributor's Firebase Authentication
UID in the `ownerUid` field (or, for Storage, inside the path
`spot_photos/{uid}/{spotId}/{filename}`). Firebase UIDs are stable per-user identifiers.
They do not contain a name or email, but they are linkable: anyone who can read those
collections can join one person's contributions across collections and build a
contribution profile (what you posted, where you were, with what photos). Any single
disclosure that connects a UID to a real identity de-anonymizes all of the linked rows.

The exposure is **not** currently symmetric across features:

| Location | `ownerUid` present | Publicly readable | Used for logic | Used for display |
|---|---|---|---|---|
| `community_submissions/{id}.ownerUid` | yes | yes (`allow read: if true`) | rules: create/update ownership (`request.auth.uid == ownerUid`) | no |
| `community_tips/{id}.ownerUid` | yes | yes | rules: ownership edits + self-upvote prevention | no |
| `community_tips/{id}/votes/{uid}` (doc id + `uid` field) | yes | own doc only | rules: per-user vote uniqueness | no |
| `spot_photos/{id}.ownerUid` | yes | yes | rules: none (creation is server-side); Android `SpotPhotoData.ownerUid` is parsed but **not** used by any ViewModel or screen | no |
| `spot_photos/{id}/reports/{uid}` + `reporterUid` | yes | `allow read: if false` | rules: per-user report uniqueness | no |
| `users/{uid}/private_spot_notes/{id}.ownerUid` | yes | owner only (path-scoped) | rules: ownership | no |
| `shared_trips/{id}` | **no** (`buildSharedTripDocument` has no ownerUid) | public get by id | n/a | no |
| `shared_trip_publish_requests/{id}.ownerUid` | yes | no (deny all) | server: idempotent replay ownership check | no |
| Storage object path `spot_photos/{uid}/{spotId}/{filename}` + `ownerUid` custom metadata | yes (path + metadata) | objects are client-readable per `storage.rules` | rules: path==metadata match; orphan-reaper ownership check | the path is visible in download URLs |
| `rate_limits/{key}` (key embeds uid) | yes (in id) | no rules match → default deny | functions: per-UID rate limiting | no |

Display names are a **separate** facility: `community_tips.authorName` and
`community_submissions.contributor` are user-chosen strings, not UIDs. **No screen in the
app displays `ownerUid`** (checked `app/src/main/java/com/example/ui/**`).

Summary of the real risk: cross-collection linkage of one person's contribution history for any observer who
can read the public collections, plus UID leakage through Storage paths in public photo
download URLs.

## 2. Option (a) — per-collection pseudonymous IDs (HMAC-SHA256, server-held secret)

### Design

- The Firebase project holds a secret (Firebase Secret Manager), available only to Cloud
  Functions runtime.
- Cloud Functions compute `publicId = HMAC_SHA256(secret, uid)` (or
  `HMAC_SHA256(secret, uid + collectionId)` for per-collection unlinkability) and write
  that instead of the raw `uid` into publicly readable documents.
- Clients never see the secret, so they cannot map another user's UID to a public ID.

### Consequences

- **Editing / deleting.** Firestore rules cannot compute HMAC (the rules CEL has
  `hashing.sha256`/`md5` but **no HMAC**), so ownership can no longer be validated as
  `request.auth.uid == resource.data.ownerUid`. All edits and deletes must move into
  Authenticated callable Cloud Functions (`updateOwnTip`, `deleteOwnTip`, …) that
  recompute the HMAC server-side. Rules lose direct enforcement; the functions become the
  trusted boundary. This is a significant rule/function re-architecture, plus client
  repository changes for every edit/delete path.
- **Upvote / report logic.** The self-upvote guard
  (`request.auth.uid != resource.data.ownerUid`) cannot be expressed against a hashed
  field either. The atomic parent-update rule would have to compare against a
  server-maintained mirror (e.g. a rule-private `tip_ownership/{tipId}` document written
  by the function), or the whole vote flow must become a callable — losing the current
  rules-enforced atomicity (pre-write `!exists` + post-write `existsAfter`).
- **Storage paths.** Registered photos live at `spot_photos/{uid}/…`, and public download
  URLs embed that path. Keeping raw UIDs there makes any hashing of Firestore fields
  pointless: the UID is still public per photo. Option (a) therefore also requires
  re-pathing Storage to `spot_photos/{publicId}/…` with a server-side ownership index,
  which complicates the already-immutability-locked photo pipeline (registered objects
  are immutable by Prompt 2's rules) — a new path means a migration of existing objects.
- **Rate limiting** keeps working unchanged (server-side, uses the raw `uid`).
- **Migration**: every existing public document needs a backfill that replaces `ownerUid`
  with `publicId`, plus an ownership index for the rules to consult. The `users/{uid}/…`
  private paths can stay raw (they are owner-scoped, not public).

### When it helps

It prevents an *outside* party who happens to know a UID from joining it to public
content, and it removes raw UIDs from documents. It does **not** remove cross-collection
correlation itself: the same person still produces the same `publicId` across collections,
so a contribution profile can still be assembled — just keyed by an opaque ID.

## 3. Option (b) — keep `ownerUid`, restrict reads to the owner

### Design

- Keep the raw UID in documents, but deny public reads of anything carrying identity:
  `allow read: if isOwner(resource.data.ownerUid)` on
  `community_submissions` / `community_tips` / `spot_photos`, or move identity into
  per-user subcollections.

### Consequences

- **It breaks the product.** These collections are community content: the community feed,
  shared photo galleries, and tip lists are built on public reads. Owner-only reads mean
  nobody can see anyone else's contribution; community features stop functioning.
- **Editing / deleting** keep working exactly as today (rules compare
  `request.auth.uid`), and **upvote/report atomicity** is preserved as-is — this is the
  chief advantage of (b): zero change to the rules logic that currently works.
- It does not address the stated linkage risk at all for the collections where reads must
  stay public.

### When it helps

For collections that genuinely need not be public — none of the three here qualify. Where
owner-only access is the correct behavior, it is already in place
(`users/{uid}/private_spot_notes`, vote/report subcollections).

## 4. Recommendation

**Neither (a) nor (b) as stated; recommend a focused variant of (a) scoped to what
actually leaks:**

1. **Remove `ownerUid` from the publicly readable documents** of
   `community_submissions`, `community_tips`, and `spot_photos`. The owner already has
   stable, user-chosen display identity in `authorName` / `contributor`; no screen uses
   `ownerUid`.
2. **Keep ownership authority in the rules** by having the trusted Cloud Functions write
   a rule-private ownership index (e.g. `ownership_index/{collection}/{docId}` with
   `allow read, write: if false`). Rules `get()` can read documents the client cannot
   read directly, so `isOwner(get(...).data.ownerUid)` keeps edit/delete/anti-self-upvote
   enforcement in the rules layer — no callable migration, no rule logic loss.
3. **Re-path Storage last and separately.** `spot_photos/{uid}/…` paths leak the UID in
   public download URLs. Switching to `spot_photos/{publicId}/…` with the same
   ownership index removes that leak, but it must be paired with the existing registered
   photo-immutability and orphan-reaper code (path format is embedded in `storage.rules`,
   `functions/photos.js` validation, and the reaper). This is a follow-up task with its
   own migration of previously published objects.
4. Keep raw `uid` wherever access is already owner-scoped: `users/{uid}/…`,
   vote/report subcollections, `shared_trip_publish_requests`, `rate_limits`.
5. Per-collection HMAC public IDs make sense only if we later want durable, unlinkable
   **author handles** (same author recognizable across collections without a UID) — that
   is a product decision, not a security fix; note it as an open question.

Net: the linkage risk is caused by putting a stable identity into world-readable
documents; the minimal fix removes it from those documents while keeping all ownership
logic inside the rules via a server-written private index. Storage path rework is the
remaining hard part and should be tracked separately.

## 5. Migration & rollout notes (for the approved step)

- Backfill for existing documents: replace `ownerUid` with `(collection, docId) → uid`
  index docs written by an Admin SDK script (dry-run first, idempotent — same pattern as
  the private-note migration script).
- Rules must ship together with the function change that starts writing the index;
  otherwise `isOwner(get(...))` fails closed (get() on a missing path → denied), which is
  the safe direction.
- No client change needed for read paths; write paths keep sending documents without
  `ownerUid` being required client-side (functions add the index server-side).

## 6. Approval

Per the task instructions, this document stops here. **Awaiting approval** on the
recommendation above before any rules, functions, or client changes.

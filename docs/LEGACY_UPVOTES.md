# Legacy Community Tip Upvote Handling Strategy

## Overview & Current State Audit

Existing community tips in Firestore and on local devices contain historical `upvotes` counts (e.g., pre-seeded tips with `upvotes = 7` or `upvotes = 24`) that are not backed by per-user subcollection vote records (`community_tips/{tipId}/votes/{userId}`).

Under the newly implemented per-user vote tracking rules:
- `firestore.rules` requires `existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))` on every upvote update.
- `CommunitySyncWriter.incrementTipUpvoteInCloud()` (reached through `FirestoreSyncManager`) calls the trusted `castTipVote` Cloud Function (F1): inside one server-side Firestore transaction it requires a fresh vote document together with the exact +1 `upvotes` increment, refuses the tip owner's self-vote, and enforces the per-UID vote rate limit.

---

## 1. Audit: All Locations Reading or Displaying `tip.upvotes`

The following locations in the codebase read, write, or render `tip.upvotes`:

1. **`app/src/main/java/com/example/ui/components/CommunityTipCard.kt`** (UI Display):
   - Renders `tip.totalUpvotes` (sum of `legacyUpvotes` and recorded `upvotes`).

2. **`app/src/main/java/com/example/data/repository/CommunityTipRepository.kt`**:
   - Initializes newly created local tips with `upvotes = 0` and `legacyUpvotes = 0`.
   - `upvoteTip()` checks `isSyncedToCloud` and local `TipVoteDao` before triggering `FirestoreSyncManager.incrementTipUpvoteInCloud()`.

3. **`app/src/main/java/com/example/data/sync/CommunitySyncWriter.kt`** (Cloud Sync, extracted from `FirestoreSyncManager`):
   - Does NOT send `upvotes` when posting new tips through the `postCommunityTip` callable (`postCommunityTipToCloud`): the counter is server-owned and initialized to 0 server-side. It also refuses to post any tip with `legacyUpvotes !== 0` — legacy counts are introduced only by the migration script.
   - `incrementTipUpvoteInCloud()` delegates to the `castTipVote` callable, whose transaction writes the vote document and increments `upvotes` by exactly one while preserving `legacyUpvotes` (documents written directly by the migration script keep `legacyUpvotes`).
   - Parses `legacyUpvotes` and `upvotes` from downloaded remote documents in the shared remote-content download path (`syncRemoteContentDown` in `FirestoreSyncManager`).

4. **`firestore.rules`** (Security Rules):
   - Direct client creation of community-tips documents is denied entirely
     (`allow create: if false`): `postCommunityTipDocument` was never a client write,
     and after the trusted-callable change (F3) the rules cannot be bypassed by
     constructing a "create" with a server-owned counter. The client-controlled-field
     validator (`isValidTipData`) still constrains the owner-edit branch, where
     `upvotes` and `legacyUpvotes` must remain byte-for-byte unchanged.
   - The trusted `postCommunityTip` callable is the only creation path, and it rejects
     `legacyUpvotes !== 0` with `invalid-argument` on both create and replay (F3):
     legacy counts can only ever come from `scripts/migrate_legacy_upvotes.js`,
     never from a client payload. It also sets `upvotes` to 0 server-side.
   - Upvote increments (the direct-write atomic path older app versions still use)
     require `request.resource.data.upvotes == resource.data.upvotes + 1` together
     with `existsAfter(...)` vote-document creation; the primary path is now the
     `castTipVote` callable (F1), which enforces the same invariant server-side plus
     the owner self-vote refusal and the per-UID vote rate limit.

---

## 2. Proposals: Option A vs. Option B

### Option A (Adopted): Separate `legacyUpvotes` Field + Computed Total
- **Description**: Keep historical unbacked upvote counts in an immutable field named `legacyUpvotes`. Store new recorded votes in `upvotes` (starting at 0 for new tips). The total displayed upvote count is calculated dynamically as `totalUpvotes = legacyUpvotes + upvotes`.
- **Why Option A is Recommended**:
  - **Data Integrity**: 100% compliant with strict per-user vote rules without fabricating fake user identities.
  - **Zero Cost / Zero Risk**: Does not require running administrative write scripts or backfilling Firestore subcollections.
  - **Backward Compatible**: Existing historical tips preserve their original community recognition.

### Option B (Not Recommended): One-Time Backfill Migration
- **Description**: Run an administrative script to create synthetic vote documents (`votes/legacy_voter_{i}`) for all historical upvote counts.
- **Why Option B is NOT Recommended**:
  - **Pollutes Vote Attribution**: Generates synthetic, fake user IDs (`legacy_voter_1`, etc.) in subcollections that do not correspond to real authenticated Firebase users.
  - **Unnecessary Cost & Transaction Overhead**: Incurs hundreds of administrative batch write operations in Firestore.
  - **Potential Auth Conflicts**: Synthetic UIDs could conflict with or complicate future user data analytics.

---

## 3. Implemented Code Architecture for Option A

### A. Security Rules (`firestore.rules`)
```javascript
     match /community_tips/{tipId} {
       allow read: if true;
       // Direct client creation is DENIED: the trusted postCommunityTip callable is
       // the only creation path (it rejects legacyUpvotes !== 0 and sets upvotes = 0
       // server-side), and its Admin SDK writes bypass these rules.
       allow create: if false;

       allow update: if isAuthenticated() && (
         (request.resource.data.diff(resource.data).affectedKeys().hasOnly(['upvotes'])
          && request.resource.data.upvotes == resource.data.upvotes + 1
          && request.resource.data.legacyUpvotes == resource.data.legacyUpvotes
          && request.auth.uid != communityOwnerUid('community_tips', tipId, resource.data)
          && !exists(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))
          && existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))) ||
         (isOwner(communityOwnerUid('community_tips', tipId, resource.data))
          && request.resource.data.ownerUid == request.auth.uid
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly([
               'id', 'ownerUid', 'spotId', 'spotTitle', 'authorName', 'content',
               'timestamp', 'isStatusUpdate', 'statusCategory', 'legacyUpvotes', 'upvotes'
             ])
          && isValidTipData(request.resource.data)
          && request.resource.data.upvotes == resource.data.upvotes
          && request.resource.data.legacyUpvotes == resource.data.legacyUpvotes)
       );
```

### B. Kotlin Model (`app/src/main/java/com/example/data/local/CommunityTipEntity.kt`)
```kotlin
 data class CommunityTipEntity(
     @PrimaryKey
     val id: String,
     val spotId: String,
     val spotTitle: String,
     val authorName: String,
     val content: String,
     val timestamp: Long,
     val isStatusUpdate: Boolean = false,
     val statusCategory: String = "INSIGHT",
     val legacyUpvotes: Int = 0,
     val upvotes: Int = 0,
     val peerNodeId: String = "local-device",
     val isLocallyAuthored: Boolean = false,
     val isSyncedToCloud: Boolean = false,
     val isMigrated: Boolean = true
 ) {
     val totalUpvotes: Int
         get() = legacyUpvotes + upvotes
```

### C. Cloud Sync Manager (`app/src/main/java/com/example/data/sync/FirestoreSyncManager.kt`)
```kotlin
 // In postCommunityTipToCloud:
 val tipData = hashMapOf(
     "id" to tip.id,
     "ownerUid" to currentUid,
     "spotId" to tip.spotId,
     "spotTitle" to tip.spotTitle,
     "authorName" to tip.authorName,
     "content" to tip.content,
     "timestamp" to tip.timestamp,
     "isStatusUpdate" to tip.isStatusUpdate,
     "statusCategory" to tip.statusCategory,
     "legacyUpvotes" to tip.legacyUpvotes,
     "upvotes" to 0
 )

 // In syncRemoteContentDown:
 val hasLegacyUpvotesField = doc.contains("legacyUpvotes")
 if (hasLegacyUpvotesField) {
     legacyVotes = (doc.getLong("legacyUpvotes") ?: 0L).toInt()
     recordedVotes = (doc.getLong("upvotes") ?: 0L).toInt()
     isMigrated = true
 } else {
     val rawUpvotes = (doc.getLong("upvotes") ?: 0L).toInt()
     legacyVotes = rawUpvotes
     recordedVotes = 0
     isMigrated = false
     Log.w(TAG, "Downloaded unmigrated tip document '${id}': missing 'legacyUpvotes' field.")
 }
```

### D. UI Card Component (`app/src/main/java/com/example/ui/components/CommunityTipCard.kt`)
```kotlin
 if (tip.totalUpvotes > 0) {
     Spacer(modifier = Modifier.width(4.dp))
     Text(
         text = "${tip.totalUpvotes}",
         style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
         color = PrimaryContainer
     )
 }
```

---

## Conclusion & Next Steps

### Decision
**Option A adopted.** The application codebase and database schema (Room v12, `MIGRATION_11_12`) explicitly separate historical legacy upvotes (`legacyUpvotes`) from per-user recorded votes (`upvotes`).

### Known Gap / Open Issue
- **Pre-Existing Firestore Documents**: Historical documents in the cloud Firestore database created prior to Option A adoption still store their legacy counts in the `upvotes` field rather than `legacyUpvotes`.
- When clients pull documents without `legacyUpvotes`, `FirestoreSyncManager.syncRemoteContentDown()` gracefully falls back by mapping `doc.upvotes` to `legacyUpvotes` locally. Moving the remote Firestore field from `upvotes` to `legacyUpvotes` on cloud documents is tracked in the Migration Runbook below.

---

## 4. Migration Runbook

This runbook outlines the operational steps to execute the Option A database migration script (`scripts/migrate_legacy_upvotes.js`) against your live Cloud Firestore instance to backfill legacy documents missing the `legacyUpvotes` field.

### Preconditions

Do not start the Execution Steps until every box is checked.

- [ ] Firestore export taken and verified (`gcloud firestore export gs://[YOUR_BUCKET_NAME]`)
- [ ] App version containing the Option A client logic (Room version 12) and the `isMigrated` gating released to users [App Version Number: ____]
- [ ] Firestore rules containing the `legacyUpvotes` allowances deployed [Deployment Date: ____]
- [ ] Option A approval recorded in section 4 [Approver: Farid Oubbati, Date: 01.11.2026]

### Execution Steps
1. **Configure Service Account Credentials**:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account.json"
   ```
2. **Execute Dry-Run Scan**:
   ```bash
   node scripts/migrate_legacy_upvotes.js --dry-run
   ```
   *(Note: When credentials are provided, the dry run performs a read-only scan against live Firestore documents using cursor pagination with page size 200, outputting exact document counts without executing writes).*
3. **Review Dry-Run Output**: Review terminal output for total evaluated documents, documents needing migration, documents already migrated, and inspect the first 20 planned updates.
4. **Execute Migration**:
   ```bash
   node scripts/migrate_legacy_upvotes.js --execute
   ```
5. **Verify Document Sample in Firebase Console**: Open Firebase Console > Firestore > `community_tips` collection, inspect 3–5 updated documents, and confirm:
   - `legacyUpvotes` equals the original historical count.
   - `upvotes` equals `0`.
6. **Record Migrated Document Count**: Record the exact count of modified documents reported by the script for compliance tracking.

### Rollback Strategy
- **Restoration**: If migration issues or unintended data changes occur, restore the database from the precondition export using `gcloud firestore import gs://[YOUR_BUCKET_NAME]/[EXPORT_PREFIX]`.
- **Client App Compatibility**: If a rollback occurs, the Android client app **continues to function normally without crashing or data loss**. `FirestoreSyncManager.syncRemoteContentDown()` handles both migrated and unmigrated document schemas gracefully by treating missing `legacyUpvotes` fields as legacy counts on-the-fly.

### Monitoring: What to Watch
- **Telemetry Warnings in Logcat**: Monitor `Logcat` logs filtering for `FirestoreSyncManager` tag.
- Prior to migration, unmigrated documents log warnings:
  > `Downloaded unmigrated tip document 'tip_123': missing 'legacyUpvotes' field. Treating count as legacy count until migration script is executed.`
- **Success Criteria**: Following migration execution and app refresh, the unmigrated warning count from `syncRemoteContentDown()` should fall to **zero**.

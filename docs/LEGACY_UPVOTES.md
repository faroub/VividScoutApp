# Legacy Community Tip Upvote Handling Strategy

## Overview & Current State Audit

Existing community tips in Firestore and on local devices contain historical `upvotes` counts (e.g., pre-seeded tips with `upvotes = 7` or `upvotes = 24`) that are not backed by per-user subcollection vote records (`community_tips/{tipId}/votes/{userId}`).

Under the newly implemented per-user vote tracking rules:
- `firestore.rules` requires `existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))` on every upvote update.
- `FirestoreSyncManager.incrementTipUpvoteInCloud()` executes a transaction requiring a vote document write alongside the upvote increment.

---

## 1. Audit: All Locations Reading or Displaying `tip.upvotes`

The following locations in the codebase read, write, or render `tip.upvotes`:

1. **`app/src/main/java/com/example/ui/components/CommunityTipCard.kt`** (UI Display):
   - Renders `tip.totalUpvotes` (sum of `legacyUpvotes` and recorded `upvotes`).

2. **`app/src/main/java/com/example/data/repository/CommunityTipRepository.kt`**:
   - Initializes newly created local tips with `upvotes = 0` and `legacyUpvotes = 0`.
   - `upvoteTip()` checks `isSyncedToCloud` and local `TipVoteDao` before triggering `FirestoreSyncManager.incrementTipUpvoteInCloud()`.

3. **`app/src/main/java/com/example/data/sync/FirestoreSyncManager.kt`** (Cloud Sync):
   - Sets `"upvotes" to 0` when posting new tips to Firestore (`postCommunityTipToCloud`).
   - Transaction in `incrementTipUpvoteInCloud()` reads `currentUpvotes` and updates `"upvotes" to currentUpvotes + 1` while preserving `legacyUpvotes`.
   - Parses `legacyUpvotes` and `upvotes` from downloaded remote documents in `syncRemoteContentDown()`.

4. **`firestore.rules`** (Security Rules):
   - Enforces `request.resource.data.upvotes == 0` on document creation and allows `legacyUpvotes`.
   - Requires `request.resource.data.upvotes == resource.data.upvotes + 1` and `existsAfter(...)` vote doc creation on upvoting.

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
       allow create: if isAuthenticated()
                     && request.resource.data.keys().hasOnly([
                          'id', 'ownerUid', 'spotId', 'spotTitle', 'authorName', 'content',
                          'timestamp', 'isStatusUpdate', 'statusCategory', 'legacyUpvotes', 'upvotes'
                        ])
                     && request.resource.data.ownerUid == request.auth.uid
                     && request.resource.data.legacyUpvotes is number
                     && request.resource.data.upvotes == 0;

       allow update: if isAuthenticated() && (
         (request.resource.data.diff(resource.data).affectedKeys().hasOnly(['upvotes'])
          && request.resource.data.upvotes == resource.data.upvotes + 1
          && request.resource.data.legacyUpvotes == resource.data.legacyUpvotes
          && existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))) ||
         (isOwner(resource.data.ownerUid)
          && request.resource.data.ownerUid == request.auth.uid
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly([
               'id', 'ownerUid', 'spotId', 'spotTitle', 'authorName', 'content',
               'timestamp', 'isStatusUpdate', 'statusCategory', 'legacyUpvotes'
             ]))
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
     val isSyncedToCloud: Boolean = false
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
 } else {
     val rawUpvotes = (doc.getLong("upvotes") ?: 0L).toInt()
     legacyVotes = rawUpvotes
     recordedVotes = 0
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
**Option A adopted.** The application codebase and database schema (Room v10, `MIGRATION_9_10`) explicitly separate historical legacy upvotes (`legacyUpvotes`) from per-user recorded votes (`upvotes`).

### Known Gap / Open Issue
- **Pre-Existing Firestore Documents**: Historical documents in the cloud Firestore database created prior to Option A adoption still store their legacy counts in the `upvotes` field rather than `legacyUpvotes`.
- When clients pull documents without `legacyUpvotes`, `FirestoreSyncManager.syncRemoteContentDown()` gracefully falls back by mapping `doc.upvotes` to `legacyUpvotes` locally. Moving the remote Firestore field from `upvotes` to `legacyUpvotes` on cloud documents is tracked in the Migration Runbook below.

---

## 4. Migration Runbook

This runbook outlines the operational steps to execute the Option A database migration script (`scripts/migrate_legacy_upvotes.js`) against your live Cloud Firestore instance to backfill legacy documents missing the `legacyUpvotes` field.

### Preconditions
1. **Firestore Backup Taken**: A full export of the Cloud Firestore database has been created and verified using `gcloud firestore export gs://[YOUR_BUCKET_NAME]` or the Firebase CLI.
2. **Released App Version**: The app version containing Option A client logic (Room v10, `legacyUpvotes` parsing, `totalUpvotes` UI rendering) has been released to active users.
3. **Firestore Security Rules Deployed**: Updated security rules in `firestore.rules` (allowing `legacyUpvotes` on `create` and `update`) have been deployed to production in the Firebase Console.
4. Approval:
   - Decision: Option A (approved)
   - Approver: Farid Oubbati
   - Date: 01.11.2026
   - Scope: the approach in sections 1 to 3 only. Any change to the approach needs a new approval.

### Execution Steps
1. **Configure Service Account Credentials**:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account.json"
   ```
2. **Execute Dry-Run Simulation**:
   ```bash
   node scripts/migrate_legacy_upvotes.js --dry-run
   ```
3. **Review Dry-Run Output**: Verify the count of unmigrated documents versus already-migrated documents displayed in the terminal console summary.
4. **Execute Migration**:
   ```bash
   node scripts/migrate_legacy_upvotes.js --execute
   ```
5. **Verify Document Sample in Firebase Console**: Open Firebase Console > Firestore > `community_tips` collection, inspect 3–5 updated documents, and confirm:
   - `legacyUpvotes` equals the original historical count.
   - `upvotes` equals `0`.
6. **Record Migrated Document Count**: Log the exact count of modified documents reported by the migration script for compliance tracking.

### Rollback Strategy
- **Restoration**: If migration issues or unintended data changes occur, restore the database from the precondition export using `gcloud firestore import gs://[YOUR_BUCKET_NAME]/[EXPORT_PREFIX]`.
- **Client App Compatibility**: If a rollback occurs, the Android client app **continues to function normally without crashing or data loss**. `FirestoreSyncManager.syncRemoteContentDown()` handles both migrated and unmigrated document schemas gracefully by treating missing `legacyUpvotes` fields as legacy counts on-the-fly.

### Monitoring: What to Watch
- **Telemetry Warnings in Logcat**: Monitor `Logcat` logs filtering for `FirestoreSyncManager` tag.
- Prior to migration, unmigrated documents log warnings:
  > `Downloaded unmigrated tip document 'tip_123': missing 'legacyUpvotes' field. Treating count as legacy count until migration script is executed.`
- **Success Criteria**: Following migration execution and app refresh, the unmigrated warning count from `syncRemoteContentDown()` should fall to **zero**.



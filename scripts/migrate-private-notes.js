/**
 * One-Time Migration Script: Move private scout notes out of public community submissions.
 *
 * Background: FirestoreSyncManager.syncDraftSpotToCloud() used to write each scout's
 * private note ("userNotes") into the publicly readable community_submissions document.
 * Firestore has no field-level access control, so anyone could read those private notes.
 * The client now writes notes only under the owner-scoped path
 * users/{uid}/private_spot_notes/{spotId}, and firestore.rules forbids "userNotes" in
 * public submissions entirely. This script moves the legacy field out of the public
 * documents into the private collection, then strips it from the public document.
 *
 * Safety properties:
 * - Dry-run is the DEFAULT: no writes happen unless --execute is passed. A dry run
 *   reads production data but produces only a plan and counts.
 * - Paginated with an explicit document-id cursor, so large collections are enumerated
 *   safely and the script is resumable (already-migrated documents are skipped).
 * - Idempotent: re-running after a completed migration performs no writes.
 * - The note is copied to the owner's OWN private path (read from the submission's
 *   "ownerUid"); a note is never moved into another user's account.
 * - The public "userNotes" field is removed only AFTER the private write is verified
 *   (both happen inside one Firestore transaction).
 * - An existing private note is never overwritten: identical content is treated as an
 *   already-completed copy (field stripped), different content is logged as a conflict
 *   and left in place for manual review.
 * - Submissions with a missing/invalid ownerUid are logged for manual review and left
 *   untouched — the note is never destroyed or orphaned.
 * - Logs contain document IDs and counts only. Note content is never printed.
 *
 * Usage:
 *   Dry-run (default, read-only):
 *     node scripts/migrate-private-notes.js
 *     node scripts/migrate-private-notes.js --dry-run
 *
 *   Live execution (requires service-account credentials via GOOGLE_APPLICATION_CREDENTIALS
 *   or gcloud application-default login, plus --execute):
 *     node scripts/migrate-private-notes.js --execute
 *
 * The script uses the Firebase Admin SDK, which bypasses client security rules by design.
 * Never run --execute against production without reviewing the dry-run plan first.
 */

let admin;
try {
  admin = require('firebase-admin');
} catch (e) {
  admin = null;
}

const PAGE_SIZE = 200;
const SUBMISSIONS_COLLECTION = 'community_submissions';
const USERS_COLLECTION = 'users';
const PRIVATE_NOTES_COLLECTION = 'private_spot_notes';

const print = (message) => console.log(message);

/**
 * Classifies and (unless dry-run) migrates ONE submission document.
 *
 * Returns a result object describing the planned/performed action:
 *   - no-note:          document has no (or only blank) userNotes — nothing to do
 *   - migrated:         note copied to the owner's private document and the public
 *                        field removed (in dry-run: the same would have happened)
 *   - conflict:         the owner already has a DIFFERENT private note for this spot;
 *                        the public field is left in place for manual review
 *   - manual-review:    ownerUid is missing or invalid; the field is left in place
 *   - error:            an exception occurred; the caller records it for retry
 */
async function migrateSubmission(db, submissionSnap, options = {}) {
  const { dryRun = true, fieldDelete = null } = options;
  const data = submissionSnap.data() || {};
  const rawNotes = data.userNotes;

  if (typeof rawNotes !== 'string' || rawNotes.trim() === '') {
    return { status: 'no-note', submissionId: submissionSnap.id };
  }

  const ownerUid = data.ownerUid;
  if (typeof ownerUid !== 'string' || ownerUid.trim() === '') {
    // Without a verifiable owner the note cannot be moved into ANY account.
    return { status: 'manual-review', submissionId: submissionSnap.id, reason: 'missing-ownerUid' };
  }

  const privateRef = db.collection(USERS_COLLECTION)
    .doc(ownerUid)
    .collection(PRIVATE_NOTES_COLLECTION)
    .doc(submissionSnap.id);

  if (dryRun) {
    // Dry-run must not write, but still inspects the private document to classify
    // conflicts, so the plan it prints matches what --execute would do.
    const existingSnap = await privateRef.get().catch(() => ({ exists: () => false, get: () => undefined }));
    if (existingSnap.exists && existingSnap.exists()) {
      const existingNotes = existingSnap.get('notes');
      if (typeof existingNotes === 'string' && existingNotes.trim() !== '' && existingNotes !== rawNotes) {
        return { status: 'conflict', submissionId: submissionSnap.id, ownerUid };
      }
    }
    return { status: 'migrated', submissionId: submissionSnap.id, ownerUid, dryRun: true };
  }

  // Live execution: everything happens inside one transaction, so the private write
  // and the public-field removal commit atomically (or not at all).
  return db.runTransaction(async (transaction) => {
    const freshSnap = await transaction.get(submissionSnap.ref);
    if (!freshSnap.exists) {
      return { status: 'no-note', submissionId: submissionSnap.id };
    }
    const freshNotes = freshSnap.get('userNotes');
    if (typeof freshNotes !== 'string' || freshNotes.trim() === '') {
      return { status: 'no-note', submissionId: submissionSnap.id };
    }

    const freshOwner = freshSnap.get('ownerUid');
    if (typeof freshOwner !== 'string' || freshOwner.trim() === '') {
      return { status: 'manual-review', submissionId: submissionSnap.id, reason: 'missing-ownerUid' };
    }

    const freshPrivateRef = db.collection(USERS_COLLECTION)
      .doc(freshOwner)
      .collection(PRIVATE_NOTES_COLLECTION)
      .doc(submissionSnap.id);
    const existingPrivateSnap = await transaction.get(freshPrivateRef);
    if (existingPrivateSnap.exists) {
      const existingNotes = existingPrivateSnap.get('notes');
      if (typeof existingNotes === 'string' && existingNotes.trim() !== '' && existingNotes !== freshNotes) {
        return { status: 'conflict', submissionId: submissionSnap.id, ownerUid: freshOwner };
      }
    }

    // 1. Copy the note into the owner's private document (never another account —
    //    freshOwner comes from the submission itself).
    transaction.set(freshPrivateRef, {
      spotId: submissionSnap.id,
      ownerUid: freshOwner,
      notes: freshNotes,
      updatedAt: Date.now()
    });

    // 2. Remove the private field from the public document, verified to exist above.
    if (!fieldDelete) {
      throw new Error('fieldDelete sentinel factory is required for live execution');
    }
    transaction.update(submissionSnap.ref, { userNotes: fieldDelete() });

    return { status: 'migrated', submissionId: submissionSnap.id, ownerUid: freshOwner };
  }).catch((error) => ({ status: 'error', submissionId: submissionSnap.id, error: error.message }));
}

/**
 * Runs the full migration over community_submissions with document-id pagination,
 * accumulating a summary. Pagination is by document-id cursor (startAfter the last
 * document id of the previous page), which stays correct when documents are deleted
 * or modified while the migration runs.
 */
async function runMigration(db, options = {}) {
  const { dryRun = true, fieldDelete = null, pageSize = PAGE_SIZE, log = print } = options;

  const summary = {
    mode: dryRun ? 'dry-run' : 'execute',
    scanned: 0,
    migrated: 0,
    noNote: 0,
    conflicts: 0,
    manualReview: 0,
    errors: 0
  };
  const conflictIds = [];
  const manualReviewIds = [];
  const errorIds = [];

  let lastDocId = null;
  let hasMore = true;

  while (hasMore) {
    let query = db.collection(SUBMISSIONS_COLLECTION)
      .orderBy('__name__')
      .limit(pageSize);
    if (lastDocId !== null) {
      query = query.startAfter(lastDocId);
    }

    const page = await query.get();
    if (page.empty) {
      hasMore = false;
      break;
    }

    for (const submissionSnap of page.docs) {
      summary.scanned += 1;
      let result;
      try {
        result = await migrateSubmission(db, submissionSnap, { dryRun, fieldDelete });
      } catch (error) {
        result = { status: 'error', submissionId: submissionSnap.id, error: error.message };
      }

      switch (result.status) {
        case 'migrated':
          summary.migrated += 1;
          break;
        case 'no-note':
          summary.noNote += 1;
          break;
        case 'conflict':
          summary.conflicts += 1;
          conflictIds.push(result.submissionId);
          break;
        case 'manual-review':
          summary.manualReview += 1;
          manualReviewIds.push(result.submissionId);
          break;
        default:
          summary.errors += 1;
          errorIds.push(result.submissionId);
          log(`[migrate-private-notes] ERROR on submission '${result.submissionId}': ${result.error}` +
            (result.status === 'error' ? '' : ` (unexpected status '${result.status}')`));
      }
    }

    lastDocId = page.docs[page.docs.length - 1].id;
    if (page.docs.length < pageSize) {
      hasMore = false;
    }
  }

  // IDs only — note contents are never logged.
  if (conflictIds.length > 0) {
    log(`[migrate-private-notes] conflicts (owner already has a different private note; field kept for review): ${conflictIds.join(', ')}`);
  }
  if (manualReviewIds.length > 0) {
    log(`[migrate-private-notes] manual review required (missing/invalid ownerUid; field kept): ${manualReviewIds.join(', ')}`);
  }
  if (errorIds.length > 0) {
    log(`[migrate-private-notes] failed documents (retry the script; already-migrated documents are skipped): ${errorIds.join(', ')}`);
  }
  log(`[migrate-private-notes] summary: ${JSON.stringify(summary)}`);
  return summary;
}

async function main() {
  const args = process.argv.slice(2);
  const execute = args.includes('--execute');
  const dryRun = !execute;

  if (!admin) {
    console.error(
      '[migrate-private-notes] firebase-admin is not installed. Run: npm --prefix functions ci ' +
      '(or npm install firebase-admin in this directory), then re-run this script.'
    );
    process.exitCode = 1;
    return;
  }

  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS && !process.env.FIRESTORE_EMULATOR_HOST) {
    console.error(
      '[migrate-private-notes] No credentials found. Authenticate with\n' +
      '  gcloud auth application-default login\n' +
      'or set GOOGLE_APPLICATION_CREDENTIALS to a service-account JSON file with the\n' +
      'datastore.user / Cloud Datastore User role on the target project, then re-run.\n' +
      '(Against the local Firestore emulator, set FIRESTORE_EMULATOR_HOST instead.)'
    );
    process.exitCode = 1;
    return;
  }

  console.log(`[migrate-private-notes] mode: ${dryRun ? 'DRY-RUN (no writes)' : 'EXECUTE (writes enabled)'}`);
  admin.initializeApp();
  const db = admin.firestore();
  await runMigration(db, { dryRun, fieldDelete: () => admin.firestore.FieldValue.delete() });
}

if (require.main === module) {
  main().catch((error) => {
    console.error(`[migrate-private-notes] fatal: ${error.message}`);
    process.exitCode = 1;
  });
}

module.exports = { migrateSubmission, runMigration, PAGE_SIZE };

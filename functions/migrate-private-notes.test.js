/**
 * Migration logic tests for scripts/migrate-private-notes.js.
 *
 * These exercise the real migration decision logic (migrateSubmission / runMigration)
 * against the genuine Firestore emulator via the Admin SDK — the same way the trusted
 * server-side code paths are tested. Dry-run must never write; execution must move the
 * note into the owner's private document and strip the public field atomically;
 * re-running must be a complete no-op.
 *
 * Each per-document case drives migrateSubmission() directly on its own seeded
 * document, so assertions stay isolated from other documents leftover in the shared
 * emulator database; the runMigration() test verifies accumulation with state-based
 * (>=) assertions for the same reason.
 *
 * Run via the repo-wide emulator suite:
 *   ./functions/node_modules/.bin/firebase --config <config> emulators:exec --only firestore,storage \
 *     "node --test functions/migrate-private-notes.test.js"
 *
 * The suite refuses to start unless the emulator host environment variables are set,
 * so it can never run against a production Firebase project.
 */

const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const admin = require('firebase-admin');
const { migrateSubmission, runMigration } = require('../scripts/migrate-private-notes.js');

const PROJECT_ID = 'vividscout-rules-emulator';

let idCounter = 0;
const nextId = (prefix) => `${prefix}-${++idCounter}`;
const silentLog = () => {};

let db;

before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      'Refusing to run: FIRESTORE_EMULATOR_HOST must be set. ' +
      'Run this suite via: firebase emulators:exec --only firestore,storage "node --test functions/migrate-private-notes.test.js"'
    );
  }
  if (admin.apps.length === 0) {
    admin.initializeApp({ projectId: PROJECT_ID });
  }
  db = admin.firestore();
});

after(async () => {
  if (admin.apps.length > 0) {
    await admin.app().delete();
  }
});

const seedSubmission = async (overrides) => {
  const id = nextId('migration-note-submission');
  await db.collection('community_submissions').doc(id).create({
    id,
    ownerUid: 'alice-uid',
    title: 'Miradouro da Senhora do Monte',
    description: 'Best sunset viewpoint in Lisbon',
    category: 'sights',
    locationName: 'Lisbon',
    latitude: 38.7145,
    longitude: -9.1276,
    photoUri: '',
    contributor: 'Alice',
    timestamp: 1700000000000,
    ...overrides,
  });
  return id;
};

const privateNoteRef = (submissionId, ownerUid = 'alice-uid') =>
  db.collection('users').doc(ownerUid)
    .collection('private_spot_notes').doc(submissionId);

const executeOptions = () => ({
  dryRun: false,
  fieldDelete: () => admin.firestore.FieldValue.delete(),
});

describe('migrate-private-notes (real Firestore emulator)', () => {
  it('dry-run classifies a legacy note without writing anything', async () => {
    const id = await seedSubmission({ userNotes: 'legacy private note' });
    const snap = await db.collection('community_submissions').doc(id).get();

    const result = await migrateSubmission(db, snap, { dryRun: true });

    assert.strictEqual(result.status, 'migrated');
    assert.strictEqual(result.submissionId, id);

    // Dry-run mutates NOTHING: the public field stays, no private document is created.
    const publicDoc = await db.collection('community_submissions').doc(id).get();
    assert.strictEqual(publicDoc.get('userNotes'), 'legacy private note',
      'dry-run never removes the public field');
    assert.ok(!(await privateNoteRef(id).get()).exists,
      'dry-run never creates the private note');
  });

  it('execution moves the note into the owner private path and strips the public field', async () => {
    const id = await seedSubmission({ userNotes: 'move me' });
    const snap = await db.collection('community_submissions').doc(id).get();

    const result = await migrateSubmission(db, snap, executeOptions());

    assert.strictEqual(result.status, 'migrated');

    const publicDoc = await db.collection('community_submissions').doc(id).get();
    assert.strictEqual(publicDoc.data().userNotes, undefined,
      'the private field is removed from the public document');
    assert.strictEqual(publicDoc.get('title'),
      'Miradouro da Senhora do Monte', 'unrelated public fields survive untouched');

    const privateDoc = await privateNoteRef(id).get();
    assert.ok(privateDoc.exists, 'the owner private note was created');
    assert.strictEqual(privateDoc.get('notes'), 'move me');
    assert.strictEqual(privateDoc.get('ownerUid'), 'alice-uid');
    assert.strictEqual(privateDoc.get('spotId'), id);
    assert.ok(typeof privateDoc.get('updatedAt') === 'number');
  });

  it('is idempotent: a completed migration writes nothing on re-run', async () => {
    const id = await seedSubmission({ userNotes: 'run once' });
    const snap = await db.collection('community_submissions').doc(id).get();
    await migrateSubmission(db, snap, executeOptions());

    const result = await migrateSubmission(db, snap, executeOptions());

    assert.strictEqual(result.status, 'no-note', 'the re-run finds nothing to move');
    const privateDoc = await privateNoteRef(id).get();
    assert.strictEqual(privateDoc.get('notes'), 'run once', 'the private note is untouched');
  });

  it('treats an identical pre-existing private note as a completed copy', async () => {
    const id = await seedSubmission({ userNotes: 'same note' });
    // Pre-existing private note with identical content (e.g. an earlier migration run):
    // completing the migration must not duplicate or overwrite anything.
    await privateNoteRef(id).create({
      spotId: id,
      ownerUid: 'alice-uid',
      notes: 'same note',
      updatedAt: 1700000000000
    });
    const snap = await db.collection('community_submissions').doc(id).get();

    const result = await migrateSubmission(db, snap, executeOptions());

    assert.strictEqual(result.status, 'migrated',
      'identical content completes the copy instead of conflicting');
    const privateDoc = await privateNoteRef(id).get();
    assert.strictEqual(privateDoc.get('notes'), 'same note');
    const publicDoc = await db.collection('community_submissions').doc(id).get();
    assert.strictEqual(publicDoc.data().userNotes, undefined);
  });

  it('keeps the public field when the owner already has a DIFFERENT private note (conflict)', async () => {
    const id = await seedSubmission({ userNotes: 'public-side note' });
    await privateNoteRef(id).create({
      spotId: id,
      ownerUid: 'alice-uid',
      notes: 'private-side note',
      updatedAt: 1700000000000
    });
    const snap = await db.collection('community_submissions').doc(id).get();

    const result = await migrateSubmission(db, snap, executeOptions());

    assert.strictEqual(result.status, 'conflict');
    const privateDoc = await privateNoteRef(id).get();
    assert.strictEqual(privateDoc.get('notes'), 'private-side note',
      'the existing private note is never overwritten');
    const publicDoc = await db.collection('community_submissions').doc(id).get();
    assert.strictEqual(publicDoc.get('userNotes'), 'public-side note',
      'the note is preserved for manual review rather than destroyed');
  });

  it('preserves records with a missing ownerUid for manual review', async () => {
    const id = await seedSubmission({ ownerUid: '', userNotes: 'orphaned note' });
    const snap = await db.collection('community_submissions').doc(id).get();

    const result = await migrateSubmission(db, snap, executeOptions());

    assert.strictEqual(result.status, 'manual-review',
      'missing ownerUid is flagged for manual review, never assumed');
    const publicDoc = await db.collection('community_submissions').doc(id).get();
    assert.strictEqual(publicDoc.get('userNotes'), 'orphaned note',
      'the field is left in place — the note is never destroyed');
  });

  it('skips submissions that already have no private note', async () => {
    const id = await seedSubmission({});
    const snap = await db.collection('community_submissions').doc(id).get();

    const result = await migrateSubmission(db, snap, executeOptions());

    assert.strictEqual(result.status, 'no-note');
    assert.ok(!(await privateNoteRef(id).get()).exists);
  });

  it('runMigration paginates the whole collection and accumulates a summary', async () => {
    const firstId = await seedSubmission({ userNotes: 'first' });
    const secondId = await seedSubmission({ userNotes: 'second' });

    const summary = await runMigration(db, { dryRun: true, log: silentLog });

    assert.ok(summary.scanned >= 2, 'both new submissions are scanned');
    assert.ok(summary.migrated >= 2, 'both new legacy notes are planned');
    assert.strictEqual(summary.mode, 'dry-run');
    // Dry-run mutated nothing for the planned documents.
    for (const id of [firstId, secondId]) {
      const publicDoc = await db.collection('community_submissions').doc(id).get();
      assert.ok(publicDoc.get('userNotes'), 'dry-run leaves the public field in place');
      assert.ok(!(await privateNoteRef(id).get()).exists, 'dry-run creates nothing');
    }
  });

  it('never logs note contents (document IDs and counts only)', async () => {
    const id = await seedSubmission({ userNotes: 'SECRET-CONTENT-MUST-NOT-APPEAR-IN-LOGS' });
    const logged = [];

    await runMigration(db, {
      dryRun: true,
      log: (message) => logged.push(String(message)),
    });

    for (const line of logged) {
      assert.ok(!line.includes('SECRET-CONTENT-MUST-NOT-APPEAR-IN-LOGS'),
        'note content leaked into a log line');
    }
  });
});

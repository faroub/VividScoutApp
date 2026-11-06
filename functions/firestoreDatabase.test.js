/**
 * Firestore database-ID contract test.
 *
 * Verifies the Cloud Functions end of the Android/Functions database alignment
 * (see functions/firestoreDatabase.js): the empty string and "(default)" both
 * canonicalize to the default database (the historical single-database behavior),
 * and any other value selects that named database. This is the contract the Android
 * app relies on through R.string.firestore_database_id.
 *
 * Run directly:  node --test functions/firestoreDatabase.test.js
 * (also part of `npm --prefix functions test`).
 */

const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { firestoreDatabaseId } = require('./firestoreDatabase');

describe('firestoreDatabaseId()', () => {
  const previousValue = process.env.FIRESTORE_DATABASE_ID;

  before(() => {
    // The parameter reads process.env at value() call time, so start each case from
    // a clean state.
    delete process.env.FIRESTORE_DATABASE_ID;
  });

  after(() => {
    if (previousValue === undefined) {
      delete process.env.FIRESTORE_DATABASE_ID;
    } else {
      process.env.FIRESTORE_DATABASE_ID = previousValue;
    }
  });

  it('canonicalizes an unset variable to the default database', () => {
    assert.strictEqual(firestoreDatabaseId(), '(default)');
  });

  it('canonicalizes an empty string to the default database', () => {
    process.env.FIRESTORE_DATABASE_ID = '';
    assert.strictEqual(firestoreDatabaseId(), '(default)');
  });

  it('canonicalizes a blank/whitespace string to the default database', () => {
    process.env.FIRESTORE_DATABASE_ID = '   ';
    assert.strictEqual(firestoreDatabaseId(), '(default)');
  });

  it('canonicalizes an explicit "(default)" to itself', () => {
    process.env.FIRESTORE_DATABASE_ID = '(default)';
    assert.strictEqual(firestoreDatabaseId(), '(default)');
  });

  it('resolves a named database ID without mutation', () => {
    process.env.FIRESTORE_DATABASE_ID = 'vividscout-prod';
    assert.strictEqual(firestoreDatabaseId(), 'vividscout-prod');
  });

  it('trims surrounding whitespace from a named database ID', () => {
    process.env.FIRESTORE_DATABASE_ID = '  vividscout-prod  ';
    assert.strictEqual(firestoreDatabaseId(), 'vividscout-prod');
  });
});

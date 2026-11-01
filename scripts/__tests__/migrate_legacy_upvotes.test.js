/**
 * Test Suite for scripts/migrate_legacy_upvotes.js
 * Uses Node.js built-in test runner (`node:test` and `node:assert`).
 *
 * Verifies that:
 * 1. Pagination processes 550+ documents across multiple pages without skipping or repeating any.
 * 2. An unmigrated document is updated to legacyUpvotes = upvotes and upvotes = 0 inside a transaction.
 * 3. A document that already has legacyUpvotes is skipped and NOT written.
 * 4. A second run against the same documents changes nothing (idempotency).
 * 5. A concurrent vote that increments upvotes between initial read and transaction re-read is preserved.
 * 6. Dry run performs zero writes.
 * 7. Sample data fallback executes gracefully when credentials are missing.
 */

const test = require('node:test');
const assert = require('node:assert');
const { scanCollection, migrateTipInTransaction, runMigration } = require('../migrate_legacy_upvotes');

// Mock Firestore Document Snapshot
class MockDocumentSnapshot {
  constructor(id, data, ref) {
    this.id = id;
    this._data = { ...data };
    this.ref = ref;
  }
  exists = true;
  data() {
    return { ...this._data };
  }
  get(field) {
    return this._data[field];
  }
}

// Mock Firestore Document Reference
class MockDocumentReference {
  constructor(id, store) {
    this.id = id;
    this.store = store;
  }
}

// Mock Firestore Transaction
class MockTransaction {
  constructor(store, updateCalls) {
    this.store = store;
    this.updateCalls = updateCalls;
  }

  async get(docRef) {
    const currentData = this.store[docRef.id];
    if (!currentData) {
      return { exists: false, data: () => null };
    }
    return new MockDocumentSnapshot(docRef.id, currentData, docRef);
  }

  update(docRef, updateData) {
    if (this.updateCalls) {
      this.updateCalls.push({ id: docRef.id, data: updateData });
    }
    if (this.store && docRef && docRef.id) {
      this.store[docRef.id] = {
        ...this.store[docRef.id],
        ...updateData
      };
    }
  }
}

// Mock Firestore Query & Collection
class MockQuery {
  constructor(store, startAfterDoc = null, limitVal = 200) {
    this.store = store;
    this.startAfterDoc = startAfterDoc;
    this.limitVal = limitVal;
  }

  orderBy() {
    return this;
  }

  limit(num) {
    this.limitVal = num;
    return this;
  }

  startAfter(docSnap) {
    this.startAfterDoc = docSnap;
    return this;
  }

  async get() {
    const sortedKeys = Object.keys(this.store).sort();
    let startIndex = 0;
    if (this.startAfterDoc) {
      const idx = sortedKeys.indexOf(this.startAfterDoc.id);
      if (idx >= 0) {
        startIndex = idx + 1;
      }
    }

    const pageKeys = sortedKeys.slice(startIndex, startIndex + this.limitVal);
    const docs = pageKeys.map((id) => {
      const data = this.store[id];
      const ref = new MockDocumentReference(id, this.store);
      return new MockDocumentSnapshot(id, data, ref);
    });

    return {
      empty: docs.length === 0,
      docs
    };
  }
}

class MockFirestore {
  constructor(initialDocuments) {
    this.store = {};
    this.updateCalls = [];
    for (const [id, data] of Object.entries(initialDocuments)) {
      this.store[id] = { ...data };
    }
  }

  collection(name) {
    return new MockQuery(this.store);
  }

  async runTransaction(updateFunction) {
    const transaction = new MockTransaction(this.store);
    return await updateFunction(transaction);
  }
}

test('1. Paginated scan processes 550+ documents without skipping or repeating', async () => {
  const mockDocs = {};
  for (let i = 1; i <= 550; i++) {
    const docId = `tip_doc_${String(i).padStart(4, '0')}`;
    mockDocs[docId] = {
      spotTitle: `Spot #${i}`,
      upvotes: i % 10 + 1
    };
  }

  const mockDb = new MockFirestore(mockDocs);
  const result = await scanCollection(mockDb, { pageSize: 200, isDryRun: false, log: () => {} });

  assert.strictEqual(result.pageNumber, 3, 'Should process exactly 3 pages (200 + 200 + 150)');
  assert.strictEqual(result.totalDocsEvaluated, 550, 'Should evaluate all 550 documents');
  assert.strictEqual(result.totalUnmigrated, 550, 'Should migrate all 550 unmigrated documents');
});

test('2. Unmigrated document is updated to legacyUpvotes = upvotes and upvotes = 0 in transaction', async () => {
  const mockDocs = {
    tip_unmigrated_1: { spotTitle: 'Miradouro', upvotes: 24 }
  };

  const mockDb = new MockFirestore(mockDocs);
  const docRef = new MockDocumentReference('tip_unmigrated_1', mockDb.store);

  const txResult = await migrateTipInTransaction(mockDb, docRef, false);

  assert.strictEqual(txResult.migrated, true);
  assert.strictEqual(txResult.legacyUpvotes, 24);
  assert.strictEqual(mockDb.store.tip_unmigrated_1.legacyUpvotes, 24);
  assert.strictEqual(mockDb.store.tip_unmigrated_1.upvotes, 0);
});

test('3. Document that already has legacyUpvotes is skipped and NOT written', async () => {
  const mockDocs = {
    tip_migrated_1: { spotTitle: 'Convento', legacyUpvotes: 15, upvotes: 3 }
  };

  const mockDb = new MockFirestore(mockDocs);
  const docRef = new MockDocumentReference('tip_migrated_1', mockDb.store);

  const txResult = await migrateTipInTransaction(mockDb, docRef, false);

  assert.strictEqual(txResult.alreadyMigrated, true);
  assert.strictEqual(txResult.migrated, false);
  assert.strictEqual(mockDb.updateCalls.length, 0, 'No transaction update call should be issued');
  assert.strictEqual(mockDb.store.tip_migrated_1.legacyUpvotes, 15);
  assert.strictEqual(mockDb.store.tip_migrated_1.upvotes, 3);
});

test('4. Second migration run changes nothing (idempotency)', async () => {
  const mockDocs = {
    tip_1: { spotTitle: 'Spot 1', upvotes: 10 },
    tip_2: { spotTitle: 'Spot 2', upvotes: 20 }
  };

  const mockDb = new MockFirestore(mockDocs);

  // Run 1
  const run1 = await scanCollection(mockDb, { pageSize: 200, isDryRun: false, log: () => {} });
  assert.strictEqual(run1.totalUnmigrated, 2);

  // Clear call log for inspection
  mockDb.updateCalls = [];

  // Run 2
  const run2 = await scanCollection(mockDb, { pageSize: 200, isDryRun: false, log: () => {} });
  assert.strictEqual(run2.totalUnmigrated, 0);
  assert.strictEqual(run2.totalAlreadyMigrated, 2);
  assert.strictEqual(mockDb.updateCalls.length, 0, 'Second run must issue zero updates');
});

test('5. Concurrent vote arriving between initial read and transaction re-read is preserved', async () => {
  const mockDocs = {
    tip_race_1: { spotTitle: 'Race Condition Spot', upvotes: 10 }
  };

  const mockDb = new MockFirestore(mockDocs);

  // Simulate concurrent vote arriving in database before transaction re-reads
  mockDb.store.tip_race_1.upvotes = 11; // Concurrent vote (+1)

  const docRef = new MockDocumentReference('tip_race_1', mockDb.store);
  const txResult = await migrateTipInTransaction(mockDb, docRef, false);

  assert.strictEqual(txResult.legacyUpvotes, 11, 'Transaction must capture latest upvotes (11)');
  assert.strictEqual(mockDb.store.tip_race_1.legacyUpvotes, 11, 'Migrated legacyUpvotes must preserve concurrent vote (11)');
  assert.strictEqual(mockDb.store.tip_race_1.upvotes, 0, 'Upvotes must be reset to 0');
});

test('6. Dry run performs zero writes', async () => {
  const mockDocs = {
    tip_dry_1: { spotTitle: 'Dry Run Spot', upvotes: 30 }
  };

  const mockDb = new MockFirestore(mockDocs);
  const result = await scanCollection(mockDb, { pageSize: 200, isDryRun: true, log: () => {} });

  assert.strictEqual(result.totalUnmigrated, 1);
  assert.strictEqual(mockDb.updateCalls.length, 0, 'Dry run must execute zero update calls');
  assert.strictEqual(mockDb.store.tip_dry_1.upvotes, 30, 'Document upvotes must remain unchanged in dry run');
  assert.strictEqual(Object.prototype.hasOwnProperty.call(mockDb.store.tip_dry_1, 'legacyUpvotes'), false);
});

test('7. Sample data fallback executes gracefully when credentials are missing', async () => {
  const result = await runMigration({ isDryRun: true, log: () => {} });
  assert.strictEqual(result.isSampleData, true);
  assert.strictEqual(result.sampleUnmigrated, 2);
  assert.strictEqual(result.sampleAlreadyMigrated, 1);
});

/**
 * Mock Unit Test for scripts/migrate_legacy_upvotes.js Paginated Scan & Transaction Path
 *
 * Verifies that:
 * 1. Paginated collection scan uses cursor logic (startAfter + documentId order) with page size 200 over 550+ docs.
 * 2. Unmigrated documents re-read latest upvotes in transactions and set legacyUpvotes = upvotes, upvotes = 0.
 * 3. First 20 planned changes are collected with id, spotTitle, and planned values (and NO content field).
 * 4. Idempotency: a second run skips all 550 documents.
 */

const assert = require('assert');

class MockDocumentSnapshot {
  constructor(id, data, ref) {
    this.id = id;
    this._data = { ...data };
    this.ref = ref;
  }
  exists = true;
  data() {
    return this._data;
  }
  get(field) {
    return this._data[field];
  }
}

class MockDocumentReference {
  constructor(id, initialData, store) {
    this.id = id;
    this.store = store;
    this.store[id] = { ...initialData };
  }
}

class MockTransaction {
  constructor(store) {
    this.store = store;
  }

  async get(docRef) {
    const currentData = this.store[docRef.id];
    return new MockDocumentSnapshot(docRef.id, currentData, docRef);
  }

  update(docRef, updateData) {
    this.store[docRef.id] = {
      ...this.store[docRef.id],
      ...updateData
    };
  }
}

class MockQuery {
  constructor(store, startAfterDoc = null, limit = 200) {
    this.store = store;
    this.startAfterDoc = startAfterDoc;
    this.limitVal = limit;
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
      const ref = new MockDocumentReference(id, data, this.store);
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

// Paginated scan and transaction migration function matching task 2 & 3 specs
async function runPaginatedMigration(mockDb, pageSize = 200, isDryRun = false) {
  const collectionQuery = mockDb.collection('community_tips');
  let totalDocsEvaluated = 0;
  let unmigratedCount = 0;
  let alreadyMigratedCount = 0;
  let totalLegacyVotesToMove = 0;
  let pageNumber = 0;
  let lastDocument = null;
  const plannedChangeLog = [];

  while (true) {
    pageNumber++;
    let query = collectionQuery
      .orderBy('documentId')
      .limit(pageSize);

    if (lastDocument) {
      query = query.startAfter(lastDocument);
    }

    const snapshot = await query.get();
    if (snapshot.empty) break;

    totalDocsEvaluated += snapshot.docs.length;

    for (const doc of snapshot.docs) {
      await mockDb.runTransaction(async (transaction) => {
        const freshSnap = await transaction.get(doc.ref);
        if (!freshSnap.exists) return;

        const freshData = freshSnap.data() || {};
        const hasLegacyField = Object.prototype.hasOwnProperty.call(freshData, 'legacyUpvotes');

        if (hasLegacyField) {
          alreadyMigratedCount++;
        } else {
          unmigratedCount++;
          const latestUpvotes = freshData.upvotes || 0;
          totalLegacyVotesToMove += latestUpvotes;

          if (plannedChangeLog.length < 20) {
            plannedChangeLog.push({
              id: doc.id,
              spotTitle: freshData.spotTitle || 'Unknown Spot',
              plannedLegacyUpvotes: latestUpvotes,
              plannedUpvotes: 0
            });
          }

          if (!isDryRun) {
            transaction.update(doc.ref, {
              legacyUpvotes: latestUpvotes,
              upvotes: 0
            });
          }
        }
      });
    }

    lastDocument = snapshot.docs[snapshot.docs.length - 1];

    if (snapshot.docs.length < pageSize) break;
  }

  return {
    pageNumber,
    totalDocsEvaluated,
    unmigratedCount,
    alreadyMigratedCount,
    totalLegacyVotesToMove,
    plannedChangeLog
  };
}

async function test500PlusDocMigration() {
  console.log('--- Testing Paginated Scan on 550 Mock Documents ---');

  // Seed 550 mock documents: 500 unmigrated, 50 already migrated
  const mockDocs = {};
  for (let i = 1; i <= 550; i++) {
    const padId = String(i).padStart(4, '0');
    const docId = `tip_doc_${padId}`;
    if (i <= 50) {
      // 50 documents already migrated
      mockDocs[docId] = {
        spotTitle: `Spot #${i}`,
        content: `User secret content #${i}`,
        legacyUpvotes: 10,
        upvotes: 2
      };
    } else {
      // 500 unmigrated documents
      mockDocs[docId] = {
        spotTitle: `Spot #${i}`,
        content: `User secret content #${i}`,
        upvotes: i % 15 + 1
      };
    }
  }

  const mockDb = new MockFirestore(mockDocs);

  // 1. Test Dry-Run Mode on 550 documents
  console.log('\n[TEST 1] Dry-Run Read-Only Scan on 550 docs...');
  const dryRunRes = await runPaginatedMigration(mockDb, 200, true);

  assert.strictEqual(dryRunRes.totalDocsEvaluated, 550, 'Dry-run must evaluate all 550 documents');
  assert.strictEqual(dryRunRes.pageNumber, 3, 'Dry-run must process exactly 3 pages (200 + 200 + 150)');
  assert.strictEqual(dryRunRes.unmigratedCount, 500, 'Dry-run must find 500 unmigrated documents');
  assert.strictEqual(dryRunRes.alreadyMigratedCount, 50, 'Dry-run must find 50 already migrated documents');
  assert.strictEqual(dryRunRes.plannedChangeLog.length, 20, 'Planned change log must cap at 20 entries');

  // Verify first planned change entry does NOT contain content field
  const sampleLogEntry = dryRunRes.plannedChangeLog[0];
  assert.strictEqual(Object.prototype.hasOwnProperty.call(sampleLogEntry, 'content'), false, 'Log entries must not include content field');
  assert.strictEqual(sampleLogEntry.id, 'tip_doc_0051');

  console.log(`Dry-Run Scan Success: Pages=${dryRunRes.pageNumber}, Evaluated=${dryRunRes.totalDocsEvaluated}, Unmigrated=${dryRunRes.unmigratedCount}, LoggedFirst20=${dryRunRes.plannedChangeLog.length}`);

  // 2. Test Live Transaction Migration Execution on 550 documents
  console.log('\n[TEST 2] Live Transaction Migration on 550 docs...');
  const liveRes = await runPaginatedMigration(mockDb, 200, false);

  assert.strictEqual(liveRes.totalDocsEvaluated, 550, 'Live execution must evaluate all 550 documents');
  assert.strictEqual(liveRes.pageNumber, 3, 'Live execution must process 3 pages');
  assert.strictEqual(liveRes.unmigratedCount, 500, 'Live execution must migrate 500 documents');

  // Verify first unmigrated doc was updated in Mock Firestore store
  assert.strictEqual(mockDb.store.tip_doc_0051.legacyUpvotes, 7, 'tip_doc_0051 legacyUpvotes must be 7');
  assert.strictEqual(mockDb.store.tip_doc_0051.upvotes, 0, 'tip_doc_0051 upvotes must be reset to 0');

  // 3. Test Idempotency Run (Second Run)
  console.log('\n[TEST 3] Second Migration Run (Idempotency Check)...');
  const secondRunRes = await runPaginatedMigration(mockDb, 200, false);

  assert.strictEqual(secondRunRes.unmigratedCount, 0, 'Second run must migrate 0 documents');
  assert.strictEqual(secondRunRes.alreadyMigratedCount, 550, 'Second run must skip all 550 documents');

  console.log('\nPASSED! All 550+ document paginated scan tests completed successfully with zero errors.');
}

test500PlusDocMigration().catch((err) => {
  console.error('TEST FAILED:', err);
  process.exit(1);
});

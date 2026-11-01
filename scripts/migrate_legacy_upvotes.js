/**
 * One-Time Migration Script: Option A Legacy Upvote Separation
 *
 * NOTE: Running a migration script with the Firebase Admin SDK bypasses client-side
 * security rules defined in firestore.rules, allowing administrative schema updates.
 *
 * This script identifies all 'community_tips' documents in Cloud Firestore that are missing
 * the 'legacyUpvotes' field.
 *
 * Execution Modes:
 * - Dry-run mode (DEFAULT): Performs paginated read-only scan of 'community_tips' using Admin SDK
 *   (or sample data if credentials absent), printing planned mutations without executing writes.
 * - Live Execution (--execute): Performs paginated scan and executes per-document transactions
 *   to update legacyUpvotes = current upvotes, upvotes = 0.
 *
 * Usage:
 *   Dry-run mode (Default, read-only):
 *     node scripts/migrate_legacy_upvotes.js
 *     node scripts/migrate_legacy_upvotes.js --dry-run
 *
 *   Live execution mode (Requires service account credentials & --execute):
 *     node scripts/migrate_legacy_upvotes.js --execute
 */

let admin;
try {
  admin = require('firebase-admin');
} catch (e) {
  // firebase-admin module optional for sample mode
}
const path = require('path');
const fs = require('fs');

const PAGE_SIZE = 200;

/**
 * Migrates a single community_tip document inside a Firestore transaction.
 * Re-reads the document state inside the transaction to prevent race conditions.
 */
async function migrateTipInTransaction(db, docRef, isDryRun = false) {
  return await db.runTransaction(async (transaction) => {
    const freshSnap = await transaction.get(docRef);
    if (!freshSnap.exists) {
      return { exists: false, migrated: false };
    }

    const freshData = freshSnap.data() || {};
    const hasLegacyField = Object.prototype.hasOwnProperty.call(freshData, 'legacyUpvotes');

    if (hasLegacyField) {
      return {
        exists: true,
        migrated: false,
        alreadyMigrated: true,
        legacyUpvotes: freshData.legacyUpvotes,
        upvotes: freshData.upvotes,
        spotTitle: freshData.spotTitle || 'Unknown Spot'
      };
    }

    const latestUpvotes = freshData.upvotes || 0;
    if (!isDryRun) {
      transaction.update(docRef, {
        legacyUpvotes: latestUpvotes,
        upvotes: 0
      });
    }

    return {
      exists: true,
      migrated: true,
      alreadyMigrated: false,
      legacyUpvotes: latestUpvotes,
      upvotes: 0,
      spotTitle: freshData.spotTitle || 'Unknown Spot'
    };
  });
}

/**
 * Scans 'community_tips' collection using paginated cursor queries with page size 200.
 */
async function scanCollection(db, options = {}) {
  const pageSize = options.pageSize || PAGE_SIZE;
  const isDryRun = options.isDryRun !== false;
  const log = options.log || console.log;

  const documentIdField = (admin && admin.firestore && admin.firestore.FieldPath)
    ? admin.firestore.FieldPath.documentId()
    : 'documentId';

  const tipsRef = db.collection('community_tips');

  let totalDocsEvaluated = 0;
  let totalUnmigrated = 0;
  let totalAlreadyMigrated = 0;
  let totalLegacyVotesToMove = 0;
  let pageNumber = 0;
  let lastDocument = null;
  const plannedChangeLog = [];

  log(`\nStarting paginated scan of 'community_tips' (Page Size = ${pageSize})...`);

  while (true) {
    pageNumber++;
    let query = tipsRef
      .orderBy(documentIdField)
      .limit(pageSize);

    if (lastDocument) {
      query = query.startAfter(lastDocument);
    }

    const snapshot = await query.get();
    if (snapshot.empty) {
      if (pageNumber === 1) {
        log(`No documents found in 'community_tips' collection.`);
      }
      break;
    }

    totalDocsEvaluated += snapshot.docs.length;

    for (const doc of snapshot.docs) {
      if (isDryRun) {
        const data = doc.data();
        const hasLegacyField = Object.prototype.hasOwnProperty.call(data, 'legacyUpvotes');
        if (hasLegacyField) {
          totalAlreadyMigrated++;
        } else {
          totalUnmigrated++;
          const currentUpvotes = data.upvotes || 0;
          totalLegacyVotesToMove += currentUpvotes;

          if (plannedChangeLog.length < 20) {
            plannedChangeLog.push({
              id: doc.id,
              spotTitle: data.spotTitle || 'Unknown Spot',
              plannedLegacyUpvotes: currentUpvotes,
              plannedUpvotes: 0
            });
          }
        }
      } else {
        const txResult = await migrateTipInTransaction(db, doc.ref, isDryRun);
        if (txResult.migrated) {
          totalUnmigrated++;
          totalLegacyVotesToMove += txResult.legacyUpvotes;
          if (plannedChangeLog.length < 20) {
            plannedChangeLog.push({
              id: doc.id,
              spotTitle: txResult.spotTitle,
              plannedLegacyUpvotes: txResult.legacyUpvotes,
              plannedUpvotes: 0
            });
          }
        } else if (txResult.alreadyMigrated) {
          totalAlreadyMigrated++;
        }
      }
    }

    lastDocument = snapshot.docs[snapshot.docs.length - 1];

    log(`Page ${pageNumber} Processed (${snapshot.docs.length} docs) | ` +
      `Total Evaluated: ${totalDocsEvaluated} | ` +
      `Unmigrated: ${totalUnmigrated} | ` +
      `Already Migrated: ${totalAlreadyMigrated}`);

    if (snapshot.docs.length < pageSize) {
      break;
    }
  }

  return {
    pageNumber,
    totalDocsEvaluated,
    totalUnmigrated,
    totalAlreadyMigrated,
    totalLegacyVotesToMove,
    plannedChangeLog
  };
}

/**
 * Main migration execution controller.
 */
async function runMigration(options = {}) {
  const cliArgs = options.args || process.argv.slice(2);
  const isExecuteMode = cliArgs.includes('--execute');
  const isDryRun = options.isDryRun !== undefined ? options.isDryRun : (!isExecuteMode || cliArgs.includes('--dry-run'));
  const log = options.log || console.log;
  const pageSize = options.pageSize || PAGE_SIZE;

  log(`===========================================================`);
  log(`  Option A Legacy Upvote Migration Tool                    `);
  log(`  Execution Mode: ${isDryRun ? 'DRY-RUN (Read-Only Scan)' : 'LIVE EXECUTION (--execute)'}`);
  log(`  Page Size: ${pageSize}`);
  log(`===========================================================`);

  let db = options.dbOverride || null;
  let hasCredentials = false;

  if (db) {
    hasCredentials = true;
  } else if (process.env.FIRESTORE_EMULATOR_HOST) {
    hasCredentials = true;
    if (admin && (!admin.apps || admin.apps.length === 0)) {
      admin.initializeApp({ projectId: 'vividscout-emulator' });
    }
    db = admin.firestore();
  } else {
    const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'service-account.json');
    if (fs.existsSync(serviceAccountPath)) {
      hasCredentials = true;
      if (admin && (!admin.apps || admin.apps.length === 0)) {
        try {
          const serviceAccount = require(serviceAccountPath);
          admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
          });
        } catch (e) {
          hasCredentials = false;
        }
      }
      if (admin && admin.apps && admin.apps.length > 0) {
        db = admin.firestore();
      }
    }
  }

  // --- SAMPLE DATA FALLBACK (When no credentials found) ---
  if (!hasCredentials || !db) {
    log(`\n===========================================================`);
    log(`  [WARNING] SAMPLE DATA ONLY. NOT A REAL DRY RUN.          `);
    log(`  No Firebase Admin credentials or emulator detected.      `);
    log(`  Please set GOOGLE_APPLICATION_CREDENTIALS or place       `);
    log(`  'scripts/service-account.json' to run on real Firestore.  `);
    log(`===========================================================\n`);

    const sampleDocs = [
      { id: 'tip_alpha_1', data: { spotTitle: 'Miradouro de Santa Luzia', upvotes: 24 } },
      { id: 'tip_beta_2', data: { spotTitle: 'Pastelaria Santo António', upvotes: 15, legacyUpvotes: 15 } },
      { id: 'tip_gamma_3', data: { spotTitle: 'Convento do Carmo', upvotes: 42 } }
    ];

    let sampleUnmigrated = 0;
    let sampleAlreadyMigrated = 0;
    let sampleLegacyVotesToMove = 0;

    log(`Evaluating ${sampleDocs.length} sample documents:`);
    for (const doc of sampleDocs) {
      const hasLegacyField = Object.prototype.hasOwnProperty.call(doc.data, 'legacyUpvotes');
      if (hasLegacyField) {
        sampleAlreadyMigrated++;
        log(` - Document '${doc.id}': [ALREADY MIGRATED] legacyUpvotes=${doc.data.legacyUpvotes}, upvotes=${doc.data.upvotes}`);
      } else {
        sampleUnmigrated++;
        const currentVotes = doc.data.upvotes || 0;
        sampleLegacyVotesToMove += currentVotes;
        log(` - Document '${doc.id}' (${doc.data.spotTitle}): [PLANNED UPDATE] legacyUpvotes=${currentVotes}, upvotes=0`);
      }
    }

    log(`\n[SAMPLE DATA SUMMARY]`);
    log(`   - Total Sample Documents Evaluated: ${sampleDocs.length}`);
    log(`   - Sample Documents Needing Migration: ${sampleUnmigrated}`);
    log(`   - Sample Documents Already Migrated: ${sampleAlreadyMigrated}`);
    log(`   - Total Legacy Votes to Move: ${sampleLegacyVotesToMove}`);
    return {
      isSampleData: true,
      sampleUnmigrated,
      sampleAlreadyMigrated,
      sampleLegacyVotesToMove
    };
  }

  // --- REAL FIRESTORE PAGINATED SCAN / EXECUTION ---
  const summary = await scanCollection(db, { pageSize, isDryRun, log });

  log(`\n===========================================================`);
  log(`  MIGRATION SCAN SUMMARY (${isDryRun ? 'DRY-RUN READ-ONLY' : 'LIVE EXECUTION'})`);
  log(`===========================================================`);
  log(` - Total Pages Processed: ${summary.pageNumber}`);
  log(` - Total Documents Evaluated: ${summary.totalDocsEvaluated}`);
  log(` - Documents Needing Migration: ${summary.totalUnmigrated}`);
  log(` - Documents Already Migrated: ${summary.totalAlreadyMigrated}`);
  log(` - Total Legacy Votes to Move: ${summary.totalLegacyVotesToMove}`);

  if (summary.plannedChangeLog.length > 0) {
    log(`\nFirst ${summary.plannedChangeLog.length} Planned Changes:`);
    summary.plannedChangeLog.forEach((item, idx) => {
      log(`  ${idx + 1}. Doc ID: '${item.id}' | Spot: "${item.spotTitle}" | Planned: legacyUpvotes=${item.plannedLegacyUpvotes}, upvotes=${item.plannedUpvotes}`);
    });
  }

  if (isDryRun && summary.totalUnmigrated > 0) {
    log(`\nTo execute live updates against Cloud Firestore, run:\n  node scripts/migrate_legacy_upvotes.js --execute\n`);
  }

  return summary;
}

if (require.main === module) {
  runMigration().catch(err => {
    console.error(`[ERROR] Migration script failed:`, err);
    process.exit(1);
  });
}

module.exports = {
  scanCollection,
  migrateTipInTransaction,
  runMigration
};

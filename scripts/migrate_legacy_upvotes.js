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

// Determine execution mode
const args = process.argv.slice(2);
const isExecuteMode = args.includes('--execute');
const isDryRun = !isExecuteMode || args.includes('--dry-run');
const PAGE_SIZE = 200;

console.log(`===========================================================`);
console.log(`  Option A Legacy Upvote Migration Tool                    `);
console.log(`  Execution Mode: ${isDryRun ? 'DRY-RUN (Read-Only Scan)' : 'LIVE EXECUTION (--execute)'}`);
console.log(`  Page Size: ${PAGE_SIZE}`);
console.log(`===========================================================`);

async function runMigration() {
  let hasCredentials = false;

  // Check for credentials or emulator
  if (process.env.FIRESTORE_EMULATOR_HOST) {
    hasCredentials = true;
    if (admin && (!admin.apps || admin.apps.length === 0)) {
      admin.initializeApp({ projectId: 'vividscout-emulator' });
    }
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
    }
  }

  // --- SAMPLE DATA FALLBACK (When no credentials found) ---
  if (!hasCredentials) {
    console.log(`\n===========================================================`);
    console.log(`  [WARNING] SAMPLE DATA ONLY. NOT A REAL DRY RUN.          `);
    console.log(`  No Firebase Admin credentials or emulator detected.      `);
    console.log(`  Please set GOOGLE_APPLICATION_CREDENTIALS or place       `);
    console.log(`  'scripts/service-account.json' to run on real Firestore.  `);
    console.log(`===========================================================\n`);

    const sampleDocs = [
      { id: 'tip_alpha_1', data: { spotTitle: 'Miradouro de Santa Luzia', upvotes: 24 } },
      { id: 'tip_beta_2', data: { spotTitle: 'Pastelaria Santo António', upvotes: 15, legacyUpvotes: 15 } },
      { id: 'tip_gamma_3', data: { spotTitle: 'Convento do Carmo', upvotes: 42 } }
    ];

    let sampleUnmigrated = 0;
    let sampleAlreadyMigrated = 0;
    let sampleLegacyVotesToMove = 0;

    console.log(`Evaluating ${sampleDocs.length} sample documents:`);
    for (const doc of sampleDocs) {
      const hasLegacyField = Object.prototype.hasOwnProperty.call(doc.data, 'legacyUpvotes');
      if (hasLegacyField) {
        sampleAlreadyMigrated++;
        console.log(` - Document '${doc.id}': [ALREADY MIGRATED] legacyUpvotes=${doc.data.legacyUpvotes}, upvotes=${doc.data.upvotes}`);
      } else {
        sampleUnmigrated++;
        const currentVotes = doc.data.upvotes || 0;
        sampleLegacyVotesToMove += currentVotes;
        console.log(` - Document '${doc.id}' (${doc.data.spotTitle}): [PLANNED UPDATE] legacyUpvotes=${currentVotes}, upvotes=0`);
      }
    }

    console.log(`\n[SAMPLE DATA SUMMARY]`);
    console.log(`   - Total Sample Documents Evaluated: ${sampleDocs.length}`);
    console.log(`   - Sample Documents Needing Migration: ${sampleUnmigrated}`);
    console.log(`   - Sample Documents Already Migrated: ${sampleAlreadyMigrated}`);
    console.log(`   - Total Legacy Votes to Move: ${sampleLegacyVotesToMove}`);
    return;
  }

  // --- REAL FIRESTORE PAGINATED SCAN / EXECUTION ---
  const db = admin.firestore();
  const tipsRef = db.collection('community_tips');

  let totalDocsEvaluated = 0;
  let totalUnmigrated = 0;
  let totalAlreadyMigrated = 0;
  let totalLegacyVotesToMove = 0;
  let pageNumber = 0;
  let lastDocument = null;
  const plannedChangeLog = [];

  console.log(`\nStarting paginated scan of 'community_tips' (Page Size = ${PAGE_SIZE})...`);

  while (true) {
    pageNumber++;
    let query = tipsRef
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(PAGE_SIZE);

    if (lastDocument) {
      query = query.startAfter(lastDocument);
    }

    const snapshot = await query.get();
    if (snapshot.empty) {
      if (pageNumber === 1) {
        console.log(`No documents found in 'community_tips' collection.`);
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
        // Live execution mode: process each document in an isolated transaction
        await db.runTransaction(async (transaction) => {
          const freshSnap = await transaction.get(doc.ref);
          if (!freshSnap.exists) return;

          const freshData = freshSnap.data() || {};
          const hasLegacyField = Object.prototype.hasOwnProperty.call(freshData, 'legacyUpvotes');

          if (hasLegacyField) {
            totalAlreadyMigrated++;
          } else {
            totalUnmigrated++;
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

            transaction.update(doc.ref, {
              legacyUpvotes: latestUpvotes,
              upvotes: 0
            });
          }
        });
      }
    }

    lastDocument = snapshot.docs[snapshot.docs.length - 1];

    console.log(`Page ${pageNumber} Processed (${snapshot.docs.length} docs) | ` +
      `Total Evaluated: ${totalDocsEvaluated} | ` +
      `Unmigrated: ${totalUnmigrated} | ` +
      `Already Migrated: ${totalAlreadyMigrated}`);

    if (snapshot.docs.length < PAGE_SIZE) {
      break;
    }
  }

  // --- SUMMARY OUTPUT ---
  console.log(`\n===========================================================`);
  console.log(`  MIGRATION SCAN SUMMARY (${isDryRun ? 'DRY-RUN READ-ONLY' : 'LIVE EXECUTION'})`);
  console.log(`===========================================================`);
  console.log(` - Total Pages Processed: ${pageNumber}`);
  console.log(` - Total Documents Evaluated: ${totalDocsEvaluated}`);
  console.log(` - Documents Needing Migration: ${totalUnmigrated}`);
  console.log(` - Documents Already Migrated: ${totalAlreadyMigrated}`);
  console.log(` - Total Legacy Votes to Move: ${totalLegacyVotesToMove}`);

  if (plannedChangeLog.length > 0) {
    console.log(`\nFirst ${plannedChangeLog.length} Planned Changes:`);
    plannedChangeLog.forEach((item, idx) => {
      console.log(`  ${idx + 1}. Doc ID: '${item.id}' | Spot: "${item.spotTitle}" | Planned: legacyUpvotes=${item.plannedLegacyUpvotes}, upvotes=${item.plannedUpvotes}`);
    });
  }

  if (isDryRun && totalUnmigrated > 0) {
    console.log(`\nTo execute live updates against Cloud Firestore, run:\n  node scripts/migrate_legacy_upvotes.js --execute\n`);
  }
}

runMigration().catch(err => {
  console.error(`[ERROR] Migration script failed:`, err);
});

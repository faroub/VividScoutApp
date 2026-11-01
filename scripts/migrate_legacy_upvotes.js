/**
 * One-Time Migration Script: Option A Legacy Upvote Separation
 *
 * NOTE: Running a migration script with the Firebase Admin SDK bypasses client-side
 * security rules defined in firestore.rules, allowing administrative schema updates.
 *
 * This script identifies all 'community_tips' documents in Cloud Firestore that are missing
 * the 'legacyUpvotes' field. For each unmigrated document, it plans or executes a batch update setting:
 *   - legacyUpvotes = current upvotes
 *   - upvotes = 0
 *
 * Safety Controls:
 * - Dry-run mode is ON by default. The script logs all planned mutations without making any writes.
 * - To execute live writes against Firestore, pass the --execute flag.
 *
 * Usage:
 *   Dry-run mode (Default, read-only simulation):
 *     node scripts/migrate_legacy_upvotes.js
 *     node scripts/migrate_legacy_upvotes.js --dry-run
 *
 *   Live execution mode (Requires service account credentials):
 *     node scripts/migrate_legacy_upvotes.js --execute
 */

let admin;
try {
  admin = require('firebase-admin');
} catch (e) {
  // firebase-admin module optional for dry-run simulation mode
}
const path = require('path');

// Determine execution mode
const args = process.argv.slice(2);
const isExecuteMode = args.includes('--execute');
const isDryRun = !isExecuteMode || args.includes('--dry-run');

console.log(`===========================================================`);
console.log(`  Option A Legacy Upvote Migration Tool                    `);
console.log(`  Execution Mode: ${isDryRun ? 'DRY-RUN (Simulated Read-Only)' : 'LIVE EXECUTION (--execute)'}`);
console.log(`===========================================================`);

async function runMigration() {
  if (isDryRun) {
    console.log(`[DRY-RUN] No writes will be made to Cloud Firestore.`);
  } else {
    console.log(`[EXECUTE] Connecting to Firebase Admin SDK for live database migration...`);
    const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'service-account.json');
    try {
      const serviceAccount = require(serviceAccountPath);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
    } catch (e) {
      console.error(`[ERROR] Failed to load service account credentials at '${serviceAccountPath}'.`);
      console.error(`Please set GOOGLE_APPLICATION_CREDENTIALS or place 'service-account.json' in scripts/ directory.`);
      process.exit(1);
    }
  }

  // Sample mock documents for dry-run simulation if Admin SDK is uninitialized
  const sampleDocs = [
    { id: 'tip_alpha_1', data: { spotTitle: 'Miradouro de Santa Luzia', upvotes: 24, content: 'Quiet pergola' } },
    { id: 'tip_beta_2', data: { spotTitle: 'Pastelaria Santo António', upvotes: 15, legacyUpvotes: 15, content: 'Fresh tarts' } },
    { id: 'tip_gamma_3', data: { spotTitle: 'Convento do Carmo', upvotes: 42, content: 'Gothic arches' } }
  ];

  let unmigratedCount = 0;
  let alreadyMigratedCount = 0;

  console.log(`\nScanning 'community_tips' collection...`);

  if (isDryRun && (!admin || !admin.apps || admin.apps.length === 0)) {
    console.log(`[DRY-RUN SIMULATION] Evaluating sample Firestore document set:`);
    for (const doc of sampleDocs) {
      const hasLegacyField = Object.prototype.hasOwnProperty.call(doc.data, 'legacyUpvotes');
      if (hasLegacyField) {
        alreadyMigratedCount++;
        console.log(` - Document '${doc.id}': Already migrated (legacyUpvotes=${doc.data.legacyUpvotes}, upvotes=${doc.data.upvotes}). Skipping.`);
      } else {
        unmigratedCount++;
        const currentCount = doc.data.upvotes || 0;
        console.log(` - Document '${doc.id}': UNMIGRATED. Planned update -> legacyUpvotes=${currentCount}, upvotes=0.`);
      }
    }
    console.log(`\n[DRY-RUN SUMMARY] Scan complete.`);
    console.log(`   - Unmigrated documents needing update: ${unmigratedCount}`);
    console.log(`   - Already migrated documents: ${alreadyMigratedCount}`);
    console.log(`   - Total documents evaluated: ${sampleDocs.length}`);
    console.log(`\nTo run this migration against live Firebase, execute:\n  node scripts/migrate_legacy_upvotes.js --execute\n`);
    return;
  }

  // Live or connected Admin SDK execution
  const db = admin.firestore();
  const tipsRef = db.collection('community_tips');
  const snapshot = await tipsRef.get();

  if (snapshot.empty) {
    console.log(`No documents found in 'community_tips' collection.`);
    return;
  }

  const batch = db.batch();

  snapshot.forEach(doc => {
    const data = doc.data();
    const hasLegacyField = Object.prototype.hasOwnProperty.call(data, 'legacyUpvotes');

    if (hasLegacyField) {
      alreadyMigratedCount++;
      console.log(` - Document '${doc.id}': Already migrated. Skipping.`);
    } else {
      unmigratedCount++;
      const currentUpvotes = data.upvotes || 0;
      console.log(` - Document '${doc.id}': UNMIGRATED (upvotes=${currentUpvotes}). Planned update -> legacyUpvotes=${currentUpvotes}, upvotes=0.`);
      if (!isDryRun) {
        batch.update(doc.ref, {
          legacyUpvotes: currentUpvotes,
          upvotes: 0
        });
      }
    }
  });

  if (!isDryRun && unmigratedCount > 0) {
    console.log(`\n[EXECUTE] Committing batch write for ${unmigratedCount} documents...`);
    await batch.commit();
    console.log(`[EXECUTE] Migration successfully committed!`);
  } else {
    console.log(`\n[DRY-RUN SUMMARY] Scan complete.`);
    console.log(`   - Unmigrated documents needing update: ${unmigratedCount}`);
    console.log(`   - Already migrated documents: ${alreadyMigratedCount}`);
  }
}

runMigration().catch(err => {
  console.error(`[ERROR] Migration failed:`, err);
});

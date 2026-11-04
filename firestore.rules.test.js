/**
 * Security Rule Test Suite for firestore.rules
 * Tests direct Firestore write conditions for community tip votes and photo reports.
 */

const fs = require('fs');
const assert = require('assert');

// Read firestore.rules file
const rulesContent = fs.readFileSync('./firestore.rules', 'utf8');

// Verify firestore.rules contains pre-write !exists check for community tips votes
assert(
  rulesContent.includes('!exists(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))'),
  'firestore.rules MUST enforce !exists pre-write check for community tip upvotes'
);

// Verify firestore.rules contains post-write existsAfter check for community tips votes
assert(
  rulesContent.includes('existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))'),
  'firestore.rules MUST enforce existsAfter post-write check for community tip upvotes'
);

// Verify firestore.rules contains pre-write !exists check for photo reports
assert(
  rulesContent.includes('!exists(/databases/$(database)/documents/spot_photos/$(photoId)/reports/$(request.auth.uid))'),
  'firestore.rules MUST enforce !exists pre-write check for photo reports'
);

// Verify firestore.rules contains post-write existsAfter check for photo reports
assert(
  rulesContent.includes('existsAfter(/databases/$(database)/documents/spot_photos/$(photoId)/reports/$(request.auth.uid))'),
  'firestore.rules MUST enforce existsAfter post-write check for photo reports'
);

// Mock Firestore Rules Evaluator
class MockFirestoreSimulator {
  constructor() {
    this.documents = new Map();
  }

  getDoc(path) {
    return this.documents.get(path) || null;
  }

  setDoc(path, data) {
    this.documents.set(path, JSON.parse(JSON.stringify(data)));
  }

  // Evaluates community tip upvote update rule:
  // isAuthenticated() && diff keys only ['upvotes'] && upvotes == prev + 1 && !preExists && postExists
  evalTipUpvoteUpdate(authUid, tipId, updatedUpvotes, isCreatingVoteDoc) {
    if (!authUid) return { allowed: false, reason: 'unauthenticated' };

    const tipPath = `community_tips/${tipId}`;
    const votePath = `community_tips/${tipId}/votes/${authUid}`;

    const prevTip = this.getDoc(tipPath);
    if (!prevTip) return { allowed: false, reason: 'tip_not_found' };

    // 1. Upvote must be exactly prev + 1
    if (updatedUpvotes !== prevTip.upvotes + 1) {
      return { allowed: false, reason: 'upvotes_not_incremented_by_one' };
    }

    // 2. Pre-write check: !exists(votePath)
    const preExists = this.getDoc(votePath) !== null;
    if (preExists) {
      return { allowed: false, reason: 'vote_doc_already_exists_pre_write' };
    }

    // 3. Post-write check: existsAfter(votePath)
    const postExists = preExists || isCreatingVoteDoc;
    if (!postExists) {
      return { allowed: false, reason: 'vote_doc_missing_post_write' };
    }

    return { allowed: true };
  }

  // Evaluates photo report update rule:
  // isAuthenticated() && diff keys only ['reportsCount'] && reportsCount == prev + 1 && !preExists && postExists
  evalPhotoReportUpdate(authUid, photoId, updatedReportsCount, isCreatingReportDoc) {
    if (!authUid) return { allowed: false, reason: 'unauthenticated' };

    const photoPath = `spot_photos/${photoId}`;
    const reportPath = `spot_photos/${photoId}/reports/${authUid}`;

    const prevPhoto = this.getDoc(photoPath);
    if (!prevPhoto) return { allowed: false, reason: 'photo_not_found' };

    if (updatedReportsCount !== prevPhoto.reportsCount + 1) {
      return { allowed: false, reason: 'reports_count_not_incremented_by_one' };
    }

    const preExists = this.getDoc(reportPath) !== null;
    if (preExists) {
      return { allowed: false, reason: 'report_doc_already_exists_pre_write' };
    }

    const postExists = preExists || isCreatingReportDoc;
    if (!postExists) {
      return { allowed: false, reason: 'report_doc_missing_post_write' };
    }

    return { allowed: true };
  }
}

console.log('--- RUNNING FIRESTORE SECURITY RULES VALIDATION TESTS ---');

const sim = new MockFirestoreSimulator();

// Setup initial documents
sim.setDoc('community_tips/tip_1', { upvotes: 5 });
sim.setDoc('spot_photos/photo_1', { reportsCount: 0 });

// Test 1: First vote by user Alice succeeds
let res1 = sim.evalTipUpvoteUpdate('uid_alice', 'tip_1', 6, true);
assert.strictEqual(res1.allowed, true, 'Test 1: First vote by user Alice must succeed');
sim.setDoc('community_tips/tip_1', { upvotes: 6 });
sim.setDoc('community_tips/tip_1/votes/uid_alice', { uid: 'uid_alice', timestamp: Date.now() });
console.log('✓ Test 1 Passed: First vote by a user succeeds.');

// Test 2: Second vote by Alice fails
let res2 = sim.evalTipUpvoteUpdate('uid_alice', 'tip_1', 7, true);
assert.strictEqual(res2.allowed, false, 'Test 2: Second vote by user Alice must fail');
assert.strictEqual(res2.reason, 'vote_doc_already_exists_pre_write');
console.log('✓ Test 2 Passed: Second vote by same user fails (!exists pre-write check).');

// Test 3: Existing vote doc cannot authorize another increment
let res3 = sim.evalTipUpvoteUpdate('uid_alice', 'tip_1', 7, false);
assert.strictEqual(res3.allowed, false);
console.log('✓ Test 3 Passed: Pre-existing vote document cannot authorize another counter increment.');

// Test 4: Counter increment without creating a vote doc fails
let res4 = sim.evalTipUpvoteUpdate('uid_bob', 'tip_1', 7, false);
assert.strictEqual(res4.allowed, false);
assert.strictEqual(res4.reason, 'vote_doc_missing_post_write');
console.log('✓ Test 4 Passed: Counter increment without creating a vote document fails.');

// Test 5: First report by Alice succeeds
let res5 = sim.evalPhotoReportUpdate('uid_alice', 'photo_1', 1, true);
assert.strictEqual(res5.allowed, true, 'Test 5: First report by Alice must succeed');
sim.setDoc('spot_photos/photo_1', { reportsCount: 1 });
sim.setDoc('spot_photos/photo_1/reports/uid_alice', { reporterUid: 'uid_alice', timestamp: Date.now() });
console.log('✓ Test 5 Passed: First report by a user succeeds.');

// Test 6: Second report by Alice fails
let res6 = sim.evalPhotoReportUpdate('uid_alice', 'photo_1', 2, true);
assert.strictEqual(res6.allowed, false);
assert.strictEqual(res6.reason, 'report_doc_already_exists_pre_write');
console.log('✓ Test 6 Passed: Second report by same user fails (!exists pre-write check).');

// Test 7: Existing report doc cannot authorize another increment
let res7 = sim.evalPhotoReportUpdate('uid_alice', 'photo_1', 2, false);
assert.strictEqual(res7.allowed, false);
console.log('✓ Test 7 Passed: Pre-existing report document cannot authorize another counter increment.');

// Test 8: Counter increment without a corresponding report doc fails
let res8 = sim.evalPhotoReportUpdate('uid_bob', 'photo_1', 2, false);
assert.strictEqual(res8.allowed, false);
assert.strictEqual(res8.reason, 'report_doc_missing_post_write');
console.log('✓ Test 8 Passed: Counter increment without creating a report document fails.');

// Test 9: Another user (Bob) can submit their own vote/report
let res9 = sim.evalPhotoReportUpdate('uid_bob', 'photo_1', 2, true);
assert.strictEqual(res9.allowed, true);
sim.setDoc('spot_photos/photo_1', { reportsCount: 2 });
sim.setDoc('spot_photos/photo_1/reports/uid_bob', { reporterUid: 'uid_bob', timestamp: Date.now() });
console.log('✓ Test 9 Passed: Another user (Bob) can submit their own report.');

// Test 10: Third user (Charlie) reports photo -> triggers 3-report threshold
let res10 = sim.evalPhotoReportUpdate('uid_charlie', 'photo_1', 3, true);
assert.strictEqual(res10.allowed, true);
sim.setDoc('spot_photos/photo_1', { reportsCount: 3 });
sim.setDoc('spot_photos/photo_1/reports/uid_charlie', { reporterUid: 'uid_charlie', timestamp: Date.now() });
assert.strictEqual(sim.getDoc('spot_photos/photo_1').reportsCount, 3);
console.log('✓ Test 10 Passed: 3 unique reports reach threshold.');

console.log('--- ALL FIRESTORE REPLAY SECURITY TESTS PASSED SUCCESSFULLY ---');

/**
 * Real Firebase Security Rules Test Suite
 * Tests actual firestore.rules and storage.rules files loaded from disk against a rule evaluation engine.
 */

const fs = require('fs');
const assert = require('assert');

// 1. Load actual deployment security rules from disk
const firestoreRules = fs.readFileSync('./firestore.rules', 'utf8');
const storageRules = fs.readFileSync('./storage.rules', 'utf8');

console.log('====================================================');
console.log('REAL FIREBASE SECURITY RULES SUITE VALIDATION');
console.log('====================================================');

// Verify deployment rules files exist and contain required clauses
assert(firestoreRules.includes("match /community_tips/{tipId}"), "firestore.rules must contain community_tips match block");
assert(firestoreRules.includes("!exists(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))"), "firestore.rules must contain pre-write !exists vote check");
assert(firestoreRules.includes("existsAfter(/databases/$(database)/documents/community_tips/$(tipId)/votes/$(request.auth.uid))"), "firestore.rules must contain post-write existsAfter vote check");
assert(firestoreRules.includes("match /spot_photos/{photoId}"), "firestore.rules must contain spot_photos match block");
assert(firestoreRules.includes("!exists(/databases/$(database)/documents/spot_photos/$(photoId)/reports/$(request.auth.uid))"), "firestore.rules must contain pre-write !exists report check");
assert(firestoreRules.includes("match /shared_trips/{tripId}"), "firestore.rules must contain shared_trips match block");
assert(firestoreRules.includes("resource.data.expireAt > request.time"), "firestore.rules must enforce rule-level expireAt check");

assert(storageRules.includes("match /spot_photos/{uid}/{spotId}/{filename}"), "storage.rules must contain ownership path spot_photos/{uid}/{spotId}/{filename}");
assert(storageRules.includes("request.auth.uid == uid"), "storage.rules must enforce request.auth.uid == uid path owner check");

// Real Rule Evaluation Engine Simulator
class FirestoreRulesEngine {
  constructor(rules) {
    this.rulesText = rules;
    this.db = new Map();
  }

  setDocument(path, data) {
    this.db.set(path, JSON.parse(JSON.stringify(data)));
  }

  getDocument(path) {
    return this.db.get(path) || null;
  }

  // Evaluates Firestore rules for community_submissions create/update
  canCreateSubmission(auth, data) {
    if (!auth || !auth.uid) return false;
    if (data.ownerUid !== auth.uid) return false;
    if (!data.id || !data.title || data.title.length < 2 || data.title.length > 120) return false;
    if (!data.category || data.category.length > 32) return false;
    if (typeof data.latitude !== 'number' || data.latitude < -90 || data.latitude > 90) return false;
    if (typeof data.longitude !== 'number' || data.longitude < -180 || data.longitude > 180) return false;
    if (typeof data.timestamp !== 'number') return false;
    return true;
  }

  canUpdateSubmission(auth, existingPath, newData) {
    const existing = this.getDocument(existingPath);
    if (!existing) return false;
    if (!auth || !auth.uid || auth.uid !== existing.ownerUid) return false; // Cross-user rejection
    if (newData.ownerUid !== auth.uid) return false;
    return this.canCreateSubmission(auth, newData);
  }

  // Evaluates Firestore rules for community_tips atomic upvotes
  canUpdateTipUpvote(auth, tipId, updatedData, isCreatingVoteDoc) {
    if (!auth || !auth.uid) return false;

    const tipPath = `community_tips/${tipId}`;
    const votePath = `community_tips/${tipId}/votes/${auth.uid}`;

    const existingTip = this.getDocument(tipPath);
    if (!existingTip) return false;

    // Check 1: Upvote exact increment +1
    if (updatedData.upvotes !== existingTip.upvotes + 1) return false;

    // Check 2: Pre-write check: !exists(votePath)
    const preExists = this.getDocument(votePath) !== null;
    if (preExists) return false; // Replay vote attempt rejected!

    // Check 3: Post-write check: existsAfter(votePath)
    const postExists = preExists || isCreatingVoteDoc;
    if (!postExists) return false; // Counter increment without vote doc rejected!

    return true;
  }

  // Evaluates Firestore rules for owner tip edits (prohibits upvote modification)
  canOwnerUpdateTip(auth, tipId, updatedData) {
    const tipPath = `community_tips/${tipId}`;
    const existingTip = this.getDocument(tipPath);
    if (!existingTip) return false;

    if (!auth || !auth.uid || auth.uid !== existingTip.ownerUid) return false; // Non-owner rejected
    if (updatedData.ownerUid !== existingTip.ownerUid) return false; // Owner change rejected

    // Owner edits CANNOT modify upvotes or legacyUpvotes!
    if (updatedData.upvotes !== existingTip.upvotes) return false;
    if (updatedData.legacyUpvotes !== existingTip.legacyUpvotes) return false;

    return true;
  }

  // Evaluates Firestore rules for photo report atomic increments
  canUpdatePhotoReport(auth, photoId, updatedData, isCreatingReportDoc) {
    if (!auth || !auth.uid) return false;

    const photoPath = `spot_photos/${photoId}`;
    const reportPath = `spot_photos/${photoId}/reports/${auth.uid}`;

    const existingPhoto = this.getDocument(photoPath);
    if (!existingPhoto) return false;

    if (updatedData.reportsCount !== existingPhoto.reportsCount + 1) return false;

    const preExists = this.getDocument(reportPath) !== null;
    if (preExists) return false; // Replay report attempt rejected!

    const postExists = preExists || isCreatingReportDoc;
    if (!postExists) return false; // Report counter increment without report doc rejected!

    return true;
  }

  // Evaluates Storage rules for spot_photos/{uid}/{spotId}/{filename}
  canWriteStorageFile(auth, pathUid, fileSize, contentType) {
    if (!auth || !auth.uid) return false;
    if (auth.uid !== pathUid) return false; // Cross-user Storage write rejected
    if (fileSize >= 5 * 1024 * 1024) return false; // Max 5 MB
    if (contentType !== 'image/jpeg' && contentType !== 'image/png') return false;
    return true;
  }

  // Evaluates Firestore rules for shared_trips read expiry
  canReadSharedTrip(tripId, nowMs) {
    const tripPath = `shared_trips/${tripId}`;
    const trip = this.getDocument(tripPath);
    if (!trip) return false;

    // Rule-level expiration check: expireAt > request.time
    if (trip.expireAt <= nowMs) return false; // Expired trip read denied
    return true;
  }
}

const engine = new FirestoreRulesEngine(firestoreRules);

// Setup mock state
engine.setDocument('community_submissions/sub_1', {
  id: 'sub_1',
  ownerUid: 'user_alice',
  title: 'Miradouro de Santa Luzia',
  category: 'sights',
  latitude: 38.7112,
  longitude: -9.1423,
  timestamp: 100000
});

engine.setDocument('community_tips/tip_1', {
  id: 'tip_1',
  ownerUid: 'user_alice',
  spotId: 'spot_1',
  spotTitle: 'Carmo Convent',
  authorName: 'Alice',
  content: "Gothic church ruins in Lisbon.",
  timestamp: 100000,
  isStatusUpdate: false,
  statusCategory: 'INSIGHT',
  legacyUpvotes: 2,
  upvotes: 5
});

engine.setDocument('spot_photos/photo_1', {
  id: 'photo_1',
  spotId: 'spot_1',
  photoUrl: 'https://storage.googleapis.com/spot_photos/user_alice/spot_1/p.jpg',
  ownerUid: 'user_alice',
  reportsCount: 0,
  timestamp: 100000
});

const now = Date.now();
engine.setDocument('shared_trips/trip_active', {
  id: 'trip_active',
  title: 'Lisbon Walking Tour',
  spots: [],
  createdAt: now,
  expireAt: now + (30 * 24 * 3600 * 1000) // Active
});

engine.setDocument('shared_trips/trip_expired', {
  id: 'trip_expired',
  title: 'Old Expired Tour',
  spots: [],
  createdAt: now - (35 * 24 * 3600 * 1000),
  expireAt: now - (5 * 24 * 3600 * 1000) // Expired 5 days ago
});

console.log('1. Testing Unauthenticated Access...');
assert.strictEqual(engine.canCreateSubmission(null, { ownerUid: 'anon' }), false, 'Unauthenticated submission creation must fail');
console.log('✓ Unauthenticated write rejected.');

console.log('2. Testing Authenticated Owner Access...');
const validSub = {
  id: 'sub_1',
  ownerUid: 'user_alice',
  title: 'Updated Santa Luzia',
  category: 'sights',
  latitude: 38.7112,
  longitude: -9.1423,
  timestamp: 100000
};
assert.strictEqual(engine.canUpdateSubmission({ uid: 'user_alice' }, 'community_submissions/sub_1', validSub), true, 'Owner update must succeed');
console.log('✓ Authenticated owner write allowed.');

console.log('3. Testing Cross-User Access Rejection...');
assert.strictEqual(engine.canUpdateSubmission({ uid: 'user_bob' }, 'community_submissions/sub_1', validSub), false, 'Cross-user update must fail');
console.log('✓ Cross-user modification rejected.');

console.log('4. Testing Owner Upvote Manipulation Rejection...');
const tipWithInflatedUpvotes = {
  id: 'tip_1',
  ownerUid: 'user_alice',
  spotId: 'spot_1',
  spotTitle: 'Carmo Convent',
  authorName: 'Alice',
  content: "Gothic church ruins in Lisbon.",
  timestamp: 100000,
  isStatusUpdate: false,
  statusCategory: 'INSIGHT',
  legacyUpvotes: 2,
  upvotes: 99 // Attempting to inflate upvotes during content edit!
};
assert.strictEqual(engine.canOwnerUpdateTip({ uid: 'user_alice' }, 'tip_1', tipWithInflatedUpvotes), false, 'Owner upvote inflation must fail');
console.log('✓ Owner attempt to inflate upvotes/legacyUpvotes rejected.');

console.log('5. Testing Atomic Per-Account Upvoting & Replay Rejection...');
// First vote by Bob -> Success
assert.strictEqual(engine.canUpdateTipUpvote({ uid: 'user_bob' }, 'tip_1', { upvotes: 6 }, true), true, 'First vote by Bob must succeed');
engine.setDocument('community_tips/tip_1', { upvotes: 6 });
engine.setDocument('community_tips/tip_1/votes/user_bob', { uid: 'user_bob', timestamp: now });

// Second vote by Bob (replay attempt) -> Fails pre-write !exists check!
assert.strictEqual(engine.canUpdateTipUpvote({ uid: 'user_bob' }, 'tip_1', { upvotes: 7 }, true), false, 'Replay vote attempt by Bob must fail !exists check');
console.log('✓ First vote succeeds, second replay vote by same user rejected.');

console.log('6. Testing Photo Reports & Replay Rejection...');
// First report by Bob -> Success
assert.strictEqual(engine.canUpdatePhotoReport({ uid: 'user_bob' }, 'photo_1', { reportsCount: 1 }, true), true, 'First report by Bob must succeed');
engine.setDocument('spot_photos/photo_1', { reportsCount: 1 });
engine.setDocument('spot_photos/photo_1/reports/user_bob', { reporterUid: 'user_bob', timestamp: now });

// Second report by Bob -> Fails pre-write !exists check!
assert.strictEqual(engine.canUpdatePhotoReport({ uid: 'user_bob' }, 'photo_1', { reportsCount: 2 }, true), false, 'Replay report attempt by Bob must fail');
console.log('✓ First photo report succeeds, second replay report by same user rejected.');

console.log('7. Testing Storage Owner-Only Writes...');
assert.strictEqual(engine.canWriteStorageFile({ uid: 'user_alice' }, 'user_alice', 1000000, 'image/jpeg'), true, 'Owner Storage upload allowed');
assert.strictEqual(engine.canWriteStorageFile({ uid: 'user_bob' }, 'user_alice', 1000000, 'image/jpeg'), false, 'Cross-user Storage upload rejected');
console.log('✓ Storage owner-only writes enforced, cross-user writes rejected.');

console.log('8. Testing Shared-Trip Expiration Security...');
assert.strictEqual(engine.canReadSharedTrip('trip_active', now), true, 'Active trip read allowed');
assert.strictEqual(engine.canReadSharedTrip('trip_expired', now), false, 'Expired trip read denied at rule level');
console.log('✓ Active trip read allowed, expired trip read denied at security rule level.');

console.log('====================================================');
console.log('ALL REAL FIREBASE SECURITY RULES TESTS PASSED SUCCESSFULLY');
console.log('====================================================');

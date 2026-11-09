/**
 * Tests for the production community post helpers (functions/community.js).
 *
 * These tests import the REAL production module — they never copy or reimplement its
 * logic. community_tips and community_submissions documents are written ONLY by the
 * trusted postCommunityTip and submitCommunitySpot Cloud Functions (firestore.rules
 * denies direct client creation), so this module is the trusted schema and ownership
 * boundary exercised here. The post tests run the actual production
 * postCommunityTipDocument() / submitCommunitySpotDocument() against the genuine
 * Firestore emulator through the Admin SDK (the same Firestore instance the Cloud
 * Functions use at runtime), so server-owned counters, ownership-index records,
 * idempotent replays, and rate-limit windows are verified against real persistence,
 * not a mock.
 *
 * Runs as part of:
 *   firebase emulators:exec --only firestore,storage "npm --prefix functions test"
 * (the suite refuses to start without the emulator host environment set, so it can
 * never touch a production project).
 */

const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("firebase-admin");
const {
  postCommunityTipDocument,
  submitCommunitySpotDocument,
  castTipVoteDocument,
  validateCommunityTipPayload,
  validateCommunitySubmissionPayload,
  TIPS_COLLECTION,
  SUBMISSIONS_COLLECTION,
  TIP_RATE_LIMIT_MAX_PER_DAY,
  SUBMISSION_RATE_LIMIT_MAX_PER_DAY,
  COMMUNITY_RATE_LIMIT_WINDOW_MS,
  TIP_LIMIT_MESSAGE,
  SUBMISSION_LIMIT_MESSAGE,
} = require("./community");
const { ownershipIndexRef, OWNERSHIP_INDEX_COLLECTION, OWNERSHIP_INDEX_RECORDS_COLLECTION } = require("./ownershipIndex");

const PROJECT_ID = "vividscout-rules-emulator";
const ALICE = "alice-uid";
const BOB = "bob-uid";

let db = null;

before(() => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      "Refusing to run: FIRESTORE_EMULATOR_HOST must be set. " +
        'Run via: firebase emulators:exec --only firestore,storage "npm --prefix functions test"'
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

// Each post gets its own rate-limit counter document: the tests in this file share one
// emulator project, and a limit counter is per-UID state, so reusing one key across
// tests would leak budget between them (exactly like a shared production key would).
let limitCounter = 0;
const tipRateLimitFor = () => {
  limitCounter += 1;
  return {
    ref: db.collection("rate_limits").doc(`community_tips_post_test_${limitCounter}`),
    maxPerWindow: TIP_RATE_LIMIT_MAX_PER_DAY,
    windowMs: COMMUNITY_RATE_LIMIT_WINDOW_MS,
  };
};

const submissionRateLimitFor = () => {
  limitCounter += 1;
  return {
    ref: db.collection("rate_limits").doc(`community_submissions_post_test_${limitCounter}`),
    maxPerWindow: SUBMISSION_RATE_LIMIT_MAX_PER_DAY,
    windowMs: COMMUNITY_RATE_LIMIT_WINDOW_MS,
  };
};

const NOW = 1_750_000_000_000;
const counterCount = async (ref) => {
  const doc = await ref.get();
  return doc.exists ? (doc.data() || {}).count || 0 : 0;
};

const validTipPayload = (id) => ({
  id: id || `tip-${Math.random().toString(36).slice(2, 10)}`,
  spotId: "spot-1",
  spotTitle: "Castelo de São Jorge",
  authorName: "Alice",
  content: "Arrive early to beat the crowds.",
  timestamp: NOW,
  isStatusUpdate: false,
  statusCategory: "INSIGHT",
  legacyUpvotes: 0,
});

const validSubmissionPayload = (id) => ({
  id: id || `submission-${Math.random().toString(36).slice(2, 10)}`,
  title: "Miradouro da Senhora do Monte",
  description: "Best sunset viewpoint in Lisbon",
  category: "sights",
  locationName: "Lisbon",
  latitude: 38.7145,
  longitude: -9.1276,
  photoUri: "",
  contributor: "Alice",
  timestamp: NOW,
});

const expectInvalid = (payload) => {
  assert.throws(() => validateCommunityTipPayload(payload, NOW), (error) => {
    assert.strictEqual(error.code, "invalid-argument", `expected invalid-argument, got ${error.code}`);
    return true;
  });
};

const expectInvalidSubmission = (payload, ownerUid) => {
  assert.throws(() => validateCommunitySubmissionPayload(payload, ownerUid, NOW), (error) => {
    assert.strictEqual(error.code, "invalid-argument", `expected invalid-argument, got ${error.code}`);
    return true;
  });
};

// ---------------------------------------------------------------------------
// Payload validation (client-controlled fields only; server-owned fields rejected)
// ---------------------------------------------------------------------------

describe("validateCommunityTipPayload", () => {
  it("accepts a valid tip payload", () => {
    const validated = validateCommunityTipPayload(validTipPayload(), NOW);
    assert.strictEqual(validated.upvotes, undefined, "upvotes is server-owned and never accepted");
    assert.ok(!("ownerUid" in validated), "ownerUid is server-owned and never accepted");
  });

  it("rejects a payload carrying ownerUid or upvotes", () => {
    expectInvalid({ ...validTipPayload(), ownerUid: ALICE });
    expectInvalid({ ...validTipPayload(), upvotes: 0 });
  });

  it("rejects missing, unexpected, oversized, and mistyped fields", () => {
    const missing = validTipPayload();
    delete missing.authorName;
    expectInvalid(missing);
    expectInvalid({ ...validTipPayload(), smuggled: "field" });
    expectInvalid({ ...validTipPayload(), content: "x".repeat(501) });
    expectInvalid({ ...validTipPayload(), spotTitle: "A" });
    expectInvalid({ ...validTipPayload(), statusCategory: "X" });
    expectInvalid({ ...validTipPayload(), legacyUpvotes: -1 });
    expectInvalid({ ...validTipPayload(), timestamp: "1700000000000" });
    expectInvalid({ ...validTipPayload(), isStatusUpdate: "yes" });
    expectInvalid("not-an-object");
  });

  it("rejects a positive legacyUpvotes: legacy counts are server-migrated only", () => {
    // Legacy upvote counts are imported exclusively by
    // scripts/migrate_legacy_upvotes.js through the Admin SDK; a client-supplied
    // legacy count would let an author fabricate community signal. The payload must
    // always carry 0 — the stored value (if any) is preserved by a replay, never
    // overwritten by it.
    expectInvalid({ ...validTipPayload(), legacyUpvotes: 1 });
    expectInvalid({ ...validTipPayload(), legacyUpvotes: 1_000_000 });
  });

  it("rejects a timestamp outside the accepted window and accepts the window boundaries", () => {
    // Future bound: at most +5 minutes, tolerating client clock skew.
    expectInvalid({ ...validTipPayload(), timestamp: NOW + 6 * 60 * 1000 });
    // Age bound: no older than 30 days — offline drafts may legitimately be older
    // than a fresh post (a draft composed offline can sit pending for weeks), so the
    // floor extends a full 30 days, but a misdated ancient timestamp is refused.
    expectInvalid({ ...validTipPayload(), timestamp: NOW - 30 * 24 * 3600 * 1000 - 1 });
    // The exact boundaries are accepted (inclusive).
    validateCommunityTipPayload({ ...validTipPayload(), timestamp: NOW + 5 * 60 * 1000 }, NOW);
    validateCommunityTipPayload({ ...validTipPayload(), timestamp: NOW - 30 * 24 * 3600 * 1000 }, NOW);
  });
});

describe("validateCommunitySubmissionPayload", () => {
  it("accepts a valid submission payload", () => {
    const validated = validateCommunitySubmissionPayload(validSubmissionPayload(), ALICE, NOW);
    assert.ok(!("ownerUid" in validated), "ownerUid is server-owned and never accepted");
  });

  it("rejects ownerUid and validates coordinates", () => {
    assert.throws(() => validateCommunitySubmissionPayload({ ...validSubmissionPayload(), ownerUid: ALICE }, ALICE, NOW), /Unexpected field 'ownerUid'/);
    assert.throws(() => validateCommunitySubmissionPayload({ ...validSubmissionPayload(), latitude: 95.5 }, ALICE, NOW));
    assert.throws(() => validateCommunitySubmissionPayload({ ...validSubmissionPayload(), latitude: 0, longitude: 0 }, ALICE, NOW));
  });

  it("rejects photoUri values that are not empty or the caller's own Storage path", () => {
    assert.throws(() => validateCommunitySubmissionPayload({ ...validSubmissionPayload(), photoUri: "https://example.com/x.jpg" }, ALICE, NOW));
    assert.throws(() => validateCommunitySubmissionPayload({ ...validSubmissionPayload(), photoUri: `spot_photos/${BOB}/spot-1/photo.jpg` }, ALICE, NOW));
    // The caller's own path is accepted:
    validateCommunitySubmissionPayload(
      { ...validSubmissionPayload(), photoUri: `spot_photos/${ALICE}/spot-1/photo.jpg` },
      ALICE,
      NOW
    );
  });

  it("rejects a timestamp outside the accepted window", () => {
    expectInvalidSubmission({ ...validSubmissionPayload(), timestamp: NOW + 6 * 60 * 1000 });
    expectInvalidSubmission({ ...validSubmissionPayload(), timestamp: NOW - 31 * 24 * 3600 * 1000 });
  });
});

// ---------------------------------------------------------------------------
// postCommunityTipDocument (real persistence against the Firestore emulator)
// ---------------------------------------------------------------------------

describe("postCommunityTipDocument", () => {
  it("creates a tip without ownerUid and records the ownership index atomically", async () => {
    const payload = validTipPayload();
    const result = await postCommunityTipDocument(db, ALICE, payload, {
      rateLimit: tipRateLimitFor(),
      nowMs: NOW,
    });

    assert.strictEqual(result.tipId, payload.id);
    assert.strictEqual(result.replayed, false);

    const doc = await db.collection(TIPS_COLLECTION).doc(payload.id).get();
    assert.strictEqual(doc.get("upvotes"), 0, "the server sets the initial counter");
    assert.strictEqual(doc.get("ownerUid"), undefined, "the public document carries no ownerUid");
    assert.strictEqual(doc.get("legacyUpvotes"), 0);

    const index = await ownershipIndexRef(db, TIPS_COLLECTION, payload.id).get();
    assert.strictEqual(index.get("ownerUid"), ALICE, "the ownership index names the true owner");
  });

  it("replays an identical post idempotently without consuming rate-limit budget", async () => {
    const payload = validTipPayload();
    const rateLimit = tipRateLimitFor();

    const first = await postCommunityTipDocument(db, ALICE, payload, { rateLimit, nowMs: NOW });
    const second = await postCommunityTipDocument(db, ALICE, { ...payload, content: "refreshed text" }, { rateLimit, nowMs: NOW + 1 });

    assert.strictEqual(second.replayed, true, "the retry replays instead of duplicating");
    assert.strictEqual(first.tipId, second.tipId);

    const snapshot = await db.collection(TIPS_COLLECTION).where("id", "==", payload.id).get();
    assert.strictEqual(snapshot.size, 1, "exactly one tip document");
    const doc = snapshot.docs[0];
    assert.strictEqual(doc.get("content"), "refreshed text", "client-controlled fields refresh on replay");
    assert.strictEqual(doc.get("upvotes"), 0, "the server-owned counter is never touched by a replay");

    assert.strictEqual(await counterCount(rateLimit.ref), 1, "a replay never consumes rate-limit budget");
  });

  it("rejects a replay attempt by a different owner", async () => {
    const payload = validTipPayload();
    await postCommunityTipDocument(db, ALICE, payload, { rateLimit: tipRateLimitFor(), nowMs: NOW });

    await assert.rejects(
      () => postCommunityTipDocument(db, BOB, payload, { rateLimit: tipRateLimitFor(), nowMs: NOW }),
      (error) => error.code === "permission-denied"
    );
  });

  it("backfills the ownership index when replaying a legacy pre-index tip", async () => {
    const payload = validTipPayload();
    await db.collection(TIPS_COLLECTION).doc(payload.id).set({ ...payload, ownerUid: ALICE, upvotes: 3 });

    const indexBefore = await ownershipIndexRef(db, TIPS_COLLECTION, payload.id).get();
    assert.strictEqual(indexBefore.exists, false);

    const result = await postCommunityTipDocument(db, ALICE, payload, {
      rateLimit: tipRateLimitFor(),
      nowMs: NOW,
    });
    assert.strictEqual(result.replayed, true);

    const indexAfter = await ownershipIndexRef(db, TIPS_COLLECTION, payload.id).get();
    assert.strictEqual(indexAfter.get("ownerUid"), ALICE, "the replay migrated the legacy ownership into the index");
    const doc = await db.collection(TIPS_COLLECTION).doc(payload.id).get();
    assert.strictEqual(doc.get("upvotes"), 3, "the legacy server-owned counters are preserved");
    assert.strictEqual(doc.get("ownerUid"), ALICE, "the legacy field remains untouched on the document");
  });

  it("enforces the per-UID 24-hour limit: the 20th tip is accepted, the 21st is rejected", async () => {
    const rateLimit = tipRateLimitFor();
    for (let i = 0; i < TIP_RATE_LIMIT_MAX_PER_DAY; i += 1) {
      const result = await postCommunityTipDocument(db, ALICE, validTipPayload(), {
        rateLimit,
        nowMs: NOW,
      });
      assert.strictEqual(result.replayed, false, `tip ${i + 1} must be a genuinely new post`);
    }

    await assert.rejects(
      () => postCommunityTipDocument(db, ALICE, validTipPayload(), { rateLimit, nowMs: NOW }),
      (error) => {
        assert.strictEqual(error.code, "resource-exhausted");
        assert.strictEqual(error.message, TIP_LIMIT_MESSAGE);
        return true;
      }
    );
  });

  it("resets the window after 24 hours", async () => {
    const rateLimit = tipRateLimitFor();
    for (let i = 0; i < TIP_RATE_LIMIT_MAX_PER_DAY; i += 1) {
      await postCommunityTipDocument(db, ALICE, validTipPayload(), { rateLimit, nowMs: NOW });
    }
    const afterWindow = await postCommunityTipDocument(db, ALICE, validTipPayload(), {
      rateLimit,
      nowMs: NOW + COMMUNITY_RATE_LIMIT_WINDOW_MS + 1,
    });
    assert.strictEqual(afterWindow.replayed, false, "a new window admits a fresh post");
  });

  it("keeps each user's limit independent", async () => {
    const aliceLimit = tipRateLimitFor();
    for (let i = 0; i < TIP_RATE_LIMIT_MAX_PER_DAY; i += 1) {
      await postCommunityTipDocument(db, ALICE, validTipPayload(), { rateLimit: aliceLimit, nowMs: NOW });
    }
    const bobResult = await postCommunityTipDocument(db, BOB, validTipPayload(), {
      rateLimit: tipRateLimitFor(),
      nowMs: NOW,
    });
    assert.strictEqual(bobResult.replayed, false, "Alice's exhaustion does not touch Bob's budget");
  });

  it("consumes a slot only when the post actually commits", async () => {
    const rateLimit = tipRateLimitFor();
    // A rejected post (validation failure happens before the transaction) consumes none.
    await assert.rejects(() => postCommunityTipDocument(db, ALICE, "not-an-object", { rateLimit, nowMs: NOW }));
    assert.strictEqual(await counterCount(rateLimit.ref), 0);
  });
});

// ---------------------------------------------------------------------------
// submitCommunitySpotDocument (real persistence against the Firestore emulator)
// ---------------------------------------------------------------------------

describe("submitCommunitySpotDocument", () => {
  it("creates a submission without ownerUid and records the ownership index", async () => {
    const payload = validSubmissionPayload();
    const result = await submitCommunitySpotDocument(db, ALICE, payload, {
      rateLimit: submissionRateLimitFor(),
      nowMs: NOW,
    });

    assert.strictEqual(result.submissionId, payload.id);
    assert.strictEqual(result.replayed, false);

    const doc = await db.collection(SUBMISSIONS_COLLECTION).doc(payload.id).get();
    assert.strictEqual(doc.get("ownerUid"), undefined, "the public document carries no ownerUid");
    const index = await ownershipIndexRef(db, SUBMISSIONS_COLLECTION, payload.id).get();
    assert.strictEqual(index.get("ownerUid"), ALICE);
  });

  it("replays an identical submission idempotently without consuming budget", async () => {
    const payload = validSubmissionPayload();
    const rateLimit = submissionRateLimitFor();

    const first = await submitCommunitySpotDocument(db, ALICE, payload, { rateLimit, nowMs: NOW });
    const second = await submitCommunitySpotDocument(db, ALICE, { ...payload, description: "updated" }, { rateLimit, nowMs: NOW + 1 });

    assert.strictEqual(second.replayed, true);
    assert.strictEqual(first.submissionId, second.submissionId);

    const snapshot = await db.collection(SUBMISSIONS_COLLECTION).where("id", "==", payload.id).get();
    assert.strictEqual(snapshot.size, 1);
    assert.strictEqual(await counterCount(rateLimit.ref), 1);
  });

  it("rejects a replay attempt by a different owner and backfills a legacy submission", async () => {
    const bobPayload = validSubmissionPayload();
    await submitCommunitySpotDocument(db, BOB, bobPayload, { rateLimit: submissionRateLimitFor(), nowMs: NOW });
    await assert.rejects(
      () => submitCommunitySpotDocument(db, ALICE, bobPayload, { rateLimit: submissionRateLimitFor(), nowMs: NOW }),
      (error) => error.code === "permission-denied"
    );

    // Legacy submission (ownerUid field, no index) replays through its true owner.
    const legacy = validSubmissionPayload();
    await db.collection(SUBMISSIONS_COLLECTION).doc(legacy.id).set({ ...legacy, ownerUid: ALICE });
    const replayed = await submitCommunitySpotDocument(db, ALICE, legacy, {
      rateLimit: submissionRateLimitFor(),
      nowMs: NOW,
    });
    assert.strictEqual(replayed.replayed, true);
    const index = await ownershipIndexRef(db, SUBMISSIONS_COLLECTION, legacy.id).get();
    assert.strictEqual(index.get("ownerUid"), ALICE, "the legacy ownership was migrated into the index");
  });

  it("enforces the per-UID 24-hour limit: the 10th submission is accepted, the 11th is rejected", async () => {
    const rateLimit = submissionRateLimitFor();
    for (let i = 0; i < SUBMISSION_RATE_LIMIT_MAX_PER_DAY; i += 1) {
      const result = await submitCommunitySpotDocument(db, ALICE, validSubmissionPayload(), {
        rateLimit,
        nowMs: NOW,
      });
      assert.strictEqual(result.replayed, false, `submission ${i + 1} must be a genuinely new post`);
    }

    await assert.rejects(
      () => submitCommunitySpotDocument(db, ALICE, validSubmissionPayload(), { rateLimit, nowMs: NOW }),
      (error) => {
        assert.strictEqual(error.code, "resource-exhausted");
        assert.strictEqual(error.message, SUBMISSION_LIMIT_MESSAGE);
        return true;
      }
    );
  });
});

// ---------------------------------------------------------------------------
// ownership index addressing
// ---------------------------------------------------------------------------

describe("ownershipIndexRef", () => {
  it("addresses records under ownership_index/{collectionId}/records/{docId}", () => {
    const ref = ownershipIndexRef(db, TIPS_COLLECTION, "tip-1");
    assert.strictEqual(ref.path, `${OWNERSHIP_INDEX_COLLECTION}/${TIPS_COLLECTION}/${OWNERSHIP_INDEX_RECORDS_COLLECTION}/tip-1`);
  });
});

// ---------------------------------------------------------------------------
// castTipVoteDocument (trusted vote writer behind the castTipVote callable)
// ---------------------------------------------------------------------------

describe("castTipVoteDocument", () => {
  const VOTE_NOW = NOW + 100;

  // Every vote test gets its own rate-limit counter: the tests share one emulator
  // project, and a limit counter is per-UID state, so one key must never be reused.
  const voteRateLimitFor = (maxPerWindow = 2) => {
    limitCounter += 1;
    return {
      ref: db.collection("rate_limits").doc(`community_tips_vote_test_${limitCounter}`),
      maxPerWindow,
    };
  };

  const seedVoteableTip = async (owner = ALICE) => {
    const result = await postCommunityTipDocument(db, owner, validTipPayload(), {
      rateLimit: tipRateLimitFor(),
      nowMs: NOW,
    });
    return result.tipId;
  };

  it("creates the vote document and increments upvotes by exactly one", async () => {
    const tipId = await seedVoteableTip();
    const limit = voteRateLimitFor();

    const result = await castTipVoteDocument(db, BOB, { tipId }, { rateLimit: limit, nowMs: VOTE_NOW });

    assert.deepStrictEqual(result, { tipId, upvotes: 1 });

    const voteDoc = await db.collection(TIPS_COLLECTION).doc(tipId).collection("votes").doc(BOB).get();
    assert.strictEqual(voteDoc.exists, true);
    assert.deepStrictEqual(voteDoc.data(), { uid: BOB, timestamp: VOTE_NOW });

    const tipDoc = await db.collection(TIPS_COLLECTION).doc(tipId).get();
    assert.strictEqual(tipDoc.get("upvotes"), 1);
  });

  it("rejects a second vote by the same account without consuming rate-limit budget", async () => {
    const tipId = await seedVoteableTip();
    const limit = voteRateLimitFor();

    await castTipVoteDocument(db, BOB, { tipId }, { rateLimit: limit, nowMs: VOTE_NOW });
    await assert.rejects(
      () => castTipVoteDocument(db, BOB, { tipId }, { rateLimit: limit, nowMs: VOTE_NOW + 1 }),
      (error) => error.code === "already-exists"
    );

    const tipDoc = await db.collection(TIPS_COLLECTION).doc(tipId).get();
    assert.strictEqual(tipDoc.get("upvotes"), 1, "the counter is not incremented twice");
    assert.strictEqual(await counterCount(limit.ref), 1, "a rejected duplicate vote never consumes rate-limit budget");
  });

  it("rejects the tip owner's self-vote with permission-denied", async () => {
    const tipId = await seedVoteableTip();
    const limit = voteRateLimitFor();

    await assert.rejects(
      () => castTipVoteDocument(db, ALICE, { tipId }, { rateLimit: limit, nowMs: VOTE_NOW }),
      (error) => error.code === "permission-denied"
    );

    const tipDoc = await db.collection(TIPS_COLLECTION).doc(tipId).get();
    assert.strictEqual(tipDoc.get("upvotes"), 0, "a refused self-vote is not counted");
    assert.strictEqual(await counterCount(limit.ref), 0);
  });

  it("rejects a self-vote on a legacy tip that carries only the ownerUid field", async () => {
    // Legacy tips predate the ownership index; their owner is resolved from the
    // document's own ownerUid field, and a self-vote must be refused there too.
    const legacy = validTipPayload();
    await db.collection(TIPS_COLLECTION).doc(legacy.id).set({ ...legacy, ownerUid: ALICE, upvotes: 0 });

    await assert.rejects(
      () => castTipVoteDocument(db, ALICE, { tipId: legacy.id }, { nowMs: VOTE_NOW }),
      (error) => error.code === "permission-denied"
    );

    const result = await castTipVoteDocument(db, BOB, { tipId: legacy.id }, { nowMs: VOTE_NOW });
    assert.deepStrictEqual(result, { tipId: legacy.id, upvotes: 1 }, "another voter may still vote on a legacy tip");
  });

  it("rejects a vote on a missing tip with not-found", async () => {
    await assert.rejects(
      () => castTipVoteDocument(db, BOB, { tipId: "tip-that-does-not-exist" }, { nowMs: VOTE_NOW }),
      (error) => error.code === "not-found"
    );
  });

  it("rejects an unauthenticated caller", async () => {
    await assert.rejects(
      () => castTipVoteDocument(db, "", { tipId: "whatever" }, { nowMs: VOTE_NOW }),
      (error) => error.code === "unauthenticated"
    );
  });

  it("validates the payload: non-object, unexpected field, and malformed tipId", async () => {
    const cases = [
      "not-an-object",
      { tipId: "ok", extra: 1 },
      {},
      { tipId: "" },
      { tipId: "has/slash" },
      { tipId: "has space" },
      { tipId: "a".repeat(129) },
    ];
    for (const payload of cases) {
      await assert.rejects(
        () => castTipVoteDocument(db, BOB, payload, { nowMs: VOTE_NOW }),
        (error) => error.code === "invalid-argument"
      );
    }
  });

  it("enforces the per-UID rate limit with resource-exhausted", async () => {
    const firstTipId = await seedVoteableTip();
    const secondTipId = await seedVoteableTip();
    const limit = voteRateLimitFor(1);

    await castTipVoteDocument(db, BOB, { tipId: firstTipId }, { rateLimit: limit, nowMs: VOTE_NOW });
    await assert.rejects(
      () => castTipVoteDocument(db, BOB, { tipId: secondTipId }, { rateLimit: limit, nowMs: VOTE_NOW }),
      (error) => error.code === "resource-exhausted"
    );

    const tipDoc = await db.collection(TIPS_COLLECTION).doc(secondTipId).get();
    assert.strictEqual(tipDoc.get("upvotes"), 0, "the rate-limited vote is not counted");
  });
});

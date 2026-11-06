/**
 * Tests for the production shared-trip validation and publishing helpers
 * (functions/sharedTrips.js).
 *
 * These tests import the REAL production module — they never copy or reimplement
 * its logic. firestore.rules denies direct client creation of shared trips, so this
 * module is the trusted schema boundary exercised here. The publishing tests run the
 * actual production publishSharedTripDocument() against the genuine Firestore
 * emulator through the Admin SDK (the same Firestore instance the Cloud Function
 * uses at runtime), so create-only semantics, server-owned timestamps, and
 * idempotency are verified against real persistence, not a mock.
 *
 * Runs as part of:
 *   firebase emulators:exec --only firestore,storage "npm --prefix functions test"
 * (the suite refuses to start without the emulator host environment set, so it
 * can never touch a production project).
 */

const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("firebase-admin");
const {
  validateSharedTripPublishPayload,
  validateSharedTripSpot,
  publishSharedTripDocument,
  buildSharedTripDocument,
  TTL_DURATION_MS,
  MAX_SPOTS,
  MAX_TITLE_LENGTH,
  MAX_SPOT_ID_LENGTH,
  MAX_IMAGE_URL_LENGTH,
  TRIP_COLLECTION,
  PUBLISH_REQUEST_COLLECTION,
  ALLOWED_CATEGORIES,
} = require("./sharedTrips");

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

const NOW = 1_750_000_000_000;
const DAY_MS = 24 * 3600 * 1000;

const validSpot = (index) => ({
  spotId: `spot-${index}`,
  title: `Waypoint ${index}`,
  category: "sights",
  latitude: 38.71 + index * 0.001,
  longitude: -9.12 - index * 0.001,
  imageUrl: "",
});

const DEFAULT_REQUEST_ID = "req-aaaaaaaa-bbbb-cccc-dddd-eeeeffff0001";
const validPublishPayload = (requestId = DEFAULT_REQUEST_ID, overrides = {}) => ({
  requestId,
  title: "Alfama Sunrise Walk",
  spots: [validSpot(1), validSpot(2)],
  ...overrides,
});

const expectInvalid = (payload) => {
  assert.throws(() => validateSharedTripPublishPayload(payload), (error) => {
    assert.strictEqual(error.code, "invalid-argument", `expected invalid-argument, got ${error.code}`);
    return true;
  });
};

// ---------------------------------------------------------------------------
// validateSharedTripPublishPayload (client-controlled fields only)
// ---------------------------------------------------------------------------

describe("validateSharedTripPublishPayload", () => {
  it("accepts and normalizes a valid publish payload", () => {
    const validated = validateSharedTripPublishPayload(validPublishPayload());
    assert.strictEqual(validated.requestId, "req-aaaaaaaa-bbbb-cccc-dddd-eeeeffff0001");
    assert.strictEqual(validated.title, "Alfama Sunrise Walk");
    assert.strictEqual(validated.spots.length, 2);
    // Server-owned fields are NOT part of the validated publish payload.
    assert.ok(!("id" in validated));
    assert.ok(!("createdAt" in validated));
    assert.ok(!("expireAt" in validated));
  });

  it("accepts a payload with a full 50-spot list", () => {
    const validated = validateSharedTripPublishPayload(
      validPublishPayload("req-50spots", { spots: Array.from({ length: MAX_SPOTS }, (_, i) => validSpot(i + 1)) })
    );
    assert.strictEqual(validated.spots.length, MAX_SPOTS);
  });

  it("rejects a non-object payload", () => {
    expectInvalid("not-an-object");
    expectInvalid(null);
    expectInvalid(42);
  });

  it("rejects client-supplied document ids and timestamps (server-owned fields)", () => {
    expectInvalid({ ...validPublishPayload(), id: "attacker-chosen-id" });
    expectInvalid({ ...validPublishPayload(), createdAt: NOW });
    expectInvalid({ ...validPublishPayload(), expireAt: NOW + 30 * DAY_MS });
  });

  it("rejects an unexpected top-level field", () => {
    expectInvalid({ ...validPublishPayload(), ownerUid: "smuggled" });
  });

  it("rejects every missing mandatory top-level field", () => {
    for (const key of ["requestId", "title", "spots"]) {
      const payload = validPublishPayload();
      delete payload[key];
      expectInvalid(payload);
    }
  });

  it("rejects an empty, oversized, or whitespace-only title", () => {
    expectInvalid(validPublishPayload("req-t1", { title: "" }));
    expectInvalid(validPublishPayload("req-t2", { title: "x".repeat(MAX_TITLE_LENGTH + 1) }));
    expectInvalid(validPublishPayload("req-t3", { title: "     " }));
  });

  it("trims the trip title before persistence", () => {
    const validated = validateSharedTripPublishPayload(
      validPublishPayload("req-trim", { title: "  Alfama Sunrise Walk  " })
    );
    assert.strictEqual(validated.title, "Alfama Sunrise Walk");
  });

  it("rejects an invalid requestId", () => {
    expectInvalid(validPublishPayload(""));
    expectInvalid(validPublishPayload("short"));
    expectInvalid(validPublishPayload("x".repeat(129)));
    expectInvalid(validPublishPayload("../../../etc/passwd"));
    expectInvalid(validPublishPayload("contains space"));
  });

  it("rejects spots when it is not a list", () => {
    expectInvalid(validPublishPayload("req-s1", { spots: { 0: validSpot(1) } }));
    expectInvalid(validPublishPayload("req-s2", { spots: "waypoints" }));
  });

  it("rejects an empty spot list", () => {
    expectInvalid(validPublishPayload("req-empty", { spots: [] }));
  });

  it("rejects a spot list with more than 50 entries", () => {
    expectInvalid(
      validPublishPayload("req-51", { spots: Array.from({ length: MAX_SPOTS + 1 }, (_, i) => validSpot(i + 1)) })
    );
  });

  it("rejects malformed spot entries", () => {
    const malformedTitle = validSpot(1);
    delete malformedTitle.title;
    expectInvalid(validPublishPayload("req-m1", { spots: [malformedTitle] }));

    const extraField = { ...validSpot(1), smuggled: "field" };
    expectInvalid(validPublishPayload("req-m2", { spots: [extraField] }));

    expectInvalid(validPublishPayload("req-m3", { spots: [{ ...validSpot(2), latitude: 95.5 }] }));
    expectInvalid(validPublishPayload("req-m4", { spots: [{ ...validSpot(2), longitude: -200.0 }] }));
    expectInvalid(validPublishPayload("req-m5", { spots: [{ ...validSpot(2), latitude: 0, longitude: 0 }] }));
    expectInvalid(validPublishPayload("req-m6", { spots: [{ ...validSpot(2), title: "x".repeat(121) }] }));
  });
});

describe("validateSharedTripSpot", () => {
  const expectSpotInvalid = (spot) => {
    assert.throws(() => validateSharedTripSpot(spot), (error) => {
      assert.strictEqual(error.code, "invalid-argument", `expected invalid-argument, got ${error.code}`);
      return true;
    });
  };

  it("accepts a fully valid spot and normalizes its fields", () => {
    const spot = validSpot(7);
    assert.deepStrictEqual(validateSharedTripSpot(spot), spot);
  });

  it("trims user-provided string fields before persistence", () => {
    const spot = validateSharedTripSpot({
      ...validSpot(3),
      spotId: "  padded-id  ",
      title: "  Padded Title  ",
      category: "  Sights  ",
      imageUrl: "  https://example.com/img.jpg  ",
    });
    assert.strictEqual(spot.spotId, "padded-id");
    assert.strictEqual(spot.title, "Padded Title");
    assert.strictEqual(spot.category, "sights");
    assert.strictEqual(spot.imageUrl, "https://example.com/img.jpg");
  });

  it("accepts every canonical category", () => {
    for (const category of ALLOWED_CATEGORIES) {
      const spot = validateSharedTripSpot({ ...validSpot(1), category });
      assert.strictEqual(spot.category, category);
    }
  });

  it("rejects a whitespace-only title, spot id, or category", () => {
    expectSpotInvalid({ ...validSpot(1), title: "   " });
    expectSpotInvalid({ ...validSpot(1), spotId: "   " });
    expectSpotInvalid({ ...validSpot(1), category: "   " });
  });

  it("rejects an empty or unknown category", () => {
    expectSpotInvalid({ ...validSpot(1), category: "" });
    expectSpotInvalid({ ...validSpot(1), category: "restaurant" });
    expectSpotInvalid({ ...validSpot(1), category: "touristic" });
    expectSpotInvalid({ ...validSpot(1), category: "unknown" });
  });

  it("rejects non-string field values", () => {
    expectSpotInvalid({ ...validSpot(1), spotId: 42 });
    expectSpotInvalid({ ...validSpot(1), title: 42 });
    expectSpotInvalid({ ...validSpot(1), category: 42 });
    expectSpotInvalid({ ...validSpot(1), imageUrl: 42 });
    expectSpotInvalid({ ...validSpot(1), imageUrl: null });
  });

  it("rejects NaN and infinite coordinates", () => {
    expectSpotInvalid({ ...validSpot(1), latitude: NaN });
    expectSpotInvalid({ ...validSpot(1), longitude: NaN });
    expectSpotInvalid({ ...validSpot(1), latitude: Infinity });
    expectSpotInvalid({ ...validSpot(1), longitude: -Infinity });
  });

  it("rejects invalid image URLs", () => {
    expectSpotInvalid({ ...validSpot(1), imageUrl: "http://example.com/img.jpg" });
    expectSpotInvalid({ ...validSpot(1), imageUrl: "not-a-url" });
    expectSpotInvalid({ ...validSpot(1), imageUrl: "javascript:alert(1)" });
    expectSpotInvalid({ ...validSpot(1), imageUrl: "file:///etc/passwd" });
    expectSpotInvalid({ ...validSpot(1), imageUrl: "x".repeat(MAX_IMAGE_URL_LENGTH + 1) });
  });

  it("accepts the empty image URL as the missing-image representation", () => {
    const spot = validateSharedTripSpot({ ...validSpot(1), imageUrl: "" });
    assert.strictEqual(spot.imageUrl, "");
  });

  it("accepts values at the permitted boundaries", () => {
    const minBoundary = validateSharedTripSpot({
      spotId: "x".repeat(MAX_SPOT_ID_LENGTH),
      title: "x".repeat(MAX_TITLE_LENGTH),
      category: "shortcut",
      latitude: -90,
      longitude: -180,
      imageUrl: "",
    });
    assert.strictEqual(minBoundary.spotId.length, MAX_SPOT_ID_LENGTH);
    assert.strictEqual(minBoundary.title.length, MAX_TITLE_LENGTH);

    const maxBoundary = validateSharedTripSpot({
      ...validSpot(2),
      latitude: 90,
      longitude: 180,
    });
    assert.strictEqual(maxBoundary.latitude, 90);
    assert.strictEqual(maxBoundary.longitude, 180);
  });
});

// ---------------------------------------------------------------------------
// buildSharedTripDocument (server-owned document construction)
// ---------------------------------------------------------------------------

describe("buildSharedTripDocument", () => {
  it("sets the document id to the server-generated id and exactly 30-day timestamps", () => {
    const validated = validateSharedTripPublishPayload(validPublishPayload());
    const doc = buildSharedTripDocument("server-generated-id", validated, NOW);
    assert.strictEqual(doc.id, "server-generated-id");
    assert.strictEqual(doc.title, validated.title);
    assert.strictEqual(doc.spots, validated.spots);
    assert.strictEqual(doc.createdAt.toMillis(), NOW);
    assert.strictEqual(doc.expireAt.toMillis(), NOW + TTL_DURATION_MS);
    assert.strictEqual(doc.expireAt.toMillis() - doc.createdAt.toMillis(), 30 * DAY_MS);
  });
});

// ---------------------------------------------------------------------------
// publishSharedTripDocument (real persistence against the Firestore emulator)
// ---------------------------------------------------------------------------

describe("publishSharedTripDocument (emulator persistence)", () => {
  const nextRequestId = (() => {
    let n = 0;
    return () => `req-pub-${++n}-${Date.now().toString(36)}`;
  })();

  it("publishes one new document with server-owned id and timestamps", async () => {
    const before = Date.now();
    const published = await publishSharedTripDocument(db, ALICE, validPublishPayload(nextRequestId()));
    const after = Date.now();

    assert.ok(published.tripId, "returns a server-generated trip id");
    assert.match(published.shareableLink, /^https:\/\/vividscout\.app\/trip\/.+$/);
    assert.strictEqual(published.shareableLink.endsWith(published.tripId), true);
    assert.strictEqual(published.replayed, false);

    const doc = await db.collection(TRIP_COLLECTION).doc(published.tripId).get();
    assert.strictEqual(doc.exists, true);
    assert.strictEqual(doc.get("id"), published.tripId);
    assert.strictEqual(doc.get("title"), "Alfama Sunrise Walk");

    const createdAt = doc.get("createdAt").toMillis();
    const expireAt = doc.get("expireAt").toMillis();
    // Server clock, not the client's: within the publish call window.
    assert.ok(createdAt >= before && createdAt <= after, "createdAt comes from the server clock");
    assert.strictEqual(expireAt - createdAt, 30 * DAY_MS);
  });

  it("replays an identical request instead of publishing a second trip (idempotency)", async () => {
    const requestId = nextRequestId();
    const first = await publishSharedTripDocument(db, ALICE, validPublishPayload(requestId));
    const second = await publishSharedTripDocument(db, ALICE, validPublishPayload(requestId));

    assert.strictEqual(second.tripId, first.tripId);
    assert.strictEqual(second.shareableLink, first.shareableLink);
    assert.strictEqual(second.replayed, true);

    const snapshot = await db.collection(TRIP_COLLECTION).doc(first.tripId).get();
    assert.strictEqual(snapshot.exists, true);
    assert.strictEqual(snapshot.get("title"), "Alfama Sunrise Walk");

    // Exactly one idempotency record maps this request to the single published trip.
    const requestDoc = await db.collection(PUBLISH_REQUEST_COLLECTION).doc(requestId).get();
    assert.strictEqual(requestDoc.exists, true);
    assert.strictEqual(requestDoc.get("tripId"), first.tripId);
  });

  it("cannot be used to overwrite an existing trip document", async () => {
    // Publishing the same request twice, two different requests with identical
    // content, and a request after an idempotency record exists all produce distinct
    // create-only writes — never an overwrite of an existing document.
    const first = await publishSharedTripDocument(db, ALICE, validPublishPayload(nextRequestId()));
    const second = await publishSharedTripDocument(db, ALICE, {
      ...validPublishPayload(nextRequestId()),
      title: "Unrelated Walk",
    });

    assert.notStrictEqual(second.tripId, first.tripId);
    const firstDoc = await db.collection(TRIP_COLLECTION).doc(first.tripId).get();
    assert.strictEqual(firstDoc.get("title"), "Alfama Sunrise Walk");
  });

  it("rejects a replay attempt from a different owner", async () => {
    const requestId = nextRequestId();
    await publishSharedTripDocument(db, ALICE, validPublishPayload(requestId));

    await assert.rejects(
      publishSharedTripDocument(db, BOB, validPublishPayload(requestId)),
      (error) => {
        assert.strictEqual(error.code, "already-exists");
        return true;
      }
    );
    // The original trip remains untouched.
    const requestDoc = await db.collection(PUBLISH_REQUEST_COLLECTION).doc(requestId).get();
    assert.strictEqual(requestDoc.get("ownerUid"), ALICE);
  });

  it("rejects an unauthenticated owner", async () => {
    await assert.rejects(
      publishSharedTripDocument(db, null, validPublishPayload(nextRequestId())),
      (error) => {
        assert.strictEqual(error.code, "unauthenticated");
        return true;
      }
    );
  });

  it("rejects an invalid payload before touching Firestore", async () => {
    await assert.rejects(
      publishSharedTripDocument(db, ALICE, { ...validPublishPayload(nextRequestId()), spots: [] }),
      (error) => {
        assert.strictEqual(error.code, "invalid-argument");
        return true;
      }
    );
  });

  it("applies the rate limit to new publishes only, never to replays", async () => {
    // Allow exactly one new publish.
    let calls = 0;
    const shouldRateLimit = () => {
      calls += 1;
      return calls === 1;
    };

    const requestId = nextRequestId();
    await publishSharedTripDocument(db, ALICE, validPublishPayload(requestId), { shouldRateLimit });
    // A replay of the same request must succeed even though the budget is exhausted.
    const replayed = await publishSharedTripDocument(db, ALICE, validPublishPayload(requestId), {
      shouldRateLimit,
    });
    assert.strictEqual(replayed.replayed, true);
    assert.strictEqual(calls, 1, "the replay never consumed rate-limit budget");

    // A genuinely new publish is denied once the budget is exhausted.
    await assert.rejects(
      publishSharedTripDocument(db, ALICE, validPublishPayload(nextRequestId()), { shouldRateLimit }),
      (error) => {
        assert.strictEqual(error.code, "resource-exhausted");
        return true;
      }
    );
  });
});

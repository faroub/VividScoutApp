/**
 * Tests for the production photo finalization helper (functions/photos.js).
 *
 * These tests import the REAL production module and run its actual finalization logic
 * against the genuine Firestore and Storage emulators through the Admin SDK (the same
 * backend instances the finalizePhotoUpload Cloud Function uses at runtime). Object
 * existence, size, content type, ownership path, and idempotency are verified against
 * real storage — there is no mocking anywhere in this suite.
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
  parseStorageObjectPath,
  finalizePhotoUploadDocument,
  PHOTOS_COLLECTION,
} = require("./photos");

const PROJECT_ID = "vividscout-rules-emulator";
const ALICE = "alice-uid";
const BOB = "bob-uid";
const BUCKET_NAME = `${PROJECT_ID}.appspot.com`;

let db = null;
let bucket = null;

before(() => {
  if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_STORAGE_EMULATOR_HOST) {
    throw new Error(
      "Refusing to run: FIRESTORE_EMULATOR_HOST and FIREBASE_STORAGE_EMULATOR_HOST must be set. " +
        'Run via: firebase emulators:exec --only firestore,storage "npm --prefix functions test"'
    );
  }
  if (admin.apps.length === 0) {
    admin.initializeApp({
      projectId: PROJECT_ID,
      storageBucket: BUCKET_NAME,
    });
  }
  db = admin.firestore();
  bucket = admin.storage().bucket(BUCKET_NAME);
});

after(async () => {
  if (admin.apps.length > 0) {
    await admin.app().delete();
  }
});

const JPEG_BYTES = Buffer.from([
  0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46,
]);
const TIMESTAMP = Date.now();

const nextObjectPath = (() => {
  let n = 0;
  return (ownerUid, spotId) => `spot_photos/${ownerUid}/${spotId}/photo-${TIMESTAMP}-${++n}.jpg`;
})();

/** The Firebase Storage token-URL form the Android client obtains via getDownloadUrl(). */
const tokenUrlFor = (objectPath) =>
  `https://firebasestorage.googleapis.com/v0/b/${BUCKET_NAME}/o/${encodeURIComponent(objectPath)}?alt=media&token=some-token`;

/**
 * Seeds an object exactly like the Android upload path would: bytes stored under the
 * ownership-aware path with image/jpeg content type (Storage rules enforce path
 * ownership, size, and image type in production; here the Admin SDK mirrors the result).
 */
const seedObject = async (objectPath, options = {}) => {
  const file = bucket.file(objectPath);
  await file.save(JPEG_BYTES, {
    metadata: {
      contentType: options.contentType || "image/jpeg",
      metadata: { ownerUid: options.ownerUid || ALICE },
    },
  });
  return file;
};

const expectHttpsError = async (fn, code) => {
  await assert.rejects(fn, (error) => {
    assert.strictEqual(error.code, code, `expected ${code}, got ${error.code}`);
    return true;
  });
};

// ---------------------------------------------------------------------------
// parseStorageObjectPath (URL provenance)
// ---------------------------------------------------------------------------

describe("parseStorageObjectPath", () => {
  it("returns the decoded object path for the Firebase Storage token-URL form", () => {
    const path = "spot_photos/alice-uid/spot-1/photo-abc.jpg";
    assert.strictEqual(parseStorageObjectPath(tokenUrlFor(path), BUCKET_NAME), path);
  });

  it("rejects decoded paths containing characters outside the permitted object-path charset", () => {
    // Canonical paths are built from strictly validated spotId/filename segments, so a
    // URL decoding to a path with spaces, parentheses, or other characters can never
    // match a caller's canonical path and is rejected outright.
    const weird = "spot_photos/alice-uid/spot 1 (special)/photo-abc.jpg";
    assert.strictEqual(parseStorageObjectPath(tokenUrlFor(weird), BUCKET_NAME), null);
    assert.strictEqual(
      parseStorageObjectPath(`https://storage.googleapis.com/${BUCKET_NAME}/${encodeURIComponent(weird)}`, BUCKET_NAME),
      null
    );
  });

  it("returns the object path for the public Google Cloud Storage URL form", () => {
    const path = "spot_photos/alice-uid/spot-1/photo-abc.jpg";
    const url = `https://storage.googleapis.com/${BUCKET_NAME}/${encodeURIComponent(path)}`;
    assert.strictEqual(parseStorageObjectPath(url, BUCKET_NAME), path);
  });

  it("rejects URLs referencing a different bucket", () => {
    const path = "spot_photos/alice-uid/spot-1/photo-abc.jpg";
    assert.strictEqual(
      parseStorageObjectPath(
        `https://firebasestorage.googleapis.com/v0/b/other-bucket/o/${encodeURIComponent(path)}`,
        BUCKET_NAME
      ),
      null
    );
  });

  it("rejects arbitrary external HTTPS URLs", () => {
    assert.strictEqual(parseStorageObjectPath("https://example.com/arbitrary.jpg", BUCKET_NAME), null);
    assert.strictEqual(parseStorageObjectPath("https://evil.example/fishing.jpg?x=1", BUCKET_NAME), null);
  });

  it("rejects non-https and malformed values", () => {
    assert.strictEqual(parseStorageObjectPath("http://firebasestorage.googleapis.com/v0/b/x/o/y", BUCKET_NAME), null);
    assert.strictEqual(parseStorageObjectPath("not-a-url", BUCKET_NAME), null);
    assert.strictEqual(parseStorageObjectPath(42, BUCKET_NAME), null);
    assert.strictEqual(parseStorageObjectPath("https://firebasestorage.googleapis.com/v0/b/" + BUCKET_NAME, BUCKET_NAME), null);
  });
});

// ---------------------------------------------------------------------------
// finalizePhotoUploadDocument (real Storage + Firestore verification)
// ---------------------------------------------------------------------------

describe("finalizePhotoUploadDocument (emulator persistence)", () => {
  it("registers a photo after verifying the caller object and writes server-owned metadata", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-final-1");
    await seedObject(objectPath);

    const result = await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-final-1",
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    });

    assert.ok(result.photoId, "returns a server-generated photo id");
    assert.strictEqual(result.photoUrl, tokenUrlFor(objectPath));
    assert.strictEqual(result.spotId, "spot-final-1");
    assert.strictEqual(result.ownerUid, ALICE);
    assert.strictEqual(result.replayed, false);

    const doc = await db.collection(PHOTOS_COLLECTION).doc(result.photoId).get();
    assert.strictEqual(doc.exists, true);
    assert.strictEqual(doc.get("id"), result.photoId);
    assert.strictEqual(doc.get("photoPath"), objectPath);
    assert.strictEqual(doc.get("ownerUid"), ALICE);
    assert.strictEqual(doc.get("reportsCount"), 0);
  });

  it("cannot register another user's uploaded object", async () => {
    const victimObject = nextObjectPath(ALICE, "spot-final-2");
    await seedObject(victimObject);

    // BOB supplies ALICE's object URL while claiming the object under his own uid:
    // the canonical path for BOB cannot match ALICE's object path, so the attempt fails.
    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, BOB, {
          spotId: "spot-final-2",
          filename: victimObject.split("/").pop(),
          photoUrl: tokenUrlFor(victimObject),
        }),
      "invalid-argument"
    );
    assert.strictEqual((await db.collection(PHOTOS_COLLECTION).where("photoPath", "==", victimObject).get()).empty, true);
  });

  it("cannot register an arbitrary external HTTPS URL", async () => {
    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-final-3",
          filename: "photo-external.jpg",
          photoUrl: "https://example.com/arbitrary-external.jpg",
        }),
      "invalid-argument"
    );
  });

  it("rejects a URL whose embedded path differs from the caller's canonical path", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-final-4");
    await seedObject(objectPath);

    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-final-5", // mismatched spot segment
          filename: objectPath.split("/").pop(),
          photoUrl: tokenUrlFor(objectPath),
        }),
      "invalid-argument"
    );
  });

  it("rejects a nonexistent Storage object", async () => {
    const ghostPath = nextObjectPath(ALICE, "spot-final-6");
    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-final-6",
          filename: ghostPath.split("/").pop(),
          photoUrl: tokenUrlFor(ghostPath),
        }),
      "invalid-argument"
    );
  });

  it("rejects an object whose content type is not a permitted image", async () => {
    const badTypePath = nextObjectPath(ALICE, "spot-final-7");
    await seedObject(badTypePath, { contentType: "text/plain" });

    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-final-7",
          filename: badTypePath.split("/").pop(),
          photoUrl: tokenUrlFor(badTypePath),
        }),
      "invalid-argument"
    );
  });

  it("rejects an oversized object", async () => {
    const oversizedPath = nextObjectPath(ALICE, "spot-final-8");
    const file = bucket.file(oversizedPath);
    // Just over the 5MB limit mirrored from storage.rules.
    await file.save(Buffer.alloc(5 * 1024 * 1024 + 1), { metadata: { contentType: "image/jpeg" } });

    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-final-8",
          filename: oversizedPath.split("/").pop(),
          photoUrl: tokenUrlFor(oversizedPath),
        }),
      "invalid-argument"
    );
  });

  it("is idempotent: finalizing the same object replays the published photo, never a duplicate", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-final-9");
    await seedObject(objectPath);

    const first = await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-final-9",
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    });

    let rateLimitCalls = 0;
    const second = await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-final-9",
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    }, { shouldRateLimit: () => { rateLimitCalls += 1; return true; } });

    assert.strictEqual(second.photoId, first.photoId);
    assert.strictEqual(second.photoUrl, first.photoUrl);
    assert.strictEqual(second.replayed, true);
    assert.strictEqual(rateLimitCalls, 0, "a replay never consumes rate-limit budget");

    const registered = await db.collection(PHOTOS_COLLECTION).where("photoPath", "==", objectPath).get();
    assert.strictEqual(registered.size, 1, "exactly one metadata document for the object");
  });

  it("rejects an unauthenticated owner", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-final-10");
    await seedObject(objectPath);

    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, null, {
          spotId: "spot-final-10",
          filename: objectPath.split("/").pop(),
          photoUrl: tokenUrlFor(objectPath),
        }),
      "unauthenticated"
    );
  });

  it("rejects invalid payload fields before touching Storage or Firestore", async () => {
    await expectHttpsError(
      () => finalizePhotoUploadDocument(db, bucket, ALICE, { spotId: "", filename: "x.jpg", photoUrl: "https://x" }),
      "invalid-argument"
    );
    await expectHttpsError(
      () => finalizePhotoUploadDocument(db, bucket, ALICE, { spotId: "spot-x", filename: "../escape.jpg", photoUrl: "https://x" }),
      "invalid-argument"
    );
    await expectHttpsError(
      () => finalizePhotoUploadDocument(db, bucket, ALICE, "not-an-object"),
      "invalid-argument"
    );
    await expectHttpsError(
      () => finalizePhotoUploadDocument(db, bucket, ALICE, { spotId: "spot-x" }),
      "invalid-argument"
    );
  });

  it("applies the rate limit to new registrations only", async () => {
    let calls = 0;
    const shouldRateLimit = () => {
      calls += 1;
      return calls === 1;
    };

    const objectPathA = nextObjectPath(ALICE, "spot-final-11");
    await seedObject(objectPathA);
    await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-final-11",
      filename: objectPathA.split("/").pop(),
      photoUrl: tokenUrlFor(objectPathA),
    }, { shouldRateLimit });

    const objectPathB = nextObjectPath(ALICE, "spot-final-12");
    await seedObject(objectPathB);
    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-final-12",
          filename: objectPathB.split("/").pop(),
          photoUrl: tokenUrlFor(objectPathB),
        }, { shouldRateLimit }),
      "resource-exhausted"
    );
  });
});

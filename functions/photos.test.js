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

const { describe, it, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("firebase-admin");
const sharp = require("sharp");
const {
  parseStorageObjectPath,
  finalizePhotoUploadDocument,
  validateAndNormalizeImageObject,
  photoDocumentIdFor,
  reapUnregisteredPhotoObjects,
  moderateReportedPhotos,
  releaseDeletionClaimFor,
  PHOTOS_COLLECTION,
  CLEANUP_CLAIM_COLLECTION,
  CLEANUP_STATE_COLLECTION,
  CLEANUP_STATE_DOC,
  REAP_MIN_AGE_MS,
  REAP_MAX_OBJECTS_PER_RUN,
  MODERATION_REPORTS_THRESHOLD,
} = require("./photos");
const { ownershipIndexRef } = require("./ownershipIndex");

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

const TIMESTAMP = Date.now();

// Real, decodable image fixtures built with the same library the backend validates with.
// The finalization no longer trusts declared content types, so every successful path must
// be backed by genuine image bytes.
let jpegFixture = null;
let pngFixture = null;
const realJpeg = async () => {
  if (!jpegFixture) {
    jpegFixture = await sharp({
      create: { width: 320, height: 240, channels: 3, background: { r: 32, g: 96, b: 160 } },
    })
      .jpeg({ quality: 90 })
      .toBuffer();
  }
  return jpegFixture;
};
const realPng = async () => {
  if (!pngFixture) {
    pngFixture = await sharp({
      create: { width: 200, height: 120, channels: 4, background: { r: 200, g: 120, b: 40, alpha: 0.5 } },
    })
      .png()
      .toBuffer();
  }
  return pngFixture;
};

const nextObjectPath = (() => {
  let n = 0;
  return (ownerUid, spotId) => `spot_photos/${ownerUid}/${spotId}/photo-${TIMESTAMP}-${++n}.jpg`;
})();

/** The Firebase Storage token-URL form the Android client obtains via getDownloadUrl(). */
const tokenUrlFor = (objectPath) =>
  `https://firebasestorage.googleapis.com/v0/b/${BUCKET_NAME}/o/${encodeURIComponent(objectPath)}?alt=media&token=some-token`;

/**
 * Seeds an object exactly like the Android upload path would: bytes stored under the
 * ownership-aware path with image/jpeg content type and ownerUid/spotId custom metadata
 * (the production client sets both; the backend verifies they agree with the path).
 */
const seedObject = async (objectPath, options = {}) => {
  const file = bucket.file(objectPath);
  const pathSpotId = objectPath.split("/")[2];
  await file.save(options.bytes ?? (await realJpeg()), {
    metadata: {
      contentType: options.contentType || "image/jpeg",
      metadata: {
        ownerUid: options.ownerUid || ALICE,
        spotId: options.metadataSpotId ?? pathSpotId,
      },
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
    assert.strictEqual(doc.get("ownerUid"), undefined, "the public photo document carries no ownerUid");
    assert.strictEqual(doc.get("reportsCount"), 0);

    // The ownership record lives in the server-written index, not the public document.
    const index = await ownershipIndexRef(db, PHOTOS_COLLECTION, result.photoId).get();
    assert.strictEqual(index.get("ownerUid"), ALICE);
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

  it("rejects an object whose bytes are not a permitted image type", async () => {
    const badTypePath = nextObjectPath(ALICE, "spot-final-7");
    await seedObject(badTypePath, {
      contentType: "text/plain",
      bytes: Buffer.from("definitely not an image"),
    });

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

    const rateLimitRef = db.collection("rate_limits").doc(`test-photo-idempotent-${TIMESTAMP}`);
    const second = await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-final-9",
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    }, { rateLimit: { ref: rateLimitRef, maxPerHour: 30 } });

    assert.strictEqual(second.photoId, first.photoId);
    assert.strictEqual(second.photoUrl, first.photoUrl);
    assert.strictEqual(second.replayed, true);

    const counterDoc = await rateLimitRef.get();
    assert.ok(!counterDoc.exists, "a replay never consumes rate-limit budget");

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
    const rateLimitRef = db.collection("rate_limits").doc(`test-photo-ratelimit-${TIMESTAMP}`);
    const rateLimit = { ref: rateLimitRef, maxPerHour: 1 };

    const objectPathA = nextObjectPath(ALICE, "spot-final-11");
    await seedObject(objectPathA);
    await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-final-11",
      filename: objectPathA.split("/").pop(),
      photoUrl: tokenUrlFor(objectPathA),
    }, { rateLimit });

    // A replay of the same object succeeds even though the budget is exhausted.
    const replayed = await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-final-11",
      filename: objectPathA.split("/").pop(),
      photoUrl: tokenUrlFor(objectPathA),
    }, { rateLimit });
    assert.strictEqual(replayed.replayed, true);
    const counterAfterReplay = await rateLimitRef.get();
    assert.strictEqual(counterAfterReplay.get("count"), 1, "the replay never consumed rate-limit budget");

    const objectPathB = nextObjectPath(ALICE, "spot-final-12");
    await seedObject(objectPathB);
    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-final-12",
          filename: objectPathB.split("/").pop(),
          photoUrl: tokenUrlFor(objectPathB),
        }, { rateLimit }),
      "resource-exhausted"
    );
    const counterAfterRejection = await rateLimitRef.get();
    assert.strictEqual(counterAfterRejection.get("count"), 1, "a denied registration consumes no slot");
  });
});

// ---------------------------------------------------------------------------
// finalizePhotoUploadDocument (atomic registration, deterministic ids)
// ---------------------------------------------------------------------------

describe("finalizePhotoUploadDocument (atomic deterministic registration)", () => {
  const finalizeFor = (objectPath, ownerUid = ALICE, options = {}) =>
    finalizePhotoUploadDocument(db, bucket, ownerUid, {
      spotId: objectPath.split("/")[2],
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    }, options);

  it("registers the photo under the deterministic id derived from the object path", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-det-id");
    await seedObject(objectPath);

    const result = await finalizeFor(objectPath);

    assert.strictEqual(result.photoId, photoDocumentIdFor(objectPath));
    assert.strictEqual(result.replayed, false);
    const doc = await db.collection(PHOTOS_COLLECTION).doc(result.photoId).get();
    assert.strictEqual(doc.exists, true);
  });

  it("derives distinct ids for distinct owners, spots, and filenames", () => {
    const a = photoDocumentIdFor("spot_photos/alice-uid/spot-1/photo.jpg");
    const b = photoDocumentIdFor("spot_photos/bob-uid/spot-1/photo.jpg");
    const c = photoDocumentIdFor("spot_photos/alice-uid/spot-2/photo.jpg");
    const d = photoDocumentIdFor("spot_photos/alice-uid/spot-1/other.jpg");
    assert.notStrictEqual(a, b);
    assert.notStrictEqual(a, c);
    assert.notStrictEqual(a, d);
    assert.ok(a.length === 64, "the id is the sha-256 hex digest of the canonical path");
  });

  it("resolves concurrent finalizations of one object to exactly one registration", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-concurrent");
    await seedObject(objectPath);

    const results = await Promise.all([
      finalizeFor(objectPath),
      finalizeFor(objectPath),
    ]);

    assert.strictEqual(results[0].photoId, results[1].photoId, "both callers receive the same photo id");
    assert.strictEqual(results[0].photoUrl, results[1].photoUrl);
    const replayed = results.filter((r) => r.replayed).length;
    assert.ok(replayed >= 1, "at least one caller replays the committed registration");

    const docs = await db.collection(PHOTOS_COLLECTION)
      .where("photoPath", "==", objectPath)
      .get();
    assert.strictEqual(docs.size, 1, "exactly one metadata document exists for the object");
  });

  it("does not overwrite a conflicting registration under the deterministic id", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-conflict");
    await seedObject(objectPath);

    // A registration already exists at the deterministic id but belongs to another
    // account/spot/path shape: the replay must reject instead of returning (or
    // overwriting) someone else's metadata.
    const conflictingRef = db.collection(PHOTOS_COLLECTION).doc(photoDocumentIdFor(objectPath));
    await conflictingRef.create({
      id: conflictingRef.id,
      spotId: "spot-conflict",
      photoPath: objectPath,
      photoUrl: tokenUrlFor(objectPath),
      ownerUid: BOB,
      reportsCount: 0,
      timestamp: Date.now(),
    });

    await expectHttpsError(() => finalizeFor(objectPath), "already-exists");
    const untouched = await conflictingRef.get();
    assert.strictEqual(untouched.get("ownerUid"), BOB, "the conflicting record is not overwritten");
  });

  it("leaves no partially published metadata when a registration is rejected", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-no-partial");
    await seedObject(objectPath);

    // Budget already exhausted for this counter: one slot spent, limit one.
    const rateLimitRef = db.collection("rate_limits").doc(`test-photo-partial-${TIMESTAMP}`);
    await rateLimitRef.create({ count: 1, windowStart: Date.now() });

    await expectHttpsError(
      () => finalizeFor(objectPath, ALICE, { rateLimit: { ref: rateLimitRef, maxPerHour: 1 } }),
      "resource-exhausted"
    );

    const doc = await db.collection(PHOTOS_COLLECTION).doc(photoDocumentIdFor(objectPath)).get();
    assert.strictEqual(doc.exists, false, "no metadata document was published");
    const counterDoc = await rateLimitRef.get();
    assert.strictEqual(counterDoc.get("count"), 1, "the slot was not incremented by the rejected registration");
  });

  it("replays a registration without re-validating or re-encoding the object", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-replay-cheap");
    await seedObject(objectPath);
    const first = await finalizeFor(objectPath);

    // Replace the object bytes with garbage after registration: a replay must still
    // return the published photo — it does not re-run byte validation.
    await bucket.file(objectPath).save(Buffer.from("garbage after publication"), {
      metadata: { contentType: "image/jpeg", metadata: { ownerUid: ALICE, spotId: "spot-replay-cheap" } },
    });

    const second = await finalizeFor(objectPath);
    assert.strictEqual(second.photoId, first.photoId);
    assert.strictEqual(second.photoUrl, first.photoUrl);
    assert.strictEqual(second.replayed, true);
  });
});

// ---------------------------------------------------------------------------
// finalizePhotoUploadDocument (trusted byte-level image validation)
//
// The declared content type and the client-side processing are NOT a security boundary:
// these tests upload real and malicious byte payloads (all declared as images) and
// verify only genuine, safely-sized image data is ever registered as a photo.
// ---------------------------------------------------------------------------

describe("finalizePhotoUploadDocument (byte-level image validation)", () => {
  /** Patches the SOF dimensions of a JPEG so its header claims [width]x[height] while the
   *  file itself stays tiny — the shape of a decompression-bomb payload. */
  const patchJpegDimensions = (jpeg, width, height) => {
    const buf = Buffer.from(jpeg);
    let i = 2;
    while (i < buf.length - 9) {
      if (buf[i] !== 0xff) {
        i += 1;
        continue;
      }
      const marker = buf[i + 1];
      if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc) {
        // SOF marker layout: FF xx <len:2> <precision:1> <height:2> <width:2>
        buf.writeUInt16BE(height, i + 5);
        buf.writeUInt16BE(width, i + 7);
        return buf;
      }
      if (marker === 0xd8 || marker === 0xd9 || (marker >= 0xd0 && marker <= 0xd7)) {
        i += 2;
        continue;
      }
      i += 2 + buf.readUInt16BE(i + 2);
    }
    throw new Error("SOF marker not found in JPEG fixture");
  };

  const finalizeFor = (objectPath, ownerUid = ALICE) =>
    finalizePhotoUploadDocument(db, bucket, ownerUid, {
      spotId: objectPath.split("/")[2],
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    });

  const registrationCountFor = async (objectPath) =>
    (await db.collection(PHOTOS_COLLECTION).where("photoPath", "==", objectPath).get()).size;

  it("accepts a genuine JPEG and publishes a canonical, metadata-free object", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-valid-jpeg");
    await seedObject(objectPath, { bytes: await realJpeg() });

    const result = await finalizeFor(objectPath);

    assert.strictEqual(result.replayed, false);
    const [published] = await bucket.file(objectPath).download();
    const publishedMeta = await sharp(published).metadata();
    assert.strictEqual(publishedMeta.format, "jpeg", "the published object is canonical JPEG");
    assert.strictEqual(publishedMeta.exif, undefined, "no EXIF metadata survives publication");
    const [objectMeta] = await bucket.file(objectPath).getMetadata();
    assert.strictEqual(objectMeta.contentType, "image/jpeg");
    assert.strictEqual(objectMeta.metadata.ownerUid, ALICE);
    assert.strictEqual(objectMeta.metadata.spotId, "spot-valid-jpeg");
  });

  it("accepts a genuine PNG and re-encodes it to canonical JPEG", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-valid-png");
    await seedObject(objectPath, { contentType: "image/png", bytes: await realPng() });

    const result = await finalizeFor(objectPath);

    assert.strictEqual(result.replayed, false);
    const [published] = await bucket.file(objectPath).download();
    const publishedMeta = await sharp(published).metadata();
    assert.strictEqual(publishedMeta.format, "jpeg", "PNG is re-encoded to the canonical JPEG form");
    assert.strictEqual(publishedMeta.hasAlpha, false, "the alpha channel is flattened for JPEG");
  });

  it("rejects random binary data declared as image/jpeg, without registering metadata", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-junk");
    await seedObject(objectPath, { bytes: Buffer.alloc(256, 0x55) });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects HTML labelled as image/jpeg", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-html");
    await seedObject(objectPath, { bytes: Buffer.from("<html><body>hello</body></html>") });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects an SVG upload", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-svg");
    const svg = Buffer.from(
      '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100"/></svg>'
    );
    await seedObject(objectPath, { bytes: svg });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects a corrupted (truncated) JPEG", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-corrupt");
    const full = await realJpeg();
    await seedObject(objectPath, { bytes: full.slice(0, full.length - 30) });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects an image whose dimensions exceed the safety bound", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-huge-dims");
    const tall = await sharp({
      create: { width: 12_001, height: 1, channels: 3, background: { r: 0, g: 0, b: 0 } },
    })
      .jpeg()
      .toBuffer();
    await seedObject(objectPath, { bytes: tall });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects a decompression-bomb-style image (small file, huge declared pixel count)", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-bomb");
    const bomb = patchJpegDimensions(await realJpeg(), 8_000, 7_000); // 56 MP claimed
    await seedObject(objectPath, { bytes: bomb });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects an object whose ownerUid metadata disagrees with the caller", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-owner-mismatch");
    await seedObject(objectPath, { bytes: await realJpeg(), ownerUid: BOB });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects an object whose spotId metadata disagrees with the requested spot", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-spotid-mismatch");
    await seedObject(objectPath, { bytes: await realJpeg(), metadataSpotId: "some-other-spot" });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("rejects an object below the minimum size for a valid image", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-tiny");
    await seedObject(objectPath, { bytes: Buffer.from([0xff, 0xd8, 0xff]) });

    await expectHttpsError(() => finalizeFor(objectPath), "invalid-argument");
    assert.strictEqual(await registrationCountFor(objectPath), 0);
  });

  it("strips GPS EXIF metadata from the published object", async () => {
    const objectPath = nextObjectPath(ALICE, "spot-gps");
    const withGps = await sharp(await realJpeg())
      .withExif({
        GPSInfo: {
          GPSLatitudeRef: "N",
          GPSLatitude: "38,42,52.8",
          GPSLongitudeRef: "W",
          GPSLongitude: "9,8,22.2",
        },
      })
      .jpeg()
      .toBuffer();
    await seedObject(objectPath, { bytes: withGps });

    const before = await sharp(withGps).metadata();
    assert.ok(before.exif instanceof Buffer, "fixture sanity: the uploaded bytes carry EXIF");

    await finalizeFor(objectPath);

    const [published] = await bucket.file(objectPath).download();
    const publishedMeta = await sharp(published).metadata();
    assert.strictEqual(publishedMeta.exif, undefined, "GPS/EXIF metadata is stripped before publication");
    assert.strictEqual(publishedMeta.format, "jpeg");
    assert.strictEqual(await registrationCountFor(objectPath), 1);
  });

  it("validates directly exposed (unit-level) and agrees with the end-to-end result", async () => {
    // validateAndNormalizeImageObject is the exported trusted boundary: exercising it
    // directly documents the contract independently of the finalization flow.
    const objectPath = nextObjectPath(ALICE, "spot-direct");
    await seedObject(objectPath, { bytes: await realPng(), contentType: "image/png" });

    const verdict = await validateAndNormalizeImageObject(
      bucket.file(objectPath),
      ALICE,
      "spot-direct"
    );

    assert.strictEqual(verdict.ok, true);
    assert.strictEqual(verdict.width, 200);
    assert.strictEqual(verdict.height, 120);
  });
});

// ---------------------------------------------------------------------------
// reapUnregisteredPhotoObjects (scheduled orphan cleanup)
// ---------------------------------------------------------------------------

describe("reapUnregisteredPhotoObjects (orphan cleanup)", () => {
  const HOUR_MS = 3600 * 1000;
  const NOW_MS = 10 * HOUR_MS;

  const candidate = (objectPath, ageMs, ownerUid = "alice-uid") => ({
    name: objectPath,
    createdMs: NOW_MS - ageMs,
    ownerUidFromMetadata: ownerUid,
  });

  /**
   * Runs the REAL reaper decision logic against the genuine Firestore emulator, with
   * candidate objects supplied through the listing seam. The claim/release/cursor seams
   * default to the REAL production Firestore helpers, so the claim transaction and the
   * registration gate execute in production code; only the object listing and the
   * bucket delete are fakes (the Storage listing/pages are scripted explicitly where
   * pagination matters).
   */
  const setupReap = () => {
    const deleted = [];
    const deleteFailures = new Set();
    const fakeBucket = {
      file: (name) => ({
        delete: async () => {
          if (deleteFailures.has(name)) {
            throw new Error("storage temporarily unavailable");
          }
          deleted.push(name);
        },
      }),
    };
    const cursorState = { token: null, saved: [] };
    const reap = (entries, options = {}) =>
      reapUnregisteredPhotoObjects(db, fakeBucket, {
        nowMs: NOW_MS,
        minAgeMs: HOUR_MS,
        listPage: async (limit, pageToken) => {
          assert.strictEqual(pageToken, cursorState.token, "listing must resume from the saved cursor");
          // Model a real listing: objects already deleted are no longer listed.
          const live = entries.filter((entry) => !deleted.includes(entry.name));
          return { candidates: live.slice(0, limit), nextPageToken: null };
        },
        loadCursor: async () => cursorState.token,
        saveCursor: async (token) => {
          cursorState.saved.push(token);
          cursorState.token = token;
        },
        ...options,
      });
    return { deleted, deleteFailures, reap, cursorState };
  };

  // Clear the server-side cleanup state so each test starts from a clean slate — the
  // reaper otherwise retries claims left behind by earlier runs (phase 1 of its work).
  beforeEach(async () => {
    const claims = await db.collection(CLEANUP_CLAIM_COLLECTION).limit(200).get();
    await Promise.all(claims.docs.map((doc) => doc.ref.delete()));
    const state = await db.collection(CLEANUP_STATE_COLLECTION).limit(10).get();
    await Promise.all(state.docs.map((doc) => doc.ref.delete()));
  });

  const nextOrphanPath = (() => {
    let n = 0;
    return () => `spot_photos/alice-uid/spot-reap-${++n}/orphan-${TIMESTAMP}.jpg`;
  })();

  it("deletes an old unregistered object", async () => {
    const orphan = nextOrphanPath();
    const { deleted, reap } = setupReap();
    const result = await reap([candidate(orphan, 2 * HOUR_MS)]);

    assert.deepStrictEqual(result, { processed: 1, deleted: 1, kept: 0, failed: 0, retried: 0 });
    assert.deepStrictEqual(deleted, [orphan]);
  });

  it("never deletes an object referenced by a registered photo record", async () => {
    // Register a real photo through the production finalization, then present its exact
    // path to the reaper: the Firestore registration gate (claim transaction) must keep
    // it, and the photo must remain finalizable-replayable afterwards.
    const objectPath = nextObjectPath(ALICE, "spot-reap-kept");
    await seedObject(objectPath);
    await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-reap-kept",
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    });

    const { deleted, reap } = setupReap();
    const result = await reap([candidate(objectPath, 2 * HOUR_MS)]);

    assert.deepStrictEqual(result, { processed: 1, deleted: 0, kept: 1, failed: 0, retried: 0 });
    assert.deepStrictEqual(deleted, []);
    const [stillExists] = await bucket.file(objectPath).exists();
    assert.ok(stillExists, "the registered photo object must still exist");
  });

  it("keeps young objects so in-flight uploads are never deleted", async () => {
    const freshObject = nextOrphanPath();
    const { deleted, reap } = setupReap();
    const result = await reap([candidate(freshObject, 30 * 60 * 1000)]);

    assert.deepStrictEqual(result, { processed: 1, deleted: 0, kept: 1, failed: 0, retried: 0 });
    assert.deepStrictEqual(deleted, []);
    assert.ok(
      REAP_MIN_AGE_MS >= HOUR_MS,
      "the default reaper safety window is at least one hour"
    );
  });

  it("keeps malformed paths and objects whose ownerUid metadata disagrees with the path", async () => {
    const malformedPath = "spot_photos/unstructured-object.jpg";
    const mismatched = candidate(
      "spot_photos/alice-uid/spot-x/smuggled.jpg",
      2 * HOUR_MS,
      "bob-uid"
    );
    const missingOwner = candidate(nextOrphanPath(), 2 * HOUR_MS);
    missingOwner.ownerUidFromMetadata = null;
    const { deleted, reap } = setupReap();
    const result = await reap([
      candidate(malformedPath, 2 * HOUR_MS),
      mismatched,
      missingOwner,
    ]);

    assert.deepStrictEqual(
      result,
      { processed: 3, deleted: 0, kept: 3, failed: 0, retried: 0 }
    );
    assert.deepStrictEqual(deleted, []);
  });

  it("bounds the objects processed per run", async () => {
    const orphans = [1, 2, 3].map(() => candidate(nextOrphanPath(), 2 * HOUR_MS));
    const { deleted, reap } = setupReap();
    const result = await reap(orphans, { maxObjects: 2 });

    assert.deepStrictEqual(result, { processed: 2, deleted: 2, kept: 0, failed: 0, retried: 0 });
    assert.strictEqual(deleted.length, 2);
    assert.ok(REAP_MAX_OBJECTS_PER_RUN > 0, "a bounded per-run cap is configured");
  });

  it("continues after a failing delete so the next scheduled run can retry it", async () => {
    const failingOrphan = nextOrphanPath();
    const healthyOrphan = nextOrphanPath();
    const { deleted, deleteFailures, reap } = setupReap();
    deleteFailures.add(failingOrphan);

    const result = await reap([
      candidate(failingOrphan, 2 * HOUR_MS),
      candidate(healthyOrphan, 2 * HOUR_MS),
    ]);

    assert.deepStrictEqual(
      result,
      { processed: 2, deleted: 1, kept: 0, failed: 1, retried: 0 }
    );
    assert.deepStrictEqual(deleted, [healthyOrphan]);
  });

  it("never deletes a candidate without a verifiable creation timestamp", async () => {
    const unageable = { name: nextOrphanPath(), createdMs: NaN, ownerUidFromMetadata: "alice-uid" };
    const { deleted, reap } = setupReap();
    const result = await reap([unageable]);

    assert.deepStrictEqual(result, { processed: 1, deleted: 0, kept: 1, failed: 0, retried: 0 });
    assert.deepStrictEqual(deleted, []);
  });

  it("retries a previously failed deletion on the next run (persisted claim)", async () => {
    const orphan = nextOrphanPath();
    const { deleted, deleteFailures, reap } = setupReap();
    deleteFailures.add(orphan);

    const first = await reap([candidate(orphan, 2 * HOUR_MS)]);
    assert.strictEqual(first.failed, 1);

    // The claim survived the failed delete; phase 1 of the next run retries it. Read the
    // claim by its deterministic document id (a claim shares the document id derived
    // from the object path), not with a collection scan: node --test runs this suite
    // concurrently with the security-rules suite in the same emulator, whose own
    // claim fixtures must not perturb this assertion.
    deleteFailures.delete(orphan);
    const claimRef = db.collection(CLEANUP_CLAIM_COLLECTION).doc(photoDocumentIdFor(orphan));
    assert.ok((await claimRef.get()).exists, "the failed deletion must leave exactly one claim");

    const second = await reap([candidate(orphan, 2 * HOUR_MS)]);
    assert.strictEqual(second.deleted, 1);
    assert.strictEqual(second.retried, 1);
    assert.deepStrictEqual(deleted, [orphan]);

    assert.ok(!(await claimRef.get()).exists, "a successful delete releases its claim");
  });

  it("resumes the listing from the persisted cursor across runs and resets at the end", async () => {
    // A listing whose first page is a retained (registered) object: without cursor
    // persistence, every run would restart at that object and the later orphan would
    // never be reached.
    const registeredPath = nextObjectPath(ALICE, "spot-reap-cursor");
    await seedObject(registeredPath);
    await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-reap-cursor",
      filename: registeredPath.split("/").pop(),
      photoUrl: tokenUrlFor(registeredPath),
    });
    const orphan = nextOrphanPath();

    const pages = {
      start: { candidates: [candidate(registeredPath, 2 * HOUR_MS)], nextPageToken: "page-2" },
      "page-2": { candidates: [candidate(orphan, 2 * HOUR_MS)], nextPageToken: "page-3" },
      "page-3": { candidates: [], nextPageToken: null },
    };
    const listCalls = [];
    const deleted = [];
    const scriptedReap = (options = {}) =>
      reapUnregisteredPhotoObjects(
        db,
        {
          file: (name) => ({
            delete: async () => {
              deleted.push(name);
            },
          }),
        },
        {
          nowMs: NOW_MS,
          minAgeMs: HOUR_MS,
          maxObjects: 1,
          listPage: async (limit, pageToken) => {
            listCalls.push(pageToken ?? "start");
            return pages[pageToken ?? "start"];
          },
          ...options,
        }
      );

    const first = await scriptedReap();
    assert.strictEqual(first.processed, 1);
    assert.strictEqual(first.kept, 1);

    const second = await scriptedReap();
    assert.strictEqual(second.processed, 1);
    assert.strictEqual(second.deleted, 1);
    assert.deepStrictEqual(deleted, [orphan]);

    const third = await scriptedReap();
    assert.strictEqual(third.processed, 0);

    assert.deepStrictEqual(listCalls, ["start", "page-2", "page-3"]);

    const stateDoc = await db.collection(CLEANUP_STATE_COLLECTION).doc(CLEANUP_STATE_DOC).get();
    assert.ok(stateDoc.exists, "the cursor document must be persisted");
    assert.strictEqual(
      (stateDoc.data() || {}).nextPageToken,
      null,
      "the cursor must reset once the listing is exhausted"
    );
  });

  it("refuses to finalize an object the reaper has claimed for deletion", async () => {
    // Real bytes, so the finalization's early claim check and byte validation both run.
    const claimedObject = nextObjectPath(ALICE, "spot-reap-claim");
    await seedObject(claimedObject);
    const { deleteFailures, reap } = setupReap();
    deleteFailures.add(claimedObject);

    // The reaper verifies the object is unregistered and claims it; the delete fails, so
    // the claim stays ACTIVE while the object still exists.
    const claimed = await reap([candidate(claimedObject, 2 * HOUR_MS)]);
    assert.strictEqual(claimed.processed, 1);

    // Concurrent finalization of the claimed object must be REJECTED, not race the
    // deletion: the reaper never deletes a photo it then publishes.
    await assert.rejects(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-reap-claim",
          filename: claimedObject.split("/").pop(),
          photoUrl: tokenUrlFor(claimedObject),
        }),
      (err) => err.code === "aborted",
      "finalization must be refused while a deletion claim is active"
    );

    // Once the claim is released (delete completed) the same object can be finalized
    // again — the path is not blocked forever.
    deleteFailures.delete(claimedObject);
    await releaseDeletionClaimFor(db, claimedObject);
    const result = await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-reap-claim",
      filename: claimedObject.split("/").pop(),
      photoUrl: tokenUrlFor(claimedObject),
    });
    assert.ok(result.photoId, "finalization must succeed after the claim is released");
  });

  it("never deletes a photo that registers while a claim exists", async () => {
    // A stale/expired claim must not make the reaper delete a now-registered object:
    // the claim transaction re-checks the registration document and refuses.
    const objectPath = nextObjectPath(ALICE, "spot-reap-register-while-claimed");
    await seedObject(objectPath);
    await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-reap-register-while-claimed",
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    });

    const { deleted, reap } = setupReap();
    const result = await reap([candidate(objectPath, 2 * HOUR_MS)]);

    assert.deepStrictEqual(result, { processed: 1, deleted: 0, kept: 1, failed: 0, retried: 0 });
    assert.deepStrictEqual(deleted, []);
  });
});

// ---------------------------------------------------------------------------
// moderateReportedPhotos (authoritative backend moderation)
// ---------------------------------------------------------------------------

describe("moderateReportedPhotos (authoritative moderation)", () => {
  const FIXED_NOW = 42_000_000;

  // Each test drives the sweep with its own threshold so the shared-emulator query
  // (where reportsCount >= threshold) matches only the document that test seeded —
  // node:test runs the cases in this file back-to-back against one Firestore.
  let thresholdSeed = 100;
  const nextThreshold = () => ++thresholdSeed;

  /**
   * Seeds a photo record exactly like the trusted writer, with an adjustable report
   * counter, plus the real Storage object its photoPath references.
   */
  const seedReportedPhoto = async (reportsCount, options = {}) => {
    const objectPath = options.objectPath ?? nextObjectPath(ALICE, "spot-moderation");
    if (options.objectExists !== false) {
      await seedObject(objectPath);
    }
    const ref = db.collection(PHOTOS_COLLECTION).doc();
    await ref.create({
      id: ref.id,
      spotId: "spot-moderation",
      photoPath: objectPath,
      photoUrl: tokenUrlFor(objectPath),
      ownerUid: ALICE,
      reportsCount,
      timestamp: Date.now(),
    });
    return { id: ref.id, objectPath, ref };
  };

  /** A bucket stand-in that records delete calls and can be made to fail or 404. */
  const recordingBucket = (behavior = {}) => ({
    file: (name) => ({
      delete: async () => {
        behavior.calls?.push(name);
        if (behavior.failWith) {
          throw behavior.failWith;
        }
      },
    }),
  });

  it("defaults to the production 3-report moderation threshold", async () => {
    assert.strictEqual(MODERATION_REPORTS_THRESHOLD, 3);
  });

  it("deletes the Storage object and marks the record removed once the threshold is reached", async () => {
    const threshold = nextThreshold();
    const { ref, objectPath } = await seedReportedPhoto(threshold);

    const result = await moderateReportedPhotos(db, bucket, {
      threshold,
      nowMs: () => FIXED_NOW,
    });

    assert.deepStrictEqual(result, { processed: 1, removed: 1, kept: 0, failed: 0 });
    const [stillExists] = await bucket.file(objectPath).exists();
    assert.strictEqual(stillExists, false, "the moderated object is deleted, revoking its download URL");
    const doc = await ref.get();
    assert.strictEqual(doc.get("moderationState"), "removed");
    assert.strictEqual(doc.get("moderatedAt"), FIXED_NOW);
  });

  it("is idempotent: a second sweep re-checks an already-removed record's path and tolerates the missing object", async () => {
    const threshold = nextThreshold();
    const { ref, objectPath } = await seedReportedPhoto(threshold);
    await moderateReportedPhotos(db, bucket, { threshold, nowMs: () => FIXED_NOW });

    // Already-removed records are re-verified: the owner could have re-uploaded an
    // object to the moderated path after the first sweep deleted it (storage.rules
    // allows create once the object is gone), so the sweep attempts the delete again.
    // Here the object is already gone, so the 404 is tolerated and the record is kept.
    const calls = [];
    const result = await moderateReportedPhotos(
      db,
      recordingBucket({ calls, failWith: Object.assign(new Error("not found"), { code: 404 }) }),
      { threshold, nowMs: () => FIXED_NOW }
    );

    assert.deepStrictEqual(result, { processed: 1, removed: 0, kept: 1, failed: 0 });
    assert.deepStrictEqual(calls, [objectPath], "the sweep re-checks the moderated path for a re-upload");
    const doc = await ref.get();
    assert.strictEqual(doc.get("moderationState"), "removed");
    const [stillExists] = await bucket.file(objectPath).exists();
    assert.strictEqual(stillExists, false);
  });

  it("leaves photos below the report threshold untouched", async () => {
    const threshold = nextThreshold();
    const { ref, objectPath } = await seedReportedPhoto(threshold - 1);

    const result = await moderateReportedPhotos(db, bucket, {
      threshold,
      nowMs: () => FIXED_NOW,
    });

    assert.deepStrictEqual(result, { processed: 0, removed: 0, kept: 0, failed: 0 });
    const doc = await ref.get();
    assert.strictEqual(doc.get("moderationState"), undefined);
    const [stillExists] = await bucket.file(objectPath).exists();
    assert.strictEqual(stillExists, true, "the object is untouched");
  });

  it("leaves the record unmarked when the object delete fails, so the next sweep retries", async () => {
    const threshold = nextThreshold();
    const { ref } = await seedReportedPhoto(threshold);

    const result = await moderateReportedPhotos(
      db,
      recordingBucket({ failWith: new Error("storage temporarily unavailable") }),
      { threshold, nowMs: () => FIXED_NOW }
    );

    assert.deepStrictEqual(result, { processed: 1, removed: 0, kept: 0, failed: 1 });
    const doc = await ref.get();
    assert.strictEqual(doc.get("moderationState"), undefined, "no removal is claimed while the object is still live");
  });

  it("tolerates an already-deleted object (404) and still marks the record removed", async () => {
    const threshold = nextThreshold();
    const { ref } = await seedReportedPhoto(threshold);

    const result = await moderateReportedPhotos(
      db,
      recordingBucket({ failWith: Object.assign(new Error("not found"), { code: 404 }) }),
      { threshold, nowMs: () => FIXED_NOW }
    );

    assert.deepStrictEqual(result, { processed: 1, removed: 1, kept: 0, failed: 0 });
    const doc = await ref.get();
    assert.strictEqual(doc.get("moderationState"), "removed");
  });

  it("quarantines a record whose photoPath is missing without attempting a delete", async () => {
    // A record without a usable photoPath cannot be fetched anyway; quarantine hides it
    // everywhere instead of leaving a permanently visible moderated photo.
    const threshold = nextThreshold();
    const objectPath = nextObjectPath(ALICE, "spot-moderation");
    const ref = await db.collection(PHOTOS_COLLECTION).doc();
    await ref.create({
      id: ref.id,
      spotId: "spot-moderation",
      photoUrl: tokenUrlFor(objectPath),
      ownerUid: ALICE,
      reportsCount: threshold,
      timestamp: Date.now(),
    });

    const calls = [];
    const result = await moderateReportedPhotos(
      db,
      recordingBucket({ calls }),
      { threshold, nowMs: () => FIXED_NOW }
    );

    assert.deepStrictEqual(result, { processed: 1, removed: 1, kept: 0, failed: 0 });
    assert.deepStrictEqual(calls, []);
    const doc = await ref.get();
    assert.strictEqual(doc.get("moderationState"), "removed");
  });

  it("deletes an object re-uploaded to a removed photo's path on the next sweep", async () => {
    const threshold = nextThreshold();
    const { objectPath } = await seedReportedPhoto(threshold);
    await moderateReportedPhotos(db, bucket, { threshold, nowMs: () => FIXED_NOW });

    // The owner re-uploads to the same path after moderation deleted the object
    // (storage.rules allows create once the object is gone). The next sweep must
    // delete the re-upload: otherwise the moderated photo's slot in Storage could
    // serve arbitrary unvalidated bytes through a fresh download URL.
    await seedObject(objectPath);
    const [existsBefore] = await bucket.file(objectPath).exists();
    assert.strictEqual(existsBefore, true);

    const result = await moderateReportedPhotos(db, bucket, { threshold, nowMs: () => FIXED_NOW });

    assert.deepStrictEqual(result, { processed: 1, removed: 1, kept: 0, failed: 0 });
    const [stillExists] = await bucket.file(objectPath).exists();
    assert.strictEqual(stillExists, false, "the re-uploaded object is deleted by the sweep");
  });

  it("refuses to re-register a removed photo's path: finalization rejects a re-uploaded object", async () => {
    const threshold = nextThreshold();
    const objectPath = nextObjectPath(ALICE, "spot-mod-reregister");
    await seedObject(objectPath);
    const registered = await finalizePhotoUploadDocument(db, bucket, ALICE, {
      spotId: "spot-mod-reregister",
      filename: objectPath.split("/").pop(),
      photoUrl: tokenUrlFor(objectPath),
    });
    const photoRef = db.collection(PHOTOS_COLLECTION).doc(registered.photoId);
    await photoRef.update({ reportsCount: threshold });
    await moderateReportedPhotos(db, bucket, { threshold, nowMs: () => FIXED_NOW });
    assert.strictEqual((await photoRef.get()).get("moderationState"), "removed");

    // The owner re-uploads an object to the moderated path and tries to re-register it
    // (e.g. with a fresh download URL). The replay must refuse instead of publishing
    // the re-uploaded — never re-validated — bytes behind the removed record.
    await seedObject(objectPath);
    await expectHttpsError(
      () =>
        finalizePhotoUploadDocument(db, bucket, ALICE, {
          spotId: "spot-mod-reregister",
          filename: objectPath.split("/").pop(),
          photoUrl: tokenUrlFor(objectPath),
        }),
      "permission-denied"
    );

    const record = await photoRef.get();
    assert.strictEqual(record.get("moderationState"), "removed", "the removed record is untouched");
    const registrations = await db
      .collection(PHOTOS_COLLECTION)
      .where("photoPath", "==", objectPath)
      .get();
    assert.strictEqual(registrations.size, 1, "no second registration was written for the object");
  });
});

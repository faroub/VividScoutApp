/**
 * Photo finalization — trusted production helper module.
 *
 * Firestore photo metadata documents (spot_photos/{photoId}) are created ONLY by the
 * trusted finalizePhotoUpload Cloud Function; firestore.rules denies direct client
 * creation. The client cannot register an arbitrary URL as a photo: the finalization
 * verifies that the referenced Storage object
 *   - exists in the project's bucket,
 *   - lives under the authenticated caller's own ownership-aware path
 *     spot_photos/{uid}/{spotId}/{filename}, and the object's own ownerUid/spotId
 *     custom metadata agrees with that path,
 *   - contains GENUINE image bytes (magic bytes + real decode via sharp — the declared
 *     content type is not trusted), within the decompression-bomb safety bounds, and
 *   - is re-encoded in place into the canonical published JPEG with all metadata
 *     (including EXIF/GPS) stripped,
 * and only then creates the metadata document with a server-derived document id. The
 * public photo document carries no ownerUid (anonymous UIDs are stable identifiers);
 * ownership is recorded in the server-written ownership index
 * (functions/ownershipIndex.js) and is inherent in the canonical path itself.
 *
 * Tests must import this module (not copy its logic).
 */

const { HttpsError } = require("firebase-functions/v2/https");
const sharp = require("sharp");
const crypto = require("crypto");
const { decideRateLimitInTransaction } = require("./rateLimits");
const { ownershipIndexRef } = require("./ownershipIndex");

const PHOTOS_COLLECTION = "spot_photos";
const STORAGE_PATH_PREFIX = "spot_photos/";
const MAX_SPOT_ID_LENGTH = 128;
const MAX_FILENAME_LENGTH = 256;
const MAX_PHOTO_URL_LENGTH = 2048;
const MAX_OBJECT_SIZE_BYTES = 5 * 1024 * 1024; // mirrors storage.rules
const PERMITTED_CONTENT_TYPES = ["image/jpeg", "image/png"];
const PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR = 30;
// An object unregistered this long can never still be part of an upload whose
// client-side retry is in flight — it is a confirmed orphan.
const REAP_MIN_AGE_MS = 24 * 3600 * 1000;
// Bounded work per scheduled run: keeps each invocation cheap and lets the hourly
// schedule drain the backlog naturally.
const REAP_MAX_OBJECTS_PER_RUN = 200;

const invalid = (message) => new HttpsError("invalid-argument", message);

const isPlainObject = (value) =>
  typeof value === "object" && value !== null && !Array.isArray(value);

// Authoritative moderation: once a photo accumulates this many reports, the trusted
// backend hides it (moderationState "hidden"). Hiding — not deleting the Storage
// object — is the automatic step: a content decision this consequential never becomes
// irreversible from a plain report count alone (see moderateReportedPhotos).
// "removed" remains the terminal, manually-reviewed takedown state.
const MODERATION_REPORTS_THRESHOLD = 3;
const MODERATION_STATE_HIDDEN = "hidden";
const MODERATION_STATE_REMOVED = "removed";
const MODERATION_SCAN_LIMIT = 100;
const REPORTS_SUBCOLLECTION = "reports";
// A non-anonymous account may file this many photo reports per hour: abusive mass
// reporting of a single photo by one person is bounded, while aNormal flagging
// session stay well under it.
const PHOTO_REPORT_RATE_LIMIT_MAX_PER_HOUR = 20;

/**
 * Moderation states that quarantine a photo's path: hidden (auto-hidden by the report
 * threshold sweep) and removed (taken down after review). In either state the photo is
 * out of the app, and finalization must never hand its published URL back out.
 */
const isModeratedAway = (state) =>
  state === MODERATION_STATE_HIDDEN || state === MODERATION_STATE_REMOVED;

// Server-side bookkeeping for the orphan reaper. Both collections are written and read
// only by the trusted backend (rules deny all client access): photo_cleanup_state holds
// the resumable listing cursor, photo_cleanup_claims records objects whose deletion was
// claimed (in progress or failed, awaiting retry). Claim document ids reuse the
// deterministic photo document id of the object path, so a claim and a registration can
// never coexist unnoticed (see claimUnregisteredObjectForDeletion).
const CLEANUP_STATE_COLLECTION = "photo_cleanup_state";
const CLEANUP_STATE_DOC = "orphan_reaper_cursor";
const CLEANUP_CLAIM_COLLECTION = "photo_cleanup_claims";
// A deletion claim outlasts a single reaper run (a failed delete is retried by the next
// scheduled pass) but expires after this window so it cannot block finalization of a
// re-uploaded object forever. Finalization clears expired claims and proceeds.
const CLEANUP_CLAIM_TTL_MS = 2 * 3600 * 1000;
// Bounded number of failed deletions retried per scheduled run, in addition to the
// listing sweep.
const REAP_CLAIM_RETRIES_PER_RUN = 50;

// Server-side image safety limits, kept consistent with the Android client's
// PhotoProcessor (app/src/main/java/com/example/util/PhotoProcessor.kt): the client
// already downscales to a 1600px long edge, enforces a 12000px maximum dimension and a
// 50-megapixel total cap, and re-encodes to quality-85 JPEG. The backend enforces the
// same bounds on the actual bytes, because a malicious client can bypass app-side
// processing and upload arbitrary bytes while declaring image/jpeg.
const MAX_IMAGE_DIMENSION_PX = 12_000;
const MAX_TOTAL_PIXELS = 50_000_000;
const CANONICAL_JPEG_QUALITY = 85;
const CANONICAL_MAX_LONG_EDGE_PX = 1600;
const CANONICAL_CONTENT_TYPE = "image/jpeg";

/**
 * Parses an https URL referencing an object in [expectedBucket] and returns the decoded
 * object path, or null when the URL is not a Firebase/Google Cloud Storage URL, does not
 * reference [expectedBucket], or carries no object path. Accepts both the long-lived
 * Firebase Storage token-URL form
 *   https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{path}?alt=media&token=...
 * and the public Google Cloud Storage form
 *   https://storage.googleapis.com/{bucket}/{path}.
 */
function parseStorageObjectPath(photoUrl, expectedBucket) {
  if (typeof photoUrl !== "string" || photoUrl.length > MAX_PHOTO_URL_LENGTH) {
    return null;
  }
  if (!photoUrl.startsWith("https://")) {
    return null;
  }

  let rest = null;
  if (photoUrl.startsWith(`https://firebasestorage.googleapis.com/v0/b/`)) {
    rest = photoUrl.slice(`https://firebasestorage.googleapis.com/v0/b/`.length);
    // The URL form /b/{bucket}/o/{path}: bucket names cannot contain '/', so the first
    // "/o/" occurrence terminates the bucket segment.
    const bucketEnd = rest.indexOf("/o/");
    if (bucketEnd === -1) {
      return null;
    }
    const bucket = rest.slice(0, bucketEnd);
    if (bucket !== expectedBucket) {
      return null;
    }
    let path = rest.slice(bucketEnd + 3);
    const queryStart = path.indexOf("?");
    if (queryStart !== -1) {
      path = path.slice(0, queryStart);
    }
    return decodeObjectPath(path);
  }

  if (photoUrl.startsWith("https://storage.googleapis.com/")) {
    rest = photoUrl.slice("https://storage.googleapis.com/".length);
    const bucketEnd = rest.indexOf("/");
    if (bucketEnd === -1) {
      return null;
    }
    const bucket = rest.slice(0, bucketEnd);
    if (bucket !== expectedBucket) {
      return null;
    }
    return decodeObjectPath(rest.slice(bucketEnd + 1));
  }

  return null;
}

const decodeObjectPath = (encoded) => {
  if (!encoded) {
    return null;
  }
  let decoded;
  try {
    decoded = decodeURIComponent(encoded);
  } catch (_err) {
    return null;
  }
  if (!/^[A-Za-z0-9._\-\/]+$/.test(decoded)) {
    return null;
  }
  return decoded;
};

/**
 * The deterministic photo document identifier: the SHA-256 hex digest of the canonical
 * Storage object path. One Storage object maps to exactly one document id, so:
 *   - concurrent finalization requests for the same object race on a single document
 *     instead of each creating its own registration (no duplicate metadata),
 *   - finalization replay is a direct document read (no query, no index),
 *   - cleanup and moderation can locate a registration from the object path alone.
 * The digest is opaque to clients and cannot collide across objects (canonical paths
 * are unique); it is never derived from a client-supplied id.
 */
const photoDocumentIdFor = (canonicalPath) =>
  crypto.createHash("sha256").update(canonicalPath).digest("hex");

/**
 * The complete server-owned photo registration used by the finalizePhotoUpload Cloud
 * Function. `db` is the Admin Firestore instance and `bucket` the Admin Storage bucket
 * (both bypass rules; the function is the only writer of spot_photos). Guarantees:
 *
 * - spotId/filename/photoUrl are strictly validated before any verification.
 * - the canonical object path is built from the authenticated caller's own uid — the
 *   client cannot register an object stored under another user's path.
 * - the supplied photoUrl MUST reference exactly the canonical path in the project's
 *   bucket; arbitrary external HTTPS URLs are rejected.
 * - the Storage object's actual bytes are validated (genuine JPEG/PNG image data within
 *   the safety bounds) and re-encoded to the canonical form before any metadata is
 *   written — the declared content type is never trusted (see
 *   validateAndNormalizeImageObject).
 * - the Firestore document id is the server-derived hash of the canonical path (see
 *   photoDocumentIdFor), and the registration itself is ONE Firestore transaction:
 *   a replay returns the already-published photo (verifying owner, spot, and path), and
 *   otherwise the rate-limit counter and the photo document are committed together with
 *   create() semantics. No invocation can leave duplicate or partial metadata.
 * - [options.rateLimit] = { ref, maxPerHour } consumes one slot per committed new
 *   registration only; replays never consume budget.
 *
 * Returns { photoId, photoUrl, spotId, ownerUid, timestamp, replayed }.
 */
async function finalizePhotoUploadDocument(db, bucket, ownerUid, data, options = {}) {
  if (!ownerUid) {
    throw new HttpsError("unauthenticated", "User authentication required to finalize a photo upload.");
  }
  if (!isPlainObject(data)) {
    throw invalid("Photo finalization payload must be a JSON object.");
  }

  const spotId = data.spotId;
  if (
    typeof spotId !== "string" ||
    spotId.length === 0 ||
    spotId.length > MAX_SPOT_ID_LENGTH ||
    spotId.includes("/")
  ) {
    throw invalid(`'spotId' must be a non-empty string of at most ${MAX_SPOT_ID_LENGTH} characters without slashes.`);
  }

  const filename = data.filename;
  if (
    typeof filename !== "string" ||
    filename.length === 0 ||
    filename.length > MAX_FILENAME_LENGTH ||
    !/^[A-Za-z0-9_.\-]+$/.test(filename)
  ) {
    throw invalid(`'filename' must be an alphanumeric file name of at most ${MAX_FILENAME_LENGTH} characters.`);
  }

  const photoUrl = data.photoUrl;
  if (typeof photoUrl !== "string" || photoUrl.length === 0 || photoUrl.length > MAX_PHOTO_URL_LENGTH) {
    throw invalid(`'photoUrl' must be a string of at most ${MAX_PHOTO_URL_LENGTH} characters.`);
  }

  // The caller's own ownership-aware path: the Storage rules already guarantee only the
  // caller could have written here, so the canonical path IS the ownership proof.
  const canonicalPath = `${STORAGE_PATH_PREFIX}${ownerUid}/${spotId}/${filename}`;

  // The download URL must reference exactly the canonical object in the project bucket.
  const urlPath = parseStorageObjectPath(photoUrl, bucket.name);
  if (urlPath !== canonicalPath) {
    throw invalid(
      "'photoUrl' does not reference the caller's own uploaded object at the expected Storage path."
    );
  }

  // Verify the object exists.
  const file = bucket.file(canonicalPath);
  const [exists] = await file.exists();
  if (!exists) {
    throw invalid("The uploaded object no longer exists in Storage; the photo cannot be registered.");
  }

  // The deterministic registration address for this exact object.
  const photoRef = db.collection(PHOTOS_COLLECTION).doc(photoDocumentIdFor(canonicalPath));
  const claimRef = deletionClaimRefFor(db, canonicalPath);
  const nowMs = Date.now();

  // Cheap early rejection: an ACTIVE reaper deletion claim means the object is being
  // deleted right now, so the expensive byte validation is skipped. The transaction
  // below remains the authoritative check.
  const existingClaimDoc = await claimRef.get();
  const existingClaim = existingClaimDoc.exists ? existingClaimDoc.data() : null;
  if (
    existingClaim &&
    typeof existingClaim.expiresAt === "number" &&
    existingClaim.expiresAt > nowMs &&
    existingClaim.photoPath === canonicalPath
  ) {
    throw new HttpsError(
      "aborted",
      "Cleanup of this Storage object is in progress; retry photo registration shortly."
    );
  }

  // Fast replay path (cheap direct read): an already-registered object returns its
  // published photo without re-validation — registration itself already proved the
  // object's bytes. Racy by design: a concurrent registration missed here is still
  // caught authoritatively by the transaction below. A photo hidden or removed by
  // moderation can never be re-registered: its path is quarantined, so a re-uploaded
  // object is refused instead of replaying the moderated record's (now untrusted)
  // published URL.
  const replayedDoc = await photoRef.get();
  if (replayedDoc.exists) {
    if (isModeratedAway((replayedDoc.data() || {}).moderationState)) {
      throw new HttpsError("permission-denied", "This photo was removed by moderation.");
    }
    const replay = replayResult(replayedDoc, ownerUid, spotId, canonicalPath);
    if (replay) {
      return replay;
    }
  }

  // Trusted byte-level validation and canonical re-encode of the actual object content
  // (declared content type and client-side processing are NOT a security boundary).
  const verdict = await validateAndNormalizeImageObject(file, ownerUid, spotId);
  if (!verdict.ok) {
    throw invalid(verdict.reason);
  }

  const rateLimit = options.rateLimit || null;

  return db.runTransaction(async (transaction) => {
    // All reads precede all writes (Firestore transaction requirement).
    const photoDoc = await transaction.get(photoRef);
    const claimDoc = await transaction.get(claimRef);
    const indexRef = ownershipIndexRef(db, PHOTOS_COLLECTION, photoRef.id);
    const indexDoc = await transaction.get(indexRef);

    // Replay: the object is registered. Ownership is proven by the canonical path
    // itself — it embeds the caller's uid under spot_photos/{uid}/{spotId}/{filename},
    // and only the caller's own registration could have created that exact record —
    // so the public photo document carries no ownerUid, and its ownership is recorded
    // in the server-written index (replayed here for legacy documents predating it).
    if (photoDoc.exists) {
      // Moderation quarantine inside the transaction too: the fast path above may have
      // missed a record hidden or removed concurrently, so the authoritative replay
      // refuses the same way. Throwing aborts the transaction, so no partial state is
      // committed.
      if (isModeratedAway((photoDoc.data() || {}).moderationState)) {
        throw new HttpsError("permission-denied", "This photo was removed by moderation.");
      }
      const replay = replayResult(photoDoc, ownerUid, spotId, canonicalPath);
      if (replay) {
        if (!indexDoc.exists) {
          transaction.set(indexRef, { ownerUid, createdAt: nowMs });
        }
        return replay;
      }
      throw new HttpsError(
        "already-exists",
        "This photo object is already registered with conflicting metadata."
      );
    }

    // Race safety against the orphan reaper: an ACTIVE deletion claim means the trusted
    // cleanup has verified the object is unregistered and is deleting it right now.
    // Registering (publishing) it then would race the deletion — refuse and let the
    // client retry after the sweep completes. A claim that outlived its window (a
    // crashed run) is cleared here so re-uploads are never blocked forever.
    const claim = claimDoc.exists ? claimDoc.data() : null;
    if (
      claim &&
      typeof claim.expiresAt === "number" &&
      claim.expiresAt > nowMs &&
      claim.photoPath === canonicalPath
    ) {
      throw new HttpsError(
        "aborted",
        "Cleanup of this Storage object is in progress; retry photo registration shortly."
      );
    }
    if (claimDoc.exists) {
      transaction.delete(claimRef);
    }

    // Rate limit, consumed only by genuinely new registrations, atomically with them.
    if (rateLimit) {
      const allowed = await decideRateLimitInTransaction(
        transaction,
        rateLimit.ref,
        rateLimit.maxPerHour,
        nowMs
      );
      if (!allowed) {
        throw new HttpsError(
          "resource-exhausted",
          `Rate limit exceeded. Maximum ${PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR} photo uploads per hour per user.`
        );
      }
    }

    // Server-derived document id; create() never overwrites an existing document. The
    // document carries no ownerUid (publicly readable): ownership lives in the
    // ownership index the rules read, and in the canonical path itself.
    transaction.create(photoRef, {
      id: photoRef.id,
      spotId: spotId,
      photoPath: canonicalPath,
      photoUrl: photoUrl,
      reportsCount: 0,
      timestamp: nowMs,
    });
    transaction.set(indexRef, { ownerUid, createdAt: nowMs });

    return {
      photoId: photoRef.id,
      photoUrl: photoUrl,
      spotId: spotId,
      ownerUid: ownerUid,
      timestamp: nowMs,
      replayed: false,
    };
  });
}

/**
 * Builds the idempotent replay result from an existing registration document, or null
 * when the record does not match [spotId] and [canonicalPath] — ownership is proven by
 * the canonical path itself (spot_photos/{uid}/{spotId}/{filename} embeds the caller's
 * uid, so only that caller's own registration could have produced this exact record),
 * so the publicly readable document carries no ownerUid field. Documents written before
 * that change still carry a legacy ownerUid: when present, it must agree with the
 * caller, so a corrupt legacy record can never be replayed as another user's photo.
 * The returned ownerUid is the authenticated caller's own uid, never read from the
 * public document.
 */
function replayResult(doc, ownerUid, spotId, canonicalPath) {
  const data = doc.data() || {};
  if (data.spotId !== spotId || data.photoPath !== canonicalPath) {
    return null;
  }
  const legacyOwnerUid = data.ownerUid;
  if (typeof legacyOwnerUid === "string" && legacyOwnerUid.length > 0 && legacyOwnerUid !== ownerUid) {
    return null;
  }
  const url = data.photoUrl;
  if (typeof url !== "string" || url.length === 0) {
    return null;
  }
  return {
    photoId: doc.id,
    photoUrl: url,
    spotId: data.spotId || spotId,
    ownerUid: ownerUid,
    timestamp: data.timestamp || Date.now(),
    replayed: true,
  };
}

/**
 * Validates the ownership-aware Storage object structure spot_photos/{uid}/{spotId}/{filename}
 * the upload path requires. Returns the parsed segments, or null for any object that does
 * not follow the structure (the reaper never deletes such objects: they are not part of
 * the app's own upload namespace and must not be cleaned up).
 */
function parseOwnershipAwarePhotoPath(objectPath) {
  if (typeof objectPath !== "string" || objectPath.length === 0) {
    return null;
  }
  const segments = objectPath.split("/");
  if (segments.length !== 4 || segments[0] !== STORAGE_PATH_PREFIX.slice(0, -1)) {
    return null;
  }
  const ownerUid = segments[1];
  const spotId = segments[2];
  const filename = segments[3];
  if (!ownerUid || !spotId || !filename) {
    return null;
  }
  if (spotId.length > MAX_SPOT_ID_LENGTH || filename.length > MAX_FILENAME_LENGTH) {
    return null;
  }
  if (!/^[A-Za-z0-9_.\-]+$/.test(filename)) {
    return null;
  }
  return { ownerUid, spotId, filename };
}

/**
 * Lists candidate Storage objects under the photo path prefix one page at a time via the
 * Admin SDK's paginated getFiles (the [nextQuery] returned by the SDK carries the
 * continuation token for the following page), mapping each object to a plain descriptor.
 * Objects whose creation timestamp cannot be determined are dropped: the reaper never
 * deletes what it cannot age-verify.
 *
 * Returns { candidates, nextPageToken } — nextPageToken is null once the whole listing
 * has been consumed, so the caller can persist it and resume exactly where this page
 * ended without re-inspecting (or skipping past) any object.
 */
async function defaultListCandidateObjects(bucket, maxObjects, pageToken) {
  const query = { prefix: STORAGE_PATH_PREFIX, maxResults: maxObjects };
  if (pageToken) {
    query.pageToken = pageToken;
  }
  const [files, nextQuery] = await bucket.getFiles(query);
  const candidates = files
    .map((file) => {
      const meta = file.metadata || {};
      const createdMs = meta.timeCreated ? Date.parse(meta.timeCreated) : NaN;
      if (!Number.isFinite(createdMs)) {
        return null;
      }
      const custom = meta.metadata || {};
      return {
        name: file.name,
        createdMs,
        ownerUidFromMetadata: typeof custom.ownerUid === "string" ? custom.ownerUid : null,
      };
    })
    .filter((candidate) => candidate !== null);
  const nextPageToken = nextQuery && nextQuery.pageToken ? nextQuery.pageToken : null;
  return { candidates, nextPageToken };
}

/**
 * Loads the persisted reaper listing cursor so a run resumes exactly where the previous
 * one stopped. Returns null when no cursor exists (fresh project or end of listing).
 */
async function defaultLoadCursor(db) {
  const doc = await db.collection(CLEANUP_STATE_COLLECTION).doc(CLEANUP_STATE_DOC).get();
  if (!doc.exists) {
    return null;
  }
  const token = (doc.data() || {}).nextPageToken;
  return typeof token === "string" && token.length > 0 ? token : null;
}

/**
 * Persists the reaper listing cursor (null resets it — the next run starts a fresh
 * listing), so interrupted runs continue the sweep instead of restarting it and so
 * orphans deep in the listing are eventually reached even when earlier objects are
 * always retained.
 */
async function defaultSaveCursor(db, pageToken) {
  await db
    .collection(CLEANUP_STATE_COLLECTION)
    .doc(CLEANUP_STATE_DOC)
    .set({ nextPageToken: pageToken ?? null, updatedAt: Date.now() });
}

/**
 * The deletion claim for an object: the same deterministic document id the object's photo
 * registration would use, in the dedicated claims collection. Sharing the id means the
 * claim and the registration address the same object path, so a claim created while a
 * registration exists (or vice versa) is detected atomically inside a transaction.
 */
const deletionClaimRefFor = (db, objectPath) =>
  db.collection(CLEANUP_CLAIM_COLLECTION).doc(photoDocumentIdFor(objectPath));

/**
 * Atomically claims an unregistered object for deletion inside ONE Firestore
 * transaction: reads the photo registration document first, and only when it does NOT
 * exist writes (or refreshes) a deletion claim with a server timestamp and an expiry.
 *
 * This is the race-aware core of the cleanup: because finalization also checks the claim
 * inside its own transaction (see finalizePhotoUploadDocument), a concurrent finalizer
 * can never register (and thereby publish) an object whose deletion the reaper has
 * already claimed — and a claim collision makes the finalizer's transaction retry and
 * observe the claim instead of committing. The process never deletes a registered photo.
 *
 * Returns true when the claim was committed (the caller may delete the object), or false
 * when the object turned out to be registered (keep it; the claim is not written).
 */
async function claimUnregisteredObjectForDeletion(db, objectPath) {
  const photoRef = db.collection(PHOTOS_COLLECTION).doc(photoDocumentIdFor(objectPath));
  const claimRef = deletionClaimRefFor(db, objectPath);
  const claimedAt = Date.now();
  return db.runTransaction(async (transaction) => {
    const photoDoc = await transaction.get(photoRef);
    if (photoDoc.exists) {
      return false;
    }
    transaction.set(claimRef, {
      photoPath: objectPath,
      claimedAt,
      expiresAt: claimedAt + CLEANUP_CLAIM_TTL_MS,
    });
    return true;
  });
}

/**
 * Releases a deletion claim after the object was successfully deleted, so the object path
 * is immediately free for a future re-upload + finalization. A missing claim (cleared by
 * a concurrent registration) is tolerated. Failures are swallowed: an unreleased claim
 * simply expires, and finalization clears expired claims itself.
 */
async function releaseDeletionClaimFor(db, objectPath) {
  try {
    await deletionClaimRefFor(db, objectPath).delete();
  } catch (_err) {
    // Best-effort release; expiry covers the rest.
  }
}

/**
 * Validates the actual bytes of an uploaded photo object and re-encodes them into the
 * canonical published form. The declared content type and client-side processing are NOT
 * a security boundary: a malicious client can upload arbitrary bytes while declaring
 * image/jpeg, and even the normal Android pipeline is not trusted. This check
 * guarantees that a registered photo:
 *   - is backed by genuine, decodable JPEG or PNG image data (magic bytes + real decode
 *     via sharp — arbitrary text, HTML, SVG, scripts, and corrupted files are rejected);
 *   - stays inside the decompression-bomb safety bounds (per-dimension and total pixel
 *     caps, mirroring the client's PhotoProcessor limits);
 *   - carries custom metadata whose ownerUid and spotId exactly match the canonical
 *     Storage path the caller claims;
 *   - is re-encoded in place as canonical JPEG (auto-oriented, long edge capped,
 *     quality 85) with ALL metadata stripped, so EXIF/GPS data never reaches the
 *     published object and the download URL resolves to the verified, sanitized image.
 *
 * The re-encoded bytes replace the object at its canonical path through the Admin SDK
 * (the trusted backend may write where clients cannot). Returns { ok, width, height }
 * on success, or { ok: false, reason } — the caller maps reason to invalid-argument.
 */
async function validateAndNormalizeImageObject(file, ownerUid, spotId, options = {}) {
  const maxBytes = options.maxBytes ?? MAX_OBJECT_SIZE_BYTES;

  const [metadata] = await file.getMetadata();
  const custom = metadata.metadata || {};

  const declaredSize = Number(metadata.size);
  if (!Number.isFinite(declaredSize) || declaredSize <= 0 || declaredSize > maxBytes) {
    return { ok: false, reason: `The uploaded object is outside the permitted size limit of ${maxBytes} bytes.` };
  }

  // The object's ownership and spot metadata must exactly match the canonical path the
  // caller claims — path ownership alone is not accepted, the upload itself must agree.
  if (custom.ownerUid !== ownerUid) {
    return { ok: false, reason: "The uploaded object's ownerUid metadata does not match the authenticated caller." };
  }
  if (custom.spotId !== spotId) {
    return { ok: false, reason: "The uploaded object's spotId metadata does not match the requested spot." };
  }

  let buffer;
  try {
    [buffer] = await file.download();
  } catch (_err) {
    return { ok: false, reason: "The uploaded object could not be read from Storage." };
  }
  if (!Buffer.isBuffer(buffer) || buffer.length === 0 || buffer.length > maxBytes) {
    return { ok: false, reason: `The uploaded object is outside the permitted size limit of ${maxBytes} bytes.` };
  }
  if (buffer.length < 12) {
    return { ok: false, reason: "The uploaded object is too small to be a valid image." };
  }

  // Magic bytes: genuinely a JPEG or a PNG (rejects HTML, SVG, scripts, random bytes
  // regardless of the declared content type).
  const isJpeg = buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
  const isPng =
    buffer[0] === 0x89 &&
    buffer[1] === 0x50 &&
    buffer[2] === 0x4e &&
    buffer[3] === 0x47 &&
    buffer[4] === 0x0d &&
    buffer[5] === 0x0a &&
    buffer[6] === 0x1a &&
    buffer[7] === 0x0a;
  if (!isJpeg && !isPng) {
    return { ok: false, reason: "The uploaded object is not a JPEG or PNG image." };
  }

  let imageMeta;
  try {
    imageMeta = await sharp(buffer).metadata();
  } catch (_err) {
    return { ok: false, reason: "The uploaded object could not be decoded as an image." };
  }
  const width = Number(imageMeta.width);
  const height = Number(imageMeta.height);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return { ok: false, reason: "The uploaded object has invalid image dimensions." };
  }
  if (imageMeta.format !== "jpeg" && imageMeta.format !== "png") {
    return { ok: false, reason: "The uploaded object is not a JPEG or PNG image." };
  }
  // Decompression-bomb and excessive-size protection (mirrors the client's bounds).
  if (width > MAX_IMAGE_DIMENSION_PX || height > MAX_IMAGE_DIMENSION_PX) {
    return { ok: false, reason: `The uploaded image dimensions (${width}x${height}) exceed the maximum permitted size.` };
  }
  if (width * height > MAX_TOTAL_PIXELS) {
    return { ok: false, reason: `The uploaded image (${width}x${height}) exceeds the maximum permitted pixel count.` };
  }

  // Canonical re-encode: auto-orient from EXIF, cap the long edge, output JPEG with all
  // metadata stripped (EXIF/GPS never reaches the published object).
  let canonical;
  try {
    canonical = await sharp(buffer)
      .rotate()
      .resize({
        width: CANONICAL_MAX_LONG_EDGE_PX,
        height: CANONICAL_MAX_LONG_EDGE_PX,
        fit: "inside",
        withoutEnlargement: true,
      })
      .jpeg({ quality: CANONICAL_JPEG_QUALITY })
      .toBuffer();
  } catch (_err) {
    return { ok: false, reason: "The uploaded object could not be re-encoded into the canonical image format." };
  }
  if (!Buffer.isBuffer(canonical) || canonical.length === 0 || canonical.length > maxBytes) {
    return { ok: false, reason: `The canonical image exceeds the permitted size limit of ${maxBytes} bytes.` };
  }

  let canonicalMeta;
  try {
    canonicalMeta = await sharp(canonical).metadata();
  } catch (_err) {
    return { ok: false, reason: "The canonical image could not be verified after re-encoding." };
  }

  // Replace the object with the verified, sanitized bytes, preserving ownership metadata.
  await file.save(canonical, {
    metadata: {
      contentType: CANONICAL_CONTENT_TYPE,
      metadata: { ownerUid, spotId },
    },
  });

  return { ok: true, width: canonicalMeta.width, height: canonicalMeta.height };
}

/**
 * The scheduled server-side cleanup of orphaned photo uploads: Storage objects under
 * spot_photos/ that were never registered as photos (client crashed, network lost
 * between upload and finalization, or registration was definitively rejected). Safety
 * guarantees:
 *   - age: objects younger than [options.minAgeMs] (default 24 h) are always kept, so
 *     an upload whose finalization is still being retried is never deleted;
 *   - ownership: only objects following the ownership-aware structure
 *     spot_photos/{uid}/{spotId}/{filename} are considered, and ONLY when the ownerUid
 *     custom metadata written at upload EXACTLY matches the path owner — a missing or
 *     mismatched ownerUid is always retained;
 *   - reference + race safety: before deleting, the reaper claims the object for
 *     deletion inside a Firestore transaction that re-checks the registration document
 *     (by its deterministic id, no query, no index). Finalization refuses to register an
 *     object with an active claim, so a concurrent finalizer can never publish an object
 *     the reaper is deleting — the process never deletes a registered photo;
 *   - resumability: the listing cursor (Storage continuation token) is persisted between
 *     runs, so repeated invocations advance through the listing and orphans past the
 *     per-run cap are eventually reached instead of the sweep restarting at the head of
 *     a listing full of retained objects forever. The cursor resets at the end of the
 *     listing;
 *   - retries: deletions that fail leave a claim behind and are retried by a later run
 *     (bounded number of retried claims per invocation), independent of the cursor;
 *   - metrics: aggregate counts only are logged, never object paths or user data.
 *
 * Returns { processed, deleted, kept, failed, retried } diagnostics.
 */
async function reapUnregisteredPhotoObjects(db, bucket, options = {}) {
  const nowMs = options.nowMs ?? Date.now();
  const minAgeMs = options.minAgeMs ?? REAP_MIN_AGE_MS;
  const maxObjects = options.maxObjects ?? REAP_MAX_OBJECTS_PER_RUN;
  const claimForDeletion =
    options.claimForDeletion ?? ((objectPath) => claimUnregisteredObjectForDeletion(db, objectPath));
  const releaseClaim =
    options.releaseClaim ?? ((objectPath) => releaseDeletionClaimFor(db, objectPath));
  const loadCursor = options.loadCursor ?? (() => defaultLoadCursor(db));
  const saveCursor = options.saveCursor ?? ((token) => defaultSaveCursor(db, token));
  const listPage =
    options.listPage ?? ((limit, pageToken) => defaultListCandidateObjects(bucket, limit, pageToken));

  const result = { processed: 0, deleted: 0, kept: 0, failed: 0, retried: 0 };

  // Phase 1: retry deletions that failed on earlier runs. Their claims persist, so the
  // retry does not depend on the listing cursor position.
  const claimsSnapshot = await db
    .collection(CLEANUP_CLAIM_COLLECTION)
    .limit(REAP_CLAIM_RETRIES_PER_RUN)
    .get();
  for (const claimDoc of claimsSnapshot.docs) {
    const claim = claimDoc.data() || {};
    const photoPath = claim.photoPath;
    if (typeof photoPath !== "string" || photoPath.length === 0) {
      try {
        await claimDoc.ref.delete();
      } catch (_err) {
        // Best-effort cleanup of a malformed claim.
      }
      continue;
    }
    result.retried += 1;
    const claimed = await claimForDeletion(photoPath);
    if (!claimed) {
      // The object became registered in the meantime (or the claim raced a registration).
      await releaseClaim(photoPath);
      continue;
    }
    try {
      await bucket.file(photoPath).delete();
      result.deleted += 1;
      await releaseClaim(photoPath);
    } catch (err) {
      result.failed += 1;
      // The claim stays, so the next scheduled run retries.
      console.warn(`[reapOrphanedPhotoUploads] retried delete failed: ${err.message}`);
    }
  }

  // Phase 2: the listing sweep, resuming from the persisted cursor.
  let pageToken = await loadCursor();
  while (result.processed < maxObjects) {
    const remaining = maxObjects - result.processed;
    // The page is capped at the remaining per-run budget, so a stopped run never skips
    // objects it did not process: the saved cursor resumes right after this page.
    const page = await listPage(remaining, pageToken);
    pageToken = page.nextPageToken ?? null;

    // Advance (or reset at the end of the listing) before processing: a process killed
    // mid-page resumes at this page's start, and reprocessing is idempotent.
    await saveCursor(pageToken);

    if (page.candidates.length === 0) {
      break;
    }

    for (const candidate of page.candidates) {
      if (result.processed >= maxObjects) {
        break;
      }
      result.processed += 1;

      // 1. Age gate: recent uploads may still be finalized by a retrying client. Objects
      //    whose age cannot be determined are always kept.
      const ageMs = nowMs - candidate.createdMs;
      if (!Number.isFinite(ageMs) || ageMs < minAgeMs) {
        result.kept += 1;
        continue;
      }

      // 2. Ownership gate: only a well-formed ownership-aware path whose uploaded
      //    ownerUid metadata EXACTLY matches the path owner may be reaped. A missing
      //    ownerUid metadata value is retained, never assumed to match.
      const parsed = parseOwnershipAwarePhotoPath(candidate.name);
      if (
        !parsed ||
        candidate.ownerUidFromMetadata === null ||
        candidate.ownerUidFromMetadata !== parsed.ownerUid
      ) {
        result.kept += 1;
        continue;
      }

      // 3. Reference + race gate: claim for deletion only when NOT registered, inside a
      //    transaction the finalizer also locks on.
      const claimed = await claimForDeletion(candidate.name);
      if (!claimed) {
        result.kept += 1;
        continue;
      }

      try {
        await bucket.file(candidate.name).delete();
        result.deleted += 1;
        await releaseClaim(candidate.name);
      } catch (err) {
        result.failed += 1;
        // The claim persists (with its expiry), so a later run retries this deletion.
        console.warn(`[reapOrphanedPhotoUploads] delete failed for an unreferenced object: ${err.message}`);
      }
    }

    if (!pageToken) {
      break;
    }
  }

  console.log(
    `[reapOrphanedPhotoUploads] aggregate metrics: processed=${result.processed} ` +
      `deleted=${result.deleted} kept=${result.kept} failed=${result.failed} retried=${result.retried}`
  );
  return result;
}

/**
 * The authoritative photo moderation sweep. The 3-report threshold HIDES the photo:
 * the Firestore record is marked [MODERATION_STATE_HIDDEN], so it stops appearing in
 * the app immediately and stays consistent with the app-side hidden filter.
 *
 * Deleting the Storage object is deliberately NOT part of this step: a plain report
 * count is not sufficient grounds to make content permanently unreachable, and keeping
 * the object preserves both the evidence and the ability to un-hide a photo after a
 * mistaken or abusive flagging campaign. A takedown becomes irreversible only in the
 * manually reviewed [MODERATION_STATE_REMOVED] state.
 *
 * Semantics:
 *   - scans photos whose reportsCount has reached the threshold;
 *   - records already hidden or already removed are skipped (idempotent: the sweep
 *     never re-marks a moderated record and never deletes anything);
 *   - a failed mark (Firestore write error) is counted as failed so the next scheduled
 *     run retries it.
 *
 * Returns { processed, hidden, kept, failed } diagnostics. Run as a scheduled function.
 */
async function moderateReportedPhotos(db, bucket, options = {}) {
  const threshold = options.threshold ?? MODERATION_REPORTS_THRESHOLD;
  const scanLimit = options.scanLimit ?? MODERATION_SCAN_LIMIT;
  const nowMs = options.nowMs ?? null;

  const result = { processed: 0, hidden: 0, kept: 0, failed: 0 };

  const snapshot = await db
    .collection(PHOTOS_COLLECTION)
    .where("reportsCount", ">=", threshold)
    .limit(scanLimit)
    .get();

  if (snapshot.empty) {
    return result;
  }

  for (const photoDoc of snapshot.docs) {
    result.processed += 1;
    const data = photoDoc.data() || {};

    // Terminal moderation states need no further automatic action: hidden is this
    // sweep's own output, removed is a reviewed takedown.
    if (isModeratedAway(data.moderationState)) {
      result.kept += 1;
      continue;
    }

    const updateData = { moderationState: MODERATION_STATE_HIDDEN };
    if (typeof nowMs === "function") {
      updateData.moderatedAt = nowMs();
    }
    try {
      await photoDoc.ref.update(updateData);
      result.hidden += 1;
    } catch (err) {
      result.failed += 1;
      console.warn(`[moderateReportedPhotos] failed to hide a reported photo: ${err.message}`);
    }
  }

  return result;
}

/**
 * Validates the reportPhoto callable payload: one plain object carrying exactly one
 * key, "photoId", a non-empty document-id-looking string.
 */
const PHOTO_REPORT_PAYLOAD_KEYS = ["photoId"];

function validatePhotoReportPayload(data) {
  if (!isPlainObject(data)) {
    throw invalid("Photo report payload must be a JSON object.");
  }
  for (const key of Object.keys(data)) {
    if (!PHOTO_REPORT_PAYLOAD_KEYS.includes(key)) {
      throw invalid(`Unexpected field '${key}' in photo report payload. Allowed: ${PHOTO_REPORT_PAYLOAD_KEYS.join(", ")}.`);
    }
  }
  const photoId = data.photoId;
  if (
    typeof photoId !== "string" ||
    photoId.length === 0 ||
    photoId.length > MAX_PHOTO_ID_LENGTH ||
    !/^[A-Za-z0-9_-]+$/.test(photoId)
  ) {
    throw invalid(`'photoId' must be a non-empty document id of at most ${MAX_PHOTO_ID_LENGTH} characters.`);
  }
  return { photoId };
}

const MAX_PHOTO_ID_LENGTH = 256;

/**
 * The trusted photo report writer used by the reportPhoto Cloud Function. One Firestore
 * transaction atomically:
 *   - reads the photo document (must exist and not already be hidden/removed);
 *   - reads the per-reporter document spot_photos/{photoId}/reports/{reporterUid}
 *     (reporter identities stay private — the rules deny all client reads of the
 *     subcollection): an existing document means a duplicate report and is rejected;
 *   - consumes one slot of the per-UID report rate limit, so one account cannot
 *     mass-report photos;
 *   - creates the reporter's document (exactly the shape the rules' direct-write path
 *     writes: { reporterUid, timestamp }) and increments reportsCount by exactly one.
 * Direct client transaction writes (firestore.rules) remain permitted while older app
 * versions are in the field; this callable produces the identical document shapes and
 * invariants, and adds authentication-strength and rate-limit enforcement the rules
 * cannot express (see docs/COMMUNITY_REPORTING_DESIGN.md).
 *
 * Returns { photoId, reportsCount } — the photo's report count after this report.
 */
async function reportPhotoDocument(db, reportingUid, data, options = {}) {
  if (!reportingUid) {
    throw new HttpsError("unauthenticated", "User authentication required to report a photo.");
  }
  const { photoId } = validatePhotoReportPayload(data);
  const photoRef = db.collection(PHOTOS_COLLECTION).doc(photoId);
  const reportRef = photoRef.collection(REPORTS_SUBCOLLECTION).doc(reportingUid);
  const rateLimit = options.rateLimit || null;
  const nowMs = typeof options.nowMs === "function" ? options.nowMs() : options.nowMs ?? Date.now();

  return db.runTransaction(async (transaction) => {
    // All reads precede all writes (Firestore transaction requirement).
    const photoDoc = await transaction.get(photoRef);
    if (!photoDoc.exists) {
      throw new HttpsError("not-found", "This photo no longer exists and cannot be reported.");
    }
    const photoData = photoDoc.data() || {};
    if (isModeratedAway(photoData.moderationState)) {
      throw new HttpsError(
        "failed-precondition",
        "This photo was already hidden or removed, so it can no longer be reported."
      );
    }
    const existingReportDoc = await transaction.get(reportRef);
    if (existingReportDoc.exists) {
      throw new HttpsError(
        "already-exists",
        "You have already reported this photo. Each photo can only be reported once per account."
      );
    }
    if (rateLimit) {
      const allowed = await decideRateLimitInTransaction(
        transaction,
        rateLimit.ref,
        rateLimit.maxPerHour,
        nowMs
      );
      if (!allowed) {
        throw new HttpsError(
          "resource-exhausted",
          `Rate limit exceeded. Maximum ${rateLimit.maxPerHour} photo reports per hour per user.`
        );
      }
    }
    const currentCount = Number(photoData.reportsCount) || 0;
    transaction.set(reportRef, { reporterUid: reportingUid, timestamp: nowMs });
    transaction.update(photoRef, { reportsCount: currentCount + 1 });
    return { photoId, reportsCount: currentCount + 1 };
  });
}

module.exports = {
  PHOTOS_COLLECTION,
  STORAGE_PATH_PREFIX,
  MAX_OBJECT_SIZE_BYTES,
  PERMITTED_CONTENT_TYPES,
  MAX_IMAGE_DIMENSION_PX,
  MAX_TOTAL_PIXELS,
  CANONICAL_JPEG_QUALITY,
  CANONICAL_MAX_LONG_EDGE_PX,
  CANONICAL_CONTENT_TYPE,
  PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR,
  REAP_MIN_AGE_MS,
  REAP_MAX_OBJECTS_PER_RUN,
  REAP_CLAIM_RETRIES_PER_RUN,
  CLEANUP_STATE_COLLECTION,
  CLEANUP_STATE_DOC,
  CLEANUP_CLAIM_COLLECTION,
  CLEANUP_CLAIM_TTL_MS,
  MODERATION_REPORTS_THRESHOLD,
  MODERATION_STATE_HIDDEN,
  MODERATION_STATE_REMOVED,
  MODERATION_SCAN_LIMIT,
  REPORTS_SUBCOLLECTION,
  PHOTO_REPORT_RATE_LIMIT_MAX_PER_HOUR,
  parseStorageObjectPath,
  parseOwnershipAwarePhotoPath,
  finalizePhotoUploadDocument,
  validateAndNormalizeImageObject,
  photoDocumentIdFor,
  claimUnregisteredObjectForDeletion,
  releaseDeletionClaimFor,
  reapUnregisteredPhotoObjects,
  moderateReportedPhotos,
  validatePhotoReportPayload,
  reportPhotoDocument,
};

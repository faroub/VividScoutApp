/**
 * Photo finalization — trusted production helper module.
 *
 * Firestore photo metadata documents (spot_photos/{photoId}) are created ONLY by the
 * trusted finalizePhotoUpload Cloud Function; firestore.rules denies direct client
 * creation. The client cannot register an arbitrary URL as a photo: the finalization
 * verifies that the referenced Storage object
 *   - exists in the project's bucket,
 *   - lives under the authenticated caller's own ownership-aware path
 *     spot_photos/{uid}/{spotId}/{filename},
 *   - is within the permitted size limit, and
 *   - has a permitted image content type,
 * and only then creates the metadata document with a server-generated document id.
 *
 * Tests must import this module (not copy its logic).
 */

const { HttpsError } = require("firebase-functions/v2/https");

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
 * The complete server-owned photo registration used by the finalizePhotoUpload Cloud
 * Function. `db` is the Admin Firestore instance and `bucket` the Admin Storage bucket
 * (both bypass rules; the function is the only writer of spot_photos). Guarantees:
 *
 * - spotId/filename/photoUrl are strictly validated before any verification.
 * - the canonical object path is built from the authenticated caller's own uid — the
 *   client cannot register an object stored under another user's path.
 * - the supplied photoUrl MUST reference exactly the canonical path in the project's
 *   bucket; arbitrary external HTTPS URLs are rejected.
 * - the Storage object is verified to exist, be a permitted image type, and stay within
 *   the size limit before any metadata is written.
 * - the Firestore document id is generated server-side and the document is created with
 *   create() semantics (never an overwrite).
 * - finalizing the same object twice is idempotent: the second call replays the already
 *   published photo instead of creating a duplicate metadata document.
 * - [options.shouldRateLimit] gates only genuinely new registrations; replays never
 *   consume budget.
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

  // Verify the object itself: existence, size, and content type.
  const file = bucket.file(canonicalPath);
  const [exists] = await file.exists();
  if (!exists) {
    throw invalid("The uploaded object no longer exists in Storage; the photo cannot be registered.");
  }
  const [metadata] = await file.getMetadata();
  const size = Number(metadata.size);
  if (!Number.isFinite(size) || size <= 0 || size > MAX_OBJECT_SIZE_BYTES) {
    throw invalid(`The uploaded object is outside the permitted size limit of ${MAX_OBJECT_SIZE_BYTES} bytes.`);
  }
  if (!PERMITTED_CONTENT_TYPES.includes(metadata.contentType)) {
    throw invalid("The uploaded object is not a permitted image type (image/jpeg or image/png).");
  }

  // Idempotent replay: an already-registered object returns its existing photo document.
  const existing = await db
    .collection(PHOTOS_COLLECTION)
    .where("photoPath", "==", canonicalPath)
    .limit(1)
    .get();
  if (!existing.empty) {
    const doc = existing.docs[0];
    const url = doc.get("photoUrl");
    if (typeof url === "string" && url.length > 0) {
      return {
        photoId: doc.id,
        photoUrl: url,
        spotId: doc.get("spotId") || spotId,
        ownerUid: doc.get("ownerUid") || ownerUid,
        timestamp: doc.get("timestamp") || Date.now(),
        replayed: true,
      };
    }
  }

  if (options.shouldRateLimit) {
    const isAllowed = await options.shouldRateLimit();
    if (!isAllowed) {
      throw new HttpsError(
        "resource-exhausted",
        `Rate limit exceeded. Maximum ${PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR} photo uploads per hour per user.`
      );
    }
  }

  // Server-generated document id; create() never overwrites an existing document.
  const photoRef = db.collection(PHOTOS_COLLECTION).doc();
  const nowMs = Date.now();
  await photoRef.create({
    id: photoRef.id,
    spotId: spotId,
    photoPath: canonicalPath,
    photoUrl: photoUrl,
    ownerUid: ownerUid,
    reportsCount: 0,
    timestamp: nowMs,
  });

  return {
    photoId: photoRef.id,
    photoUrl: photoUrl,
    spotId: spotId,
    ownerUid: ownerUid,
    timestamp: nowMs,
    replayed: false,
  };
}

/**
 * Lists candidate Storage objects under the photo path prefix via the Admin SDK and
 * maps each to a plain descriptor. Objects whose creation timestamp cannot be
 * determined are dropped: the reaper never deletes what it cannot age-verify.
 */
async function defaultListCandidateObjects(bucket, maxObjects) {
  const [files] = await bucket.getFiles({ prefix: STORAGE_PATH_PREFIX, maxResults: maxObjects });
  return files
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
}

/**
 * The scheduled server-side cleanup of orphaned photo uploads: Storage objects under
 * spot_photos/ that were never registered as photos (client crashed, network lost
 * between upload and finalization, or registration was definitively rejected). Safety
 * guarantees:
 *   - age: objects younger than [options.minAgeMs] (default 24 h) are always kept, so
 *     an upload whose finalization is still being retried is never deleted;
 *   - ownership: only objects following the ownership-aware structure
 *     spot_photos/{uid}/{spotId}/{filename} are considered, and only when the ownerUid
 *     custom metadata written at upload matches the path owner;
 *   - reference: objects referenced by an existing spot_photos Firestore record
 *     (matched by exact photoPath) are always kept — valid photos are never deleted;
 *   - deletion is best-effort: a failing delete is logged and retried by the next run.
 * A single-field index on photoPath (auto-created by Firestore) serves the lookup.
 *
 * Returns { processed, deleted, kept } diagnostics.
 */
async function reapUnregisteredPhotoObjects(db, bucket, options = {}) {
  const nowMs = options.nowMs ?? Date.now();
  const minAgeMs = options.minAgeMs ?? REAP_MIN_AGE_MS;
  const maxObjects = options.maxObjects ?? REAP_MAX_OBJECTS_PER_RUN;
  const listCandidates =
    options.listCandidates ?? ((limit) => defaultListCandidateObjects(bucket, limit));

  const candidates = await listCandidates(maxObjects);
  const result = { processed: candidates.length, deleted: 0, kept: 0 };

  for (const candidate of candidates) {
    // 1. Age gate: recent uploads may still be finalized by a retrying client. Objects
    //    whose age cannot be determined are always kept — never delete what cannot be
    //    age-verified.
    const ageMs = nowMs - candidate.createdMs;
    if (!Number.isFinite(ageMs) || ageMs < minAgeMs) {
      result.kept += 1;
      continue;
    }

    // 2. Ownership gate: only a well-formed ownership-aware path whose uploaded
    //    ownerUid metadata matches the path owner may be reaped.
    const segments = candidate.name.split("/");
    const pathUid = segments.length === 4 ? segments[1] : null;
    if (!pathUid || (candidate.ownerUidFromMetadata && candidate.ownerUidFromMetadata !== pathUid)) {
      result.kept += 1;
      continue;
    }

    // 3. Reference gate: never delete an object claimed by a valid photo record.
    const registered = await db
      .collection(PHOTOS_COLLECTION)
      .where("photoPath", "==", candidate.name)
      .limit(1)
      .get();
    if (!registered.empty) {
      result.kept += 1;
      continue;
    }

    try {
      await bucket.file(candidate.name).delete();
      result.deleted += 1;
    } catch (err) {
      // Logged without private user content; the next scheduled run retries.
      console.warn(`[reapOrphanedPhotoUploads] delete failed for an unreferenced object: ${err.message}`);
      result.kept += 1;
    }
  }

  return result;
}

module.exports = {
  PHOTOS_COLLECTION,
  STORAGE_PATH_PREFIX,
  MAX_OBJECT_SIZE_BYTES,
  PERMITTED_CONTENT_TYPES,
  PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR,
  REAP_MIN_AGE_MS,
  REAP_MAX_OBJECTS_PER_RUN,
  parseStorageObjectPath,
  finalizePhotoUploadDocument,
  reapUnregisteredPhotoObjects,
};

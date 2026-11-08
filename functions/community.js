/**
 * Community tips and submissions — trusted production helper module.
 *
 * community_tips and community_submissions documents are written ONLY by the trusted
 * postCommunityTip and submitCommunitySpot Cloud Functions; firestore.rules denies
 * direct client creation. Two server-side guarantees live here:
 *
 * 1. No ownerUid in public documents. Anonymous Firebase UIDs are stable identifiers,
 *    so publishing them would let anyone link one person's contributions across every
 *    public collection. The caller's UID is instead recorded in the server-owned
 *    ownership index (functions/ownershipIndex.js), which firestore.rules reads to
 *    enforce ownership at the trusted boundary. The public document never carries it.
 * 2. Per-UID post rate limits consumed atomically with the create: 20 tips and 10
 *    submissions per user per rolling 24 hours, consumed only by genuinely new posts.
 *
 * Persistence is ONE Firestore transaction per post: it reads the existing document and
 * its ownership record (idempotent replay for the verified owner — which also backfills
 * the index for documents written before it existed, migrating them one by one —
 * rejects any other owner), reads and increments the rate-limit counter, and commits
 * the document together with its ownership record, so no invocation can ever leave an
 * orphaned or unattributed document. Validation happens before the transaction and
 * mirrors the rules' field constraints exactly.
 *
 * Tests must import this module (not copy its logic).
 */

const { HttpsError } = require("firebase-functions/v2/https");
const { decideRateLimitInTransaction } = require("./rateLimits");
const { ownershipIndexRef } = require("./ownershipIndex");

const TIPS_COLLECTION = "community_tips";
const SUBMISSIONS_COLLECTION = "community_submissions";

// Per-UID community post limits: a rolling 24-hour window (rateLimits.js implements the
// counter; the window is passed per-enforcer so community limits do not change the
// existing hourly callables).
const COMMUNITY_RATE_LIMIT_WINDOW_MS = 24 * 3600 * 1000;
const TIP_RATE_LIMIT_MAX_PER_DAY = 20;
const SUBMISSION_RATE_LIMIT_MAX_PER_DAY = 10;
const TIP_LIMIT_MESSAGE = `Rate limit exceeded. Maximum ${TIP_RATE_LIMIT_MAX_PER_DAY} community tips per 24 hours per user.`;
const SUBMISSION_LIMIT_MESSAGE = `Rate limit exceeded. Maximum ${SUBMISSION_RATE_LIMIT_MAX_PER_DAY} community submissions per 24 hours per user.`;

// Field caps mirroring firestore.rules (isValidTipData / the submission branches).
const MAX_ID_LENGTH = 128;
const MAX_TIP_TITLE_LENGTH = 120;
const MAX_SPOT_ID_LENGTH = 128;
const MAX_AUTHOR_NAME_LENGTH = 50;
const MAX_TIP_CONTENT_LENGTH = 500;
const MAX_STATUS_CATEGORY_LENGTH = 32;

// The contributor-dated timestamp window. The client dates its own contribution, so
// modest clock skew is tolerated (+5 minutes). Offline drafts may legitimately be older
// than a fresh post — a draft composed offline can sit in the pending sync queue for
// weeks — which is why the age floor is a full 30 days rather than hours or minutes;
// anything older is refused as misdated instead of being clipped, so a bogus timestamp
// can never become the document's preserved attribution date.
const TIMESTAMP_MAX_FUTURE_MS = 5 * 60 * 1000;
const TIMESTAMP_MAX_AGE_MS = 30 * 24 * 3600 * 1000;

const MAX_SUBMISSION_TITLE_LENGTH = 120;
const MAX_DESCRIPTION_LENGTH = 1000;
const MAX_CATEGORY_LENGTH = 32;
const MAX_LOCATION_NAME_LENGTH = 100;
const MAX_CONTRIBUTOR_LENGTH = 50;
const MAX_PHOTO_URI_LENGTH = 2048;

// Clients control exactly these fields; ownerUid and the vote counters are server-owned
// and must never be accepted from a request (mirrors the rules' hasOnly lists).
const TIP_PAYLOAD_KEYS = [
  "id", "spotId", "spotTitle", "authorName", "content",
  "timestamp", "isStatusUpdate", "statusCategory", "legacyUpvotes",
];
const SUBMISSION_PAYLOAD_KEYS = [
  "id", "title", "description", "category", "locationName",
  "latitude", "longitude", "photoUri", "contributor", "timestamp",
];

const isPlainObject = (value) =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const isFiniteNumber = (value) => typeof value === "number" && Number.isFinite(value);

const invalid = (message) => new HttpsError("invalid-argument", message);

const isStringLength = (value, min, max) =>
  typeof value === "string" && value.length >= min && value.length <= max;

/**
 * The Storage-object path a submission photo must reference: either '' (no photo) or
 * spot_photos/{ownerUid}/{spotId}/{filename} under the caller's OWN directory. Compared
 * path-segment by path-segment (never via a uid-interpolated regex), so an owner can
 * only reference their own uploads. Mirrors firestore.rules isValidPhotoUri.
 */
function isValidSubmissionPhotoUri(uri, ownerUid) {
  if (typeof uri !== "string" || uri.length > MAX_PHOTO_URI_LENGTH) {
    return false;
  }
  if (uri === "") {
    return true;
  }
  const parts = uri.split("/");
  return (
    parts.length === 4 &&
    parts[0] === "spot_photos" &&
    parts[1] === ownerUid &&
    parts[2].length > 0 &&
    parts[3].length > 0
  );
}

function isValidCoordinatePair(lat, lng) {
  return (
    isFiniteNumber(lat) &&
    isFiniteNumber(lng) &&
    lat >= -90 && lat <= 90 &&
    lng >= -180 && lng <= 180 &&
    !(lat === 0 && lng === 0)
  );
}

/**
 * Validates the client-controlled community tip payload, mirroring the firestore.rules
 * field constraints exactly (minus the server-owned fields). Throws
 * HttpsError("invalid-argument") for any violation. Returns the normalized document
 * fields the caller may persist.
 */
function validateCommunityTipPayload(data, nowMs = Date.now()) {
  if (!isPlainObject(data)) {
    throw invalid("Community tip payload must be a JSON object.");
  }

  for (const key of Object.keys(data)) {
    if (!TIP_PAYLOAD_KEYS.includes(key)) {
      throw invalid(`Unexpected field '${key}' in community tip payload. Allowed: ${TIP_PAYLOAD_KEYS.join(", ")}.`);
    }
  }
  for (const key of TIP_PAYLOAD_KEYS) {
    if (data[key] === undefined || data[key] === null) {
      throw invalid(`Missing mandatory field '${key}' in community tip payload.`);
    }
  }

  if (!isStringLength(data.id, 1, MAX_ID_LENGTH)) {
    throw invalid(`'id' must be a non-blank string of at most ${MAX_ID_LENGTH} characters.`);
  }
  if (!isStringLength(data.spotId, 1, MAX_SPOT_ID_LENGTH)) {
    throw invalid(`'spotId' must be a non-blank string of at most ${MAX_SPOT_ID_LENGTH} characters.`);
  }
  if (!isStringLength(data.spotTitle, 2, MAX_TIP_TITLE_LENGTH)) {
    throw invalid(`'spotTitle' must be a string of 2 to ${MAX_TIP_TITLE_LENGTH} characters.`);
  }
  if (!isStringLength(data.authorName, 2, MAX_AUTHOR_NAME_LENGTH)) {
    throw invalid(`'authorName' must be a string of 2 to ${MAX_AUTHOR_NAME_LENGTH} characters.`);
  }
  if (!isStringLength(data.content, 2, MAX_TIP_CONTENT_LENGTH)) {
    throw invalid(`'content' must be a string of 2 to ${MAX_TIP_CONTENT_LENGTH} characters.`);
  }
  if (!isStringLength(data.statusCategory, 2, MAX_STATUS_CATEGORY_LENGTH)) {
    throw invalid(`'statusCategory' must be a string of 2 to ${MAX_STATUS_CATEGORY_LENGTH} characters.`);
  }
  if (typeof data.isStatusUpdate !== "boolean") {
    throw invalid("'isStatusUpdate' must be a boolean.");
  }
  if (!isFiniteNumber(data.timestamp)) {
    throw invalid("'timestamp' must be a finite number.");
  }
  if (
    data.timestamp > nowMs + TIMESTAMP_MAX_FUTURE_MS ||
    data.timestamp < nowMs - TIMESTAMP_MAX_AGE_MS
  ) {
    throw invalid("'timestamp' must be within the last 30 days and at most 5 minutes in the future.");
  }
  // Legacy upvote counts are imported ONLY by scripts/migrate_legacy_upvotes.js through
  // the Admin SDK: a client-supplied legacy count would let the author fabricate
  // community signal. The payload must always carry 0; on a replay the STORED value
  // (if any) is preserved (the update never writes this field), never overwritten.
  if (!Number.isSafeInteger(data.legacyUpvotes) || data.legacyUpvotes !== 0) {
    throw invalid("'legacyUpvotes' must be 0; legacy counts are server-migrated only.");
  }

  return {
    id: data.id,
    spotId: data.spotId,
    spotTitle: data.spotTitle,
    authorName: data.authorName,
    content: data.content,
    timestamp: data.timestamp,
    isStatusUpdate: data.isStatusUpdate,
    statusCategory: data.statusCategory,
    legacyUpvotes: data.legacyUpvotes,
  };
}

/**
 * Validates the client-controlled community submission payload, mirroring the
 * firestore.rules field constraints exactly (minus the server-owned ownerUid), plus
 * the description/location/contributor length caps the Android client already enforces.
 * Throws HttpsError("invalid-argument") for any violation.
 */
function validateCommunitySubmissionPayload(data, ownerUid, nowMs = Date.now()) {
  if (!isPlainObject(data)) {
    throw invalid("Community submission payload must be a JSON object.");
  }

  for (const key of Object.keys(data)) {
    if (!SUBMISSION_PAYLOAD_KEYS.includes(key)) {
      throw invalid(`Unexpected field '${key}' in community submission payload. Allowed: ${SUBMISSION_PAYLOAD_KEYS.join(", ")}.`);
    }
  }
  for (const key of SUBMISSION_PAYLOAD_KEYS) {
    if (data[key] === undefined || data[key] === null) {
      throw invalid(`Missing mandatory field '${key}' in community submission payload.`);
    }
  }

  if (!isStringLength(data.id, 1, MAX_ID_LENGTH)) {
    throw invalid(`'id' must be a non-blank string of at most ${MAX_ID_LENGTH} characters.`);
  }
  if (!isStringLength(data.title, 2, MAX_SUBMISSION_TITLE_LENGTH)) {
    throw invalid(`'title' must be a string of 2 to ${MAX_SUBMISSION_TITLE_LENGTH} characters.`);
  }
  if (typeof data.description !== "string" || data.description.length > MAX_DESCRIPTION_LENGTH) {
    throw invalid(`'description' must be a string of at most ${MAX_DESCRIPTION_LENGTH} characters.`);
  }
  if (!isStringLength(data.category, 1, MAX_CATEGORY_LENGTH)) {
    throw invalid(`'category' must be a non-blank string of at most ${MAX_CATEGORY_LENGTH} characters.`);
  }
  if (typeof data.locationName !== "string" || data.locationName.length > MAX_LOCATION_NAME_LENGTH) {
    throw invalid(`'locationName' must be a string of at most ${MAX_LOCATION_NAME_LENGTH} characters.`);
  }
  if (!isValidCoordinatePair(data.latitude, data.longitude)) {
    throw invalid("'latitude'/'longitude' must be finite coordinates within geographic range, not both zero.");
  }
  if (!isValidSubmissionPhotoUri(data.photoUri, ownerUid)) {
    throw invalid("'photoUri' must be empty or a Storage path under the caller's own spot_photos/{uid}/ directory.");
  }
  if (!isStringLength(data.contributor, 1, MAX_CONTRIBUTOR_LENGTH)) {
    throw invalid(`'contributor' must be a non-blank string of at most ${MAX_CONTRIBUTOR_LENGTH} characters.`);
  }
  if (!isFiniteNumber(data.timestamp)) {
    throw invalid("'timestamp' must be a finite number.");
  }
  if (
    data.timestamp > nowMs + TIMESTAMP_MAX_FUTURE_MS ||
    data.timestamp < nowMs - TIMESTAMP_MAX_AGE_MS
  ) {
    throw invalid("'timestamp' must be within the last 30 days and at most 5 minutes in the future.");
  }

  return {
    id: data.id,
    title: data.title,
    description: data.description,
    category: data.category,
    locationName: data.locationName,
    latitude: data.latitude,
    longitude: data.longitude,
    photoUri: data.photoUri,
    contributor: data.contributor,
    timestamp: data.timestamp,
  };
}

/**
 * The owner of an existing public community document: the ownership index is
 * authoritative for documents written through the callables; documents written before
 * the index existed still carry the legacy ownerUid field, and ownership keeps
 * validating against it until a replay through the callable backfills the index.
 * Returns null when neither source names an owner (fail closed for every caller).
 */
function ownerOfExistingDocument(existingDoc, existingIndexDoc) {
  if (existingIndexDoc.exists) {
    const ownerUid = (existingIndexDoc.data() || {}).ownerUid;
    return typeof ownerUid === "string" && ownerUid.length > 0 ? ownerUid : null;
  }
  const legacy = existingDoc.exists ? (existingDoc.data() || {}).ownerUid : null;
  return typeof legacy === "string" && legacy.length > 0 ? legacy : null;
}

/**
 * The complete server-owned community tip post used by the postCommunityTip Cloud
 * Function. `db` is the Admin Firestore instance (bypasses rules; the function is the
 * only writer of community_tips). ONE Firestore transaction:
 *
 * - reads the existing document and its ownership record: a replay updates the
 *   client-controlled fields for the verified owner (preserving the server-owned
 *   upvotes counter and legacyUpvotes), backfills the ownership index when the retried
 *   document predates it, and rejects any other owner's document;
 * - otherwise reads and consumes the per-UID rate-limit slot (only genuinely new tips
 *   consume budget);
 * - commits the tip document (server-set upvotes: 0, NO ownerUid) together with its
 *   ownership record, so no invocation can leave an orphaned or unattributed tip.
 *
 * [options.rateLimit] = { ref, maxPerWindow, windowMs } consumes one slot per committed
 * new post only; replays never consume budget. [options.nowMs] injects the clock for
 * tests. Returns { tipId, replayed }.
 */
async function postCommunityTipDocument(db, ownerUid, data, options = {}) {
  if (!ownerUid) {
    throw new HttpsError("unauthenticated", "User authentication required to post a community tip.");
  }
  const nowMs = typeof options.nowMs === "function" ? options.nowMs() : options.nowMs ?? Date.now();
  const validated = validateCommunityTipPayload(data, nowMs);

  const docRef = db.collection(TIPS_COLLECTION).doc(validated.id);
  const indexRef = ownershipIndexRef(db, TIPS_COLLECTION, validated.id);
  const rateLimit = options.rateLimit || null;

  return db.runTransaction(async (transaction) => {
    // All reads precede all writes (Firestore transaction requirement).
    const existingDoc = await transaction.get(docRef);
    const existingIndexDoc = await transaction.get(indexRef);

    if (existingDoc.exists) {
      const owner = ownerOfExistingDocument(existingDoc, existingIndexDoc);
      if (!owner) {
        throw new HttpsError("permission-denied", "This community tip has no verifiable owner and cannot be modified.");
      }
      if (owner !== ownerUid) {
        throw new HttpsError("permission-denied", "This community tip belongs to another user.");
      }
      // Idempotent replay/refresh: update the client-controlled fields, keep the
      // server-owned counters exactly as they are, and backfill the ownership index for
      // documents written before it existed (one-by-one migration on verified replays).
      transaction.update(docRef, {
        id: validated.id,
        spotId: validated.spotId,
        spotTitle: validated.spotTitle,
        authorName: validated.authorName,
        content: validated.content,
        timestamp: validated.timestamp,
        isStatusUpdate: validated.isStatusUpdate,
        statusCategory: validated.statusCategory,
      });
      if (!existingIndexDoc.exists) {
        transaction.set(indexRef, { ownerUid, createdAt: nowMs });
      }
      return { tipId: validated.id, replayed: true };
    }

    if (rateLimit) {
      const allowed = await decideRateLimitInTransaction(
        transaction,
        rateLimit.ref,
        rateLimit.maxPerWindow,
        nowMs,
        rateLimit.windowMs
      );
      if (!allowed) {
        throw new HttpsError("resource-exhausted", TIP_LIMIT_MESSAGE);
      }
    }

    transaction.create(docRef, {
      id: validated.id,
      spotId: validated.spotId,
      spotTitle: validated.spotTitle,
      authorName: validated.authorName,
      content: validated.content,
      timestamp: validated.timestamp,
      isStatusUpdate: validated.isStatusUpdate,
      statusCategory: validated.statusCategory,
      legacyUpvotes: validated.legacyUpvotes,
      upvotes: 0,
    });
    transaction.set(indexRef, { ownerUid, createdAt: nowMs });
    return { tipId: validated.id, replayed: false };
  });
}

/**
 * The complete server-owned community submission post used by the submitCommunitySpot
 * Cloud Function. Same guarantees as postCommunityTipDocument for the
 * community_submissions collection: no ownerUid in the public document, ownership
 * recorded and validated through the index (with the legacy-field fallback), and the
 * per-UID rate limit consumed atomically with a genuinely new submission only.
 * [options.rateLimit] = { ref, maxPerWindow, windowMs }. Returns { submissionId, replayed }.
 */
async function submitCommunitySpotDocument(db, ownerUid, data, options = {}) {
  if (!ownerUid) {
    throw new HttpsError("unauthenticated", "User authentication required to submit a community spot.");
  }
  const nowMs = typeof options.nowMs === "function" ? options.nowMs() : options.nowMs ?? Date.now();
  const validated = validateCommunitySubmissionPayload(data, ownerUid, nowMs);

  const docRef = db.collection(SUBMISSIONS_COLLECTION).doc(validated.id);
  const indexRef = ownershipIndexRef(db, SUBMISSIONS_COLLECTION, validated.id);
  const rateLimit = options.rateLimit || null;

  return db.runTransaction(async (transaction) => {
    // All reads precede all writes (Firestore transaction requirement).
    const existingDoc = await transaction.get(docRef);
    const existingIndexDoc = await transaction.get(indexRef);

    if (existingDoc.exists) {
      const owner = ownerOfExistingDocument(existingDoc, existingIndexDoc);
      if (!owner) {
        throw new HttpsError("permission-denied", "This community submission has no verifiable owner and cannot be modified.");
      }
      if (owner !== ownerUid) {
        throw new HttpsError("permission-denied", "This community submission belongs to another user.");
      }
      transaction.update(docRef, { ...validated });
      if (!existingIndexDoc.exists) {
        transaction.set(indexRef, { ownerUid, createdAt: nowMs });
      }
      return { submissionId: validated.id, replayed: true };
    }

    if (rateLimit) {
      const allowed = await decideRateLimitInTransaction(
        transaction,
        rateLimit.ref,
        rateLimit.maxPerWindow,
        nowMs,
        rateLimit.windowMs
      );
      if (!allowed) {
        throw new HttpsError("resource-exhausted", SUBMISSION_LIMIT_MESSAGE);
      }
    }

    transaction.create(docRef, { ...validated });
    transaction.set(indexRef, { ownerUid, createdAt: nowMs });
    return { submissionId: validated.id, replayed: false };
  });
}

module.exports = {
  TIPS_COLLECTION,
  SUBMISSIONS_COLLECTION,
  COMMUNITY_RATE_LIMIT_WINDOW_MS,
  TIP_RATE_LIMIT_MAX_PER_DAY,
  SUBMISSION_RATE_LIMIT_MAX_PER_DAY,
  TIP_LIMIT_MESSAGE,
  SUBMISSION_LIMIT_MESSAGE,
  MAX_ID_LENGTH,
  MAX_TIP_TITLE_LENGTH,
  MAX_SPOT_ID_LENGTH,
  MAX_AUTHOR_NAME_LENGTH,
  MAX_TIP_CONTENT_LENGTH,
  MAX_STATUS_CATEGORY_LENGTH,
  MAX_SUBMISSION_TITLE_LENGTH,
  MAX_DESCRIPTION_LENGTH,
  MAX_CATEGORY_LENGTH,
  MAX_LOCATION_NAME_LENGTH,
  MAX_CONTRIBUTOR_LENGTH,
  TIP_PAYLOAD_KEYS,
  SUBMISSION_PAYLOAD_KEYS,
  validateCommunityTipPayload,
  validateCommunitySubmissionPayload,
  ownerOfExistingDocument,
  postCommunityTipDocument,
  submitCommunitySpotDocument,
};

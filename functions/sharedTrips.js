/**
 * Shared-trip schema validation — production helper module.
 *
 * Shared trips are written ONLY by the trusted publishSharedTrip Cloud Function
 * (firestore.rules denies direct client creation, because the rules language has
 * no list iteration and caps rule expression size, so full per-spot validation
 * cannot be enforced in rules). This module is the trusted validation boundary:
 * it validates the exact payload the Android serializer (SharedTripManager)
 * produces and returns the normalized document.
 *
 * Tests must import this module (not copy its logic).
 */

const { HttpsError } = require("firebase-functions/v2/https");
const { Timestamp } = require("firebase-admin/firestore");
const { decideRateLimitInTransaction } = require("./rateLimits");

const MAX_SPOTS = 50;
const MAX_TITLE_LENGTH = 120;
const MAX_SPOT_ID_LENGTH = 128;
const MAX_CATEGORY_LENGTH = 32;
const MAX_IMAGE_URL_LENGTH = 2048;
const MAX_REQUEST_ID_LENGTH = 128;
const TTL_DURATION_MS = 30 * 24 * 3600 * 1000; // 30-day maximum lifetime

// Canonical category policy shared with the Android app (ScoutCategory): spot
// categories are normalized to exactly one of these ids before persistence.
const ALLOWED_CATEGORIES = ["sights", "eats", "history", "shortcut"];

// Hosts permitted for shared-trip spot imagery. Every viewer's device fetches these
// URLs, so only well-known, purpose-fit hosts are allowed: an arbitrary host would
// expose each viewer's IP address to a third party and enable cross-user tracking
// (the same reasoning as firestore.rules' isValidPhotoUri for community submissions).
// Grounded in the app's real image sources: catalog/curated spots serve Wikimedia
// Commons thumbnails; Gemini-authored suggestions may reference Unsplash.
const ALLOWED_IMAGE_HOSTS = ["upload.wikimedia.org", "images.unsplash.com"];

const TRIP_COLLECTION = "shared_trips";
const PUBLISH_REQUEST_COLLECTION = "shared_trip_publish_requests";
const SHAREABLE_LINK_BASE = "https://vividscout.app/trip/";

/**
 * Publish payload contract: clients control ONLY the idempotency request id, the trip
 * title, and the waypoint list. The document id, createdAt and expireAt are server-owned
 * and are never accepted from (or trusted from) a client.
 */
const PUBLISH_TOP_LEVEL_KEYS = ["requestId", "title", "spots"];
const SPOT_KEYS = ["spotId", "title", "category", "latitude", "longitude", "imageUrl"];

const isPlainObject = (value) =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const isFiniteNumber = (value) => typeof value === "number" && Number.isFinite(value);

const invalid = (message) => new HttpsError("invalid-argument", message);

/**
 * Validates a shared-trip waypoint exactly as serialized by SharedTripManager:
 * all six fields present and of the permitted type/length, coordinates within
 * range and not null-island. User-provided strings are trimmed before validation
 * and persistence, categories must match the canonical category policy, and a
 * non-empty imageUrl must be a valid https URL on an allowed image host
 * (ALLOWED_IMAGE_HOSTS). The empty string is the Android
 * serializer's deliberate representation of a missing image and is permitted.
 */
function validateSharedTripSpot(spot) {
  if (!isPlainObject(spot)) {
    throw invalid("Each shared trip spot must be a JSON object.");
  }
  for (const key of Object.keys(spot)) {
    if (!SPOT_KEYS.includes(key)) {
      throw invalid(`Unexpected field '${key}' in shared trip spot. Allowed: ${SPOT_KEYS.join(", ")}.`);
    }
  }
  for (const key of SPOT_KEYS) {
    if (spot[key] === undefined || spot[key] === null) {
      throw invalid(`Missing mandatory field '${key}' in shared trip spot.`);
    }
  }

  // Trim user-provided strings before validation and persistence so surrounding
  // whitespace can never smuggle a blank or oversized value through.
  if (typeof spot.spotId !== "string") {
    throw invalid("Shared trip spot 'spotId' must be a string.");
  }
  const spotId = spot.spotId.trim();
  if (spotId.length === 0 || spotId.length > MAX_SPOT_ID_LENGTH) {
    throw invalid(`Shared trip spot 'spotId' must be a non-blank string of at most ${MAX_SPOT_ID_LENGTH} characters.`);
  }
  if (typeof spot.title !== "string") {
    throw invalid("Shared trip spot 'title' must be a string.");
  }
  const title = spot.title.trim();
  if (title.length === 0 || title.length > MAX_TITLE_LENGTH) {
    throw invalid(`Shared trip spot 'title' must be a non-blank string of at most ${MAX_TITLE_LENGTH} characters.`);
  }
  if (typeof spot.category !== "string") {
    throw invalid("Shared trip spot 'category' must be a string.");
  }
  const category = spot.category.trim().toLowerCase();
  if (category.length === 0 || category.length > MAX_CATEGORY_LENGTH) {
    throw invalid(`Shared trip spot 'category' must be a non-blank string of at most ${MAX_CATEGORY_LENGTH} characters.`);
  }
  if (!ALLOWED_CATEGORIES.includes(category)) {
    throw invalid(`Shared trip spot 'category' must be one of: ${ALLOWED_CATEGORIES.join(", ")}.`);
  }
  if (!isFiniteNumber(spot.latitude) || spot.latitude < -90 || spot.latitude > 90) {
    throw invalid("Shared trip spot 'latitude' must be a finite number between -90 and 90.");
  }
  if (!isFiniteNumber(spot.longitude) || spot.longitude < -180 || spot.longitude > 180) {
    throw invalid("Shared trip spot 'longitude' must be a finite number between -180 and 180.");
  }
  if (spot.latitude === 0 && spot.longitude === 0) {
    throw invalid("Shared trip spot coordinates may not both be zero.");
  }

  if (typeof spot.imageUrl !== "string") {
    throw invalid("Shared trip spot 'imageUrl' must be a string.");
  }
  const imageUrl = spot.imageUrl.trim();
  if (imageUrl.length > MAX_IMAGE_URL_LENGTH) {
    throw invalid(`Shared trip spot 'imageUrl' must be a string of at most ${MAX_IMAGE_URL_LENGTH} characters.`);
  }
  if (imageUrl.length > 0) {
    // Only the empty string (missing image) is exempt; every other value must be a
    // well-formed https URL on an allowed image host so shared trips can never carry
    // arbitrary link targets (or expose viewers' IP addresses to unknown hosts).
    let parsed;
    try {
      parsed = new URL(imageUrl);
    } catch {
      throw invalid("Shared trip spot 'imageUrl' must be a valid https URL.");
    }
    if (parsed.protocol !== "https:") {
      throw invalid("Shared trip spot 'imageUrl' must be an https URL.");
    }
    if (!ALLOWED_IMAGE_HOSTS.includes(parsed.hostname)) {
      throw invalid(
        `Shared trip spot 'imageUrl' must be hosted on an allowed image host: ${ALLOWED_IMAGE_HOSTS.join(", ")}.`
      );
    }
  }

  return {
    spotId,
    title,
    category,
    latitude: spot.latitude,
    longitude: spot.longitude,
    imageUrl,
  };
}

/**
 * Validates the client-controlled publish payload: an idempotency requestId, a trip
 * title, and a list of at most MAX_SPOTS waypoints. The server owns the document id
 * and the creation/expiry timestamps, so those fields are NOT accepted here —
 * supplying them is rejected as an unexpected field. Throws HttpsError("invalid-argument")
 * for any violation. Returns the normalized publish payload.
 */
function validateSharedTripPublishPayload(data) {
  if (!isPlainObject(data)) {
    throw invalid("Shared trip payload must be a JSON object.");
  }

  for (const key of Object.keys(data)) {
    if (!PUBLISH_TOP_LEVEL_KEYS.includes(key)) {
      throw invalid(`Unexpected field '${key}'. Allowed fields: ${PUBLISH_TOP_LEVEL_KEYS.join(", ")}.`);
    }
  }
  for (const key of PUBLISH_TOP_LEVEL_KEYS) {
    if (data[key] === undefined || data[key] === null) {
      throw invalid(`Missing mandatory field '${key}'.`);
    }
  }

  // Client-generated idempotency key: restrict to the character set of a UUID so it
  // can never smuggle path separators or arbitrary content into a document id.
  const requestId = data.requestId;
  if (
    typeof requestId !== "string" ||
    requestId.length === 0 ||
    requestId.length > MAX_REQUEST_ID_LENGTH ||
    !/^[A-Za-z0-9_-]{8,}$/.test(requestId)
  ) {
    throw invalid(`'requestId' must be an alphanumeric string of 8 to ${MAX_REQUEST_ID_LENGTH} characters.`);
  }

  const title = data.title;
  if (typeof title !== "string" || title.trim().length === 0 || title.length > MAX_TITLE_LENGTH) {
    throw invalid(`'title' must be a non-blank string of at most ${MAX_TITLE_LENGTH} characters.`);
  }

  if (!Array.isArray(data.spots)) {
    throw invalid("'spots' must be a list of spot objects.");
  }
  if (data.spots.length === 0) {
    throw invalid("'spots' must contain at least one waypoint.");
  }
  if (data.spots.length > MAX_SPOTS) {
    throw invalid(`'spots' must contain at most ${MAX_SPOTS} entries.`);
  }
  const spots = data.spots.map(validateSharedTripSpot);

  return { requestId, title: title.trim(), spots };
}

/**
 * Builds the shared-trips document exactly as persisted by the trusted writer: an
 * unpredictable server-generated [tripId], server time [nowMs] as createdAt, and an
 * expireAt of exactly TTL_DURATION_MS (30 days) later. No client-controlled value
 * reaches the document identifier or the timestamps.
 */
function buildSharedTripDocument(tripId, validated, nowMs) {
  return {
    id: tripId,
    title: validated.title,
    spots: validated.spots,
    createdAt: Timestamp.fromMillis(nowMs),
    expireAt: Timestamp.fromMillis(nowMs + TTL_DURATION_MS),
  };
}

const linkFor = (tripId) => `${SHAREABLE_LINK_BASE}${tripId}`;

/**
 * The complete server-owned publishing operation used by the publishSharedTrip Cloud
 * Function. `db` is the Admin Firestore instance (bypasses rules; the function is the
 * only writer of shared_trips). The critical persistence is ONE Firestore transaction:
 *
 * - validation happens first, outside the transaction;
 * - the transaction reads the idempotency request marker, and if it exists returns the
 *   already-published trip for the same owner (replay) or rejects another owner's reuse;
 * - the rate-limit counter is read and, when allowed, incremented inside the SAME
 *   transaction (options.rateLimit = { ref, maxPerHour }) — so a slot is consumed
 *   exactly when the publish commits, and a failed or contended publish consumes none;
 * - the trip document and its idempotency record are created together in one commit, so
 *   no invocation can ever leave an orphaned trip document.
 *
 * The trip document reference is allocated ONCE before the transaction: transaction
 * callback retries reuse the same id, never generate a fresh one.
 *
 * - The document id is generated server-side (unpredictable auto-id); the client cannot
 *   choose or predict it, and cannot address an existing document.
 * - createdAt/expireAt are generated from the server's clock at publish time; the
 *   lifetime is exactly 30 days.
 * - Replays are idempotent and consume no rate-limit budget; concurrent duplicates of
 *   the same requestId all resolve to the single published trip.
 *
 * Returns { tripId, shareableLink, replayed }.
 */
async function publishSharedTripDocument(db, ownerUid, data, options = {}) {
  if (!ownerUid) {
    throw new HttpsError("unauthenticated", "User authentication required to publish a shared trip.");
  }
  const validated = validateSharedTripPublishPayload(data);

  const requestRef = db.collection(PUBLISH_REQUEST_COLLECTION).doc(validated.requestId);
  const rateLimit = options.rateLimit || null;
  const nowMs = Date.now();

  // Allocated once, before the transaction: a callback retry must not mint a new id.
  const tripRef = db.collection(TRIP_COLLECTION).doc();

  const outcome = await db.runTransaction(async (transaction) => {
    // All reads precede all writes (Firestore transaction requirement).
    const requestDoc = await transaction.get(requestRef);

    // 1. Idempotent replay: this exact request already published a trip. Concurrent
    //    duplicates that lost the race reach this branch after the transaction retry
    //    re-reads the marker — they replay the winner instead of writing anything.
    if (requestDoc.exists) {
      const record = requestDoc.data();
      if (record.ownerUid !== ownerUid) {
        throw new HttpsError("already-exists", "This publish request identifier is already in use.");
      }
      return { tripId: record.tripId, replayed: true };
    }

    // 2. Rate limit, consumed only by genuinely new publishes, atomically with them.
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
          "Rate limit exceeded. Maximum 10 shared trips per hour per user."
        );
      }
    }

    // 3. Exactly one trip document and its idempotency record, committed together.
    transaction.create(tripRef, buildSharedTripDocument(tripRef.id, validated, nowMs));
    transaction.create(requestRef, {
      ownerUid,
      tripId: tripRef.id,
      createdAt: Timestamp.fromMillis(nowMs),
    });
    return { tripId: tripRef.id, replayed: false };
  });

  return { tripId: outcome.tripId, shareableLink: linkFor(outcome.tripId), replayed: outcome.replayed };
}

module.exports = {
  MAX_SPOTS,
  MAX_TITLE_LENGTH,
  MAX_SPOT_ID_LENGTH,
  MAX_CATEGORY_LENGTH,
  MAX_IMAGE_URL_LENGTH,
  TTL_DURATION_MS,
  SHAREABLE_LINK_BASE,
  TRIP_COLLECTION,
  PUBLISH_REQUEST_COLLECTION,
  ALLOWED_CATEGORIES,
  ALLOWED_IMAGE_HOSTS,
  validateSharedTripPublishPayload,
  validateSharedTripSpot,
  buildSharedTripDocument,
  publishSharedTripDocument,
};

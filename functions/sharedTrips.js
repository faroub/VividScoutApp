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

const MAX_SPOTS = 50;
const MAX_TITLE_LENGTH = 120;
const MAX_SPOT_ID_LENGTH = 128;
const MAX_CATEGORY_LENGTH = 32;
const MAX_IMAGE_URL_LENGTH = 2048;
const MAX_REQUEST_ID_LENGTH = 128;
const TTL_DURATION_MS = 30 * 24 * 3600 * 1000; // 30-day maximum lifetime

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
 * range and not null-island.
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
  if (typeof spot.spotId !== "string" || spot.spotId.length > MAX_SPOT_ID_LENGTH) {
    throw invalid(`Shared trip spot 'spotId' must be a string of at most ${MAX_SPOT_ID_LENGTH} characters.`);
  }
  if (typeof spot.title !== "string" || spot.title.length === 0 || spot.title.length > MAX_TITLE_LENGTH) {
    throw invalid(`Shared trip spot 'title' must be a non-empty string of at most ${MAX_TITLE_LENGTH} characters.`);
  }
  if (typeof spot.category !== "string" || spot.category.length > MAX_CATEGORY_LENGTH) {
    throw invalid(`Shared trip spot 'category' must be a string of at most ${MAX_CATEGORY_LENGTH} characters.`);
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
  if (typeof spot.imageUrl !== "string" || spot.imageUrl.length > MAX_IMAGE_URL_LENGTH) {
    throw invalid(`Shared trip spot 'imageUrl' must be a string of at most ${MAX_IMAGE_URL_LENGTH} characters.`);
  }

  return {
    spotId: spot.spotId,
    title: spot.title,
    category: spot.category,
    latitude: spot.latitude,
    longitude: spot.longitude,
    imageUrl: spot.imageUrl,
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
  if (typeof title !== "string" || title.length === 0 || title.length > MAX_TITLE_LENGTH) {
    throw invalid(`'title' must be a non-empty string of at most ${MAX_TITLE_LENGTH} characters.`);
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

  return { requestId, title, spots };
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
 * only writer of shared_trips). Guarantees:
 *
 * - The document id is generated server-side (unpredictable auto-id); the client cannot
 *   choose or predict it, and cannot address an existing document.
 * - The trip document is created with create() semantics: an atomic create-only write
 *   that fails with ALREADY_EXISTS instead of overwriting an existing document.
 * - createdAt/expireAt are generated from the server's clock at publish time; the
 *   lifetime is exactly 30 days.
 * - An idempotency record maps the client requestId to the published tripId, so a
 *   duplicate request or retry after a lost response replays the ALREADY published
 *   trip instead of publishing a second one. Concurrent duplicates race on the
 *   idempotency record and all but one replay the winner.
 * - [options.shouldRateLimit] gates only NEW publishes; idempotent replays never
 *   consume publishing rate-limit budget.
 *
 * Returns { tripId, shareableLink, replayed }.
 */
async function publishSharedTripDocument(db, ownerUid, data, options = {}) {
  if (!ownerUid) {
    throw new HttpsError("unauthenticated", "User authentication required to publish a shared trip.");
  }
  const validated = validateSharedTripPublishPayload(data);

  // 1. Idempotent replay: return the trip this exact request already published.
  const requestRef = db.collection(PUBLISH_REQUEST_COLLECTION).doc(validated.requestId);
  const existing = await requestRef.get();
  if (existing.exists) {
    const record = existing.data();
    if (record.ownerUid !== ownerUid) {
      throw new HttpsError("already-exists", "This publish request identifier is already in use.");
    }
    return { tripId: record.tripId, shareableLink: linkFor(record.tripId), replayed: true };
  }

  // 2. Rate limit consumes budget only for genuinely new publishes.
  if (options.shouldRateLimit) {
    const isAllowed = await options.shouldRateLimit();
    if (!isAllowed) {
      throw new HttpsError(
        "resource-exhausted",
        "Rate limit exceeded. Maximum 10 shared trips per hour per user."
      );
    }
  }

  // 3. Server-owned id + timestamps; create() never overwrites an existing document.
  const nowMs = Date.now();
  const tripRef = db.collection(TRIP_COLLECTION).doc();
  await tripRef.create(buildSharedTripDocument(tripRef.id, validated, nowMs));

  // 4. Record the idempotency marker. A lost race means another invocation with the
  // same requestId published first: replay its published trip instead of leaking a
  // second document.
  try {
    await requestRef.create({
      ownerUid,
      tripId: tripRef.id,
      createdAt: Timestamp.fromMillis(nowMs),
    });
  } catch (err) {
    const isAlreadyExists =
      err.code === 6 ||
      String(err.code).includes("already-exists") ||
      String(err.message).toLowerCase().includes("already-exists");
    if (!isAlreadyExists) {
      throw err;
    }
    const winner = await requestRef.get();
    const record = winner && winner.exists ? winner.data() : null;
    if (record && record.tripId === tripRef.id) {
      return { tripId: tripRef.id, shareableLink: linkFor(tripRef.id), replayed: false };
    }
    throw new HttpsError("already-exists", "This publish request was already completed.");
  }

  return { tripId: tripRef.id, shareableLink: linkFor(tripRef.id), replayed: false };
}

module.exports = {
  MAX_SPOTS,
  MAX_TITLE_LENGTH,
  TTL_DURATION_MS,
  SHAREABLE_LINK_BASE,
  TRIP_COLLECTION,
  PUBLISH_REQUEST_COLLECTION,
  validateSharedTripPublishPayload,
  validateSharedTripSpot,
  buildSharedTripDocument,
  publishSharedTripDocument,
};

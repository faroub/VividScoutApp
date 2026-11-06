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

const MAX_SPOTS = 50;
const MAX_TITLE_LENGTH = 120;
const MAX_ID_LENGTH = 128;
const MAX_SPOT_ID_LENGTH = 128;
const MAX_CATEGORY_LENGTH = 32;
const MAX_IMAGE_URL_LENGTH = 2048;
const TTL_DURATION_MS = 30 * 24 * 3600 * 1000; // 30-day maximum lifetime

const TOP_LEVEL_KEYS = ["id", "title", "spots", "createdAt", "expireAt"];
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
 * Validates the shared-trip payload produced by the Android serializer and
 * returns the normalized document with epoch-millis timestamps. Throws
 * HttpsError("invalid-argument") for any violation:
 * unexpected or missing top-level fields, id/title bounds, a spots list of
 * more than MAX_SPOTS entries, any malformed waypoint, or a lifetime that is
 * not (now, now + 30 days].
 */
function validateSharedTripPayload(data, now = Date.now()) {
  if (!isPlainObject(data)) {
    throw invalid("Shared trip payload must be a JSON object.");
  }

  for (const key of Object.keys(data)) {
    if (!TOP_LEVEL_KEYS.includes(key)) {
      throw invalid(`Unexpected field '${key}'. Allowed fields: ${TOP_LEVEL_KEYS.join(", ")}.`);
    }
  }
  for (const key of TOP_LEVEL_KEYS) {
    if (data[key] === undefined || data[key] === null) {
      throw invalid(`Missing mandatory field '${key}'.`);
    }
  }

  const id = data.id;
  if (typeof id !== "string" || id.length === 0 || id.length > MAX_ID_LENGTH) {
    throw invalid(`'id' must be a non-empty string of at most ${MAX_ID_LENGTH} characters.`);
  }

  const title = data.title;
  if (typeof title !== "string" || title.length === 0 || title.length > MAX_TITLE_LENGTH) {
    throw invalid(`'title' must be a non-empty string of at most ${MAX_TITLE_LENGTH} characters.`);
  }

  if (!Array.isArray(data.spots)) {
    throw invalid("'spots' must be a list of spot objects.");
  }
  if (data.spots.length > MAX_SPOTS) {
    throw invalid(`'spots' must contain at most ${MAX_SPOTS} entries.`);
  }
  const spots = data.spots.map(validateSharedTripSpot);

  const createdAt = data.createdAt;
  if (!isFiniteNumber(createdAt)) {
    throw invalid("'createdAt' must be epoch milliseconds.");
  }
  const expireAt = data.expireAt;
  if (!isFiniteNumber(expireAt)) {
    throw invalid("'expireAt' must be epoch milliseconds.");
  }
  if (expireAt <= now) {
    throw invalid("'expireAt' must be in the future.");
  }
  if (expireAt > now + TTL_DURATION_MS) {
    throw invalid("'expireAt' must be at most 30 days in the future.");
  }

  return { id, title, spots, createdAt, expireAt };
}

module.exports = {
  MAX_SPOTS,
  MAX_TITLE_LENGTH,
  TTL_DURATION_MS,
  validateSharedTripPayload,
  validateSharedTripSpot,
};

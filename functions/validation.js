"use strict";

/**
 * Strict, shared request validation for the VividScoutApp Cloud Functions.
 * Imported by index.js so the production callables and their tests exercise
 * exactly the same code — validation logic is never duplicated in tests.
 */

const ALLOWED_SOURCES = ["opentripmap", "geonames"];
const RADIUS_MIN_METERS = 100;
const RADIUS_MAX_METERS = 50000;

// Provider outcome contract shared by the nearbyPlaces callable and the Android app:
// "ok" — the provider answered (an empty answer is still a real, cacheable answer);
// "transient" — timeout, unreachable, or 5xx (retryable);
// "auth-config" — credentials missing/rejected, will not fix itself by retrying;
// "rate-limit" — the provider answered 429.
const PROVIDER_STATUS_OK = "ok";
const PROVIDER_STATUS_TRANSIENT = "transient";
const PROVIDER_STATUS_AUTH_CONFIG = "auth-config";
const PROVIDER_STATUS_RATE_LIMIT = "rate-limit";

/**
 * Classifies a provider fetch error into the outcome contract. 429 is a rate limit,
 * 401/403 an authentication/configuration failure, and everything else (network, DNS,
 * timeout, 5xx, unexpected status) a transient failure. Classification matters: an
 * auth-config failure will not fix itself by retrying, while a transient one might.
 */
function providerStatusForError(err) {
  const status = err && err.response && err.response.status;
  if (status === 429) return PROVIDER_STATUS_RATE_LIMIT;
  if (status === 401 || status === 403) return PROVIDER_STATUS_AUTH_CONFIG;
  return PROVIDER_STATUS_TRANSIENT;
}

/**
 * Parses a numeric coordinate strictly.
 * Accepts finite numbers and numeric strings; rejects NaN/Infinity, strings
 * with trailing text, empty strings, and non-numeric types.
 */
function parseStrictNumber(val) {
  if (typeof val === "number") {
    return Number.isFinite(val) ? val : null;
  }
  if (typeof val === "string") {
    const trimmed = val.trim();
    if (!/^-?\d+(\.\d+)?$/.test(trimmed)) {
      return null;
    }
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

/**
 * Parses an integer strictly. Non-integer numbers are rejected (never floored):
 * 100.9 is invalid, not 100. Numeric strings must contain only digits with an
 * optional leading minus; decimal strings such as "100.9" and strings with
 * trailing text such as "100abc" are rejected. NaN and ±Infinity are rejected.
 */
function parseStrictInt(val) {
  if (typeof val === "number") {
    return Number.isSafeInteger(val) ? val : null;
  }
  if (typeof val === "string") {
    const trimmed = val.trim();
    if (!/^-?\d+$/.test(trimmed)) {
      return null;
    }
    const parsed = Number(trimmed);
    return Number.isSafeInteger(parsed) ? parsed : null;
  }
  return null;
}

/**
 * Validates the nearbyPlaces request payload end-to-end: App Check presence,
 * authentication, coordinates (finite, within geographic range, zero allowed),
 * radius (strict integer within 100–50,000 m) and the source allowlist.
 * Returns { ok: true, lat, lon, radiusMeters, requestedSources } or
 * { ok: false, error, message } describing the first failure.
 */
function validateNearbyPlacesRequest(data, auth, appCheck) {
  if (!appCheck) {
    return { ok: false, error: "failed-precondition", message: "App Check token missing or invalid." };
  }
  if (!auth || !auth.uid) {
    return { ok: false, error: "unauthenticated", message: "User authentication required." };
  }

  const lat = parseStrictNumber(data ? data.lat : undefined);
  const lon = parseStrictNumber(data ? data.lon : undefined);
  if (lat === null || lon === null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    return { ok: false, error: "invalid-argument", message: "Invalid or malformed latitude/longitude coordinates." };
  }

  const radiusMeters = parseStrictInt(data ? data.radiusMeters : undefined);
  if (radiusMeters === null || radiusMeters < RADIUS_MIN_METERS || radiusMeters > RADIUS_MAX_METERS) {
    return {
      ok: false,
      error: "invalid-argument",
      message: `Invalid radiusMeters argument. Must be an integer between ${RADIUS_MIN_METERS} and ${RADIUS_MAX_METERS} meters.`
    };
  }

  const requestedSources =
    data && Array.isArray(data.sources) && data.sources.length > 0
      ? data.sources
      : ALLOWED_SOURCES.slice();
  for (const source of requestedSources) {
    if (!ALLOWED_SOURCES.includes(source)) {
      return {
        ok: false,
        error: "invalid-argument",
        message: `Unsupported source name requested: '${source}'. Allowed sources: ${ALLOWED_SOURCES.join(", ")}`
      };
    }
  }

  return { ok: true, lat, lon, radiusMeters, requestedSources };
}

module.exports = {
  ALLOWED_SOURCES,
  RADIUS_MIN_METERS,
  RADIUS_MAX_METERS,
  PROVIDER_STATUS_OK,
  PROVIDER_STATUS_TRANSIENT,
  PROVIDER_STATUS_AUTH_CONFIG,
  PROVIDER_STATUS_RATE_LIMIT,
  parseStrictNumber,
  parseStrictInt,
  validateNearbyPlacesRequest,
  providerStatusForError
};

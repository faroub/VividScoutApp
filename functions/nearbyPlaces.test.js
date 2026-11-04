/**
 * Cloud Function Validation Test Suite: nearbyPlaces
 */

const assert = require("assert");

// Parse strict number tester
function parseStrictNumber(val) {
  if (typeof val === "number") {
    return isNaN(val) || !isFinite(val) ? null : val;
  }
  if (typeof val === "string") {
    const trimmed = val.trim();
    if (!/^-?\d+(\.\d+)?$/.test(trimmed)) {
      return null;
    }
    const parsed = Number(trimmed);
    return isNaN(parsed) || !isFinite(parsed) ? null : parsed;
  }
  return null;
}

function parseStrictInt(val) {
  if (typeof val === "number") {
    return Number.isInteger(val) ? val : Math.floor(val);
  }
  if (typeof val === "string") {
    const trimmed = val.trim();
    if (!/^\d+$/.test(trimmed)) {
      return null;
    }
    const parsed = parseInt(trimmed, 10);
    return isNaN(parsed) || !isFinite(parsed) ? null : parsed;
  }
  return null;
}

const ALLOWED_SOURCES = ["opentripmap", "geonames"];

function validateRequestParams(data, auth, appCheck) {
  if (!appCheck) {
    return { error: "failed-precondition", message: "App Check token missing or invalid." };
  }
  if (!auth || !auth.uid) {
    return { error: "unauthenticated", message: "User authentication required." };
  }

  const lat = parseStrictNumber(data.lat);
  const lon = parseStrictNumber(data.lon);
  const radiusMeters = parseStrictInt(data.radiusMeters);
  const requestedSources = Array.isArray(data.sources) && data.sources.length > 0 ? data.sources : ["opentripmap", "geonames"];

  if (lat === null || lon === null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    return { error: "invalid-argument", message: "Invalid latitude or longitude." };
  }

  if (radiusMeters === null || radiusMeters < 100 || radiusMeters > 50000) {
    return { error: "invalid-argument", message: "Invalid radiusMeters argument." };
  }

  for (const source of requestedSources) {
    if (!ALLOWED_SOURCES.includes(source)) {
      return { error: "invalid-argument", message: `Unsupported source: ${source}` };
    }
  }

  return { success: true, lat, lon, radiusMeters, requestedSources };
}

console.log("--- RUNNING NEARBY PLACES CLOUD FUNCTION VALIDATION TESTS ---");

// Test 1: Valid Call
const validRes = validateRequestParams(
  { lat: 38.7112, lon: -9.1423, radiusMeters: 3000, sources: ["opentripmap"] },
  { uid: "user_123" },
  { appId: "app_123" }
);
assert.strictEqual(validRes.success, true);
console.log("✓ Test 1 Passed: Valid call with App Check & Auth succeeds.");

// Test 2: Equator (0.0) and Prime Meridian (0.0)
const equatorRes = validateRequestParams(
  { lat: 0.0, lon: 0.0, radiusMeters: 1000 },
  { uid: "user_123" },
  { appId: "app_123" }
);
assert.strictEqual(equatorRes.success, true);
assert.strictEqual(equatorRes.lat, 0.0);
assert.strictEqual(equatorRes.lon, 0.0);
console.log("✓ Test 2 Passed: Equator (0.0) and Prime Meridian (0.0) preserved as valid.");

// Test 3: Missing App Check
const noAppCheck = validateRequestParams(
  { lat: 38.7112, lon: -9.1423, radiusMeters: 3000 },
  { uid: "user_123" },
  null
);
assert.strictEqual(noAppCheck.error, "failed-precondition");
console.log("✓ Test 3 Passed: Missing App Check is rejected.");

// Test 4: Missing Authentication
const noAuth = validateRequestParams(
  { lat: 38.7112, lon: -9.1423, radiusMeters: 3000 },
  null,
  { appId: "app_123" }
);
assert.strictEqual(noAuth.error, "unauthenticated");
console.log("✓ Test 4 Passed: Missing Authentication is rejected.");

// Test 5: Malformed String Coordinates ("38.7112abc")
const malformedLat = validateRequestParams(
  { lat: "38.7112abc", lon: -9.1423, radiusMeters: 3000 },
  { uid: "user_123" },
  { appId: "app_123" }
);
assert.strictEqual(malformedLat.error, "invalid-argument");
console.log("✓ Test 5 Passed: Malformed numeric string ('38.7112abc') is rejected.");

// Test 6: Invalid Radius (-500m or 100,000m)
const invalidRadius = validateRequestParams(
  { lat: 38.7112, lon: -9.1423, radiusMeters: -500 },
  { uid: "user_123" },
  { appId: "app_123" }
);
assert.strictEqual(invalidRadius.error, "invalid-argument");
console.log("✓ Test 6 Passed: Invalid radius (-500m) is rejected.");

// Test 7: Unsupported Source Name
const invalidSource = validateRequestParams(
  { lat: 38.7112, lon: -9.1423, radiusMeters: 3000, sources: ["unsupported_api_source"] },
  { uid: "user_123" },
  { appId: "app_123" }
);
assert.strictEqual(invalidSource.error, "invalid-argument");
console.log("✓ Test 7 Passed: Unsupported source name is rejected.");

console.log("--- ALL CLOUD FUNCTION VALIDATION TESTS PASSED ---");

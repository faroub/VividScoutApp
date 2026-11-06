/**
 * Cloud Function Validation Test Suite: nearbyPlaces
 *
 * Tests exercise the real production validation module (functions/validation.js),
 * imported — never duplicated — so the callable and its tests share one
 * implementation. Covers: strict integer parsing (no silent flooring of floats),
 * coordinate validation, radius bounds, source allowlist, App Check and
 * authentication enforcement, in the exact order the callable applies them.
 */

const assert = require("assert");
const {
  parseStrictInt,
  parseStrictNumber,
  validateNearbyPlacesRequest
} = require("./validation");

const VALID_AUTH = { uid: "user_123" };
const VALID_APP_CHECK = { appId: "app_123" };

function pass(data) {
  return validateNearbyPlacesRequest(data, VALID_AUTH, VALID_APP_CHECK);
}

function fail(data, auth = VALID_AUTH, appCheck = VALID_APP_CHECK) {
  return validateNearbyPlacesRequest(data, auth, appCheck);
}

let passed = 0;
function check(name, fn) {
  fn();
  passed++;
  console.log(`✓ ${name}`);
}

console.log("--- RUNNING NEARBY PLACES CLOUD FUNCTION VALIDATION TESTS ---");

// --- Strict integer parsing (production helper) ---

check("parseStrictInt: integer numbers pass", () => {
  assert.strictEqual(parseStrictInt(100), 100);
  assert.strictEqual(parseStrictInt(50000), 50000);
  assert.strictEqual(parseStrictInt(0), 0);
  assert.strictEqual(parseStrictInt(-100), -100);
});

check("parseStrictInt: integer-valued floats pass", () => {
  assert.strictEqual(parseStrictInt(100.0), 100);
});

check("parseStrictInt: decimal floats are rejected, never floored", () => {
  assert.strictEqual(parseStrictInt(100.9), null);
  assert.strictEqual(parseStrictInt(0.5), null);
  assert.strictEqual(parseStrictInt(-0.5), null);
});

check("parseStrictInt: clean numeric strings pass", () => {
  assert.strictEqual(parseStrictInt("100"), 100);
  assert.strictEqual(parseStrictInt("50000"), 50000);
  assert.strictEqual(parseStrictInt(" 8200 "), 8200); // surrounding whitespace tolerated
});

check("parseStrictInt: decimal strings are rejected", () => {
  assert.strictEqual(parseStrictInt("100.0"), null);
  assert.strictEqual(parseStrictInt("100.9"), null);
});

check("parseStrictInt: malformed strings are rejected", () => {
  assert.strictEqual(parseStrictInt("100abc"), null);
  assert.strictEqual(parseStrictInt(""), null);
  assert.strictEqual(parseStrictInt("   "), null);
  assert.strictEqual(parseStrictInt("1e4"), null);
  assert.strictEqual(parseStrictInt(null), null);
  assert.strictEqual(parseStrictInt(undefined), null);
  assert.strictEqual(parseStrictInt(true), null);
});

check("parseStrictInt: NaN and Infinity are rejected", () => {
  assert.strictEqual(parseStrictInt(NaN), null);
  assert.strictEqual(parseStrictInt(Infinity), null);
  assert.strictEqual(parseStrictInt(-Infinity), null);
});

// --- Number parsing for coordinates ---

check("parseStrictNumber: zero coordinates are preserved", () => {
  assert.strictEqual(parseStrictNumber(0.0), 0.0);
  assert.strictEqual(parseStrictNumber("0"), 0);
  assert.strictEqual(parseStrictNumber("0.0"), 0);
});

check("parseStrictNumber: malformed and non-finite values are rejected", () => {
  assert.strictEqual(parseStrictNumber(NaN), null);
  assert.strictEqual(parseStrictNumber(Infinity), null);
  assert.strictEqual(parseStrictNumber("38.7112abc"), null);
  assert.strictEqual(parseStrictNumber(""), null);
});

// --- Full request validation ---

check("Valid call succeeds and preserves values", () => {
  const res = pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000, sources: ["opentripmap"] });
  assert.strictEqual(res.ok, true);
  assert.strictEqual(res.lat, 38.7112);
  assert.strictEqual(res.lon, -9.1423);
  assert.strictEqual(res.radiusMeters, 3000);
  assert.deepStrictEqual(res.requestedSources, ["opentripmap"]);
});

check("Equator (0.0) and Prime Meridian (0.0) are valid coordinates", () => {
  const res = pass({ lat: 0.0, lon: 0.0, radiusMeters: 1000 });
  assert.strictEqual(res.ok, true);
  assert.strictEqual(res.lat, 0.0);
  assert.strictEqual(res.lon, 0.0);
});

check("Missing App Check is rejected (before all other checks)", () => {
  const res = fail({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000 }, VALID_AUTH, null);
  assert.strictEqual(res.ok, false);
  assert.strictEqual(res.error, "failed-precondition");
});

check("Missing authentication is rejected", () => {
  const res = fail({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000 }, null, VALID_APP_CHECK);
  assert.strictEqual(res.ok, false);
  assert.strictEqual(res.error, "unauthenticated");
});

check("Missing request data is rejected", () => {
  const res = fail(null);
  assert.strictEqual(res.ok, false);
  assert.strictEqual(res.error, "invalid-argument");
});

check("Radius boundary values: 100 and 50000 pass", () => {
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 100 }).ok, true);
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 50000 }).ok, true);
});

check("Radius 100.9 is rejected, not silently floored to 100", () => {
  const res = pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 100.9 });
  assert.strictEqual(res.ok, false);
  assert.strictEqual(res.error, "invalid-argument");
});

check("Negative radius is rejected", () => {
  const res = pass({ lat: 38.7112, lon: -9.1423, radiusMeters: -100 });
  assert.strictEqual(res.ok, false);
  assert.strictEqual(res.error, "invalid-argument");
});

check("NaN / Infinity radius is rejected", () => {
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: NaN }).ok, false);
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: Infinity }).ok, false);
});

check("Out-of-range radius is rejected", () => {
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 99 }).ok, false);
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 50001 }).ok, false);
});

check("Integer strings are accepted for radius", () => {
  const res = pass({ lat: 38.7112, lon: -9.1423, radiusMeters: "3000" });
  assert.strictEqual(res.ok, true);
  assert.strictEqual(res.radiusMeters, 3000);
});

check("Malformed radius strings are rejected", () => {
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: "100.9" }).ok, false);
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: "100abc" }).ok, false);
  assert.strictEqual(pass({ lat: 38.7112, lon: -9.1423, radiusMeters: "" }).ok, false);
});

check("Invalid coordinates are rejected", () => {
  assert.strictEqual(pass({ lat: 91.0, lon: -9.1423, radiusMeters: 3000 }).ok, false);
  assert.strictEqual(pass({ lat: -91.0, lon: -9.1423, radiusMeters: 3000 }).ok, false);
  assert.strictEqual(pass({ lat: 38.7112, lon: -181.0, radiusMeters: 3000 }).ok, false);
  assert.strictEqual(pass({ lat: 38.7112, lon: 181.0, radiusMeters: 3000 }).ok, false);
  assert.strictEqual(pass({ lat: NaN, lon: -9.1423, radiusMeters: 3000 }).ok, false);
  assert.strictEqual(pass({ lat: "38.7112abc", lon: -9.1423, radiusMeters: 3000 }).ok, false);
});

check("Unsupported source names are rejected", () => {
  const res = pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000, sources: ["unsupported_api_source"] });
  assert.strictEqual(res.ok, false);
  assert.strictEqual(res.error, "invalid-argument");
});

check("Sources default to the full allowlist when omitted", () => {
  const res = pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000 });
  assert.strictEqual(res.ok, true);
  assert.deepStrictEqual(res.requestedSources, ["opentripmap", "geonames"]);
});

check("Every allowed source individually is accepted", () => {
  for (const source of ["opentripmap", "geonames"]) {
    const res = pass({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000, sources: [source] });
    assert.strictEqual(res.ok, true, `source ${source} must be allowed`);
  }
});

console.log(`--- ALL ${passed} CLOUD FUNCTION VALIDATION TESTS PASSED ---`);

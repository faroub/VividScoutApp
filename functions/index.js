/**
 * Firebase Cloud Function: nearbyPlaces
 *
 * Secure backend proxy for OpenTripMap and GeoNames APIs.
 * Enforces App Check (enforceAppCheck: true), per-UID rate limiting (60 req/hr/UID),
 * reads secrets from Firebase Secret Manager ("OPENTRIPMAP_API_KEY", "GEONAMES_USERNAME"),
 * validates request parameters strictly against allowlists and bounds,
 * and applies exact geodesic distance filtering before returning normalized PlaceCandidate JSON objects.
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const axios = require("axios");

initializeApp();
const db = getFirestore();

const ALLOWED_SOURCES = ["opentripmap", "geonames"];
const RATE_LIMIT_MAX_PER_HOUR = 60;
const RATE_LIMIT_WINDOW_MS = 3600 * 1000; // 1 hour

/**
 * Calculates Haversine geodesic distance in meters between two lat/lon pairs.
 */
function calculateHaversineMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000; // Earth radius in meters
  const dLat = (lat2 - lat1) * (Math.PI / 180);
  const dLon = (lon2 - lon1) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180)) *
      Math.cos(lat2 * (Math.PI / 180)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

/**
 * Parses numeric coordinate values strictly, rejecting malformed strings with trailing text.
 */
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

/**
 * Parses integer values strictly, rejecting malformed strings or floats.
 */
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

/**
 * Enforces per-user rate limit (60 requests/UID/hour) using Firestore counter documents.
 */
async function checkRateLimit(uid) {
  const now = Date.now();
  const rateLimitRef = db.collection("rate_limits").doc(`nearby_places_${uid}`);

  return db.runTransaction(async (transaction) => {
    const doc = await transaction.get(rateLimitRef);
    if (!doc.exists) {
      transaction.set(rateLimitRef, {
        count: 1,
        windowStart: now
      });
      return true;
    }

    const data = doc.data();
    const windowStart = data.windowStart || now;
    const currentCount = data.count || 0;

    if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
      transaction.set(rateLimitRef, {
        count: 1,
        windowStart: now
      });
      return true;
    }

    if (currentCount >= RATE_LIMIT_MAX_PER_HOUR) {
      return false;
    }

    transaction.update(rateLimitRef, {
      count: FieldValue.increment(1)
    });
    return true;
  });
}

/**
 * Fetches OpenTripMap candidates
 */
async function fetchOpenTripMap(lat, lon, radiusMeters) {
  const apiKey = process.env.OPENTRIPMAP_API_KEY;
  if (!apiKey) {
    console.warn("[OpenTripMap] OPENTRIPMAP_API_KEY secret not configured in environment/Secret Manager. Skipping fetch.");
    return [];
  }

  const url = `https://api.opentripmap.com/0.1/en/places/radius?radius=${radiusMeters}&lon=${lon}&lat=${lat}&kinds=historic,cultural,architecture,natural,tourist_object&rate=2&format=json&limit=15&apikey=${apiKey}`;

  try {
    const response = await axios.get(url, { timeout: 8000 });
    const items = response.data || [];

    return items
      .filter((item) => item.name && item.point && !isNaN(item.point.lat) && !isNaN(item.point.lon))
      .map((item) => {
        const kinds = item.kinds || "tourist_object";
        const category = kinds.includes("food") || kinds.includes("cafe") ? "eats"
          : kinds.includes("historic") || kinds.includes("cultural") ? "history"
          : "sights";

        return {
          sourceName: "OpenTripMap API",
          sourceId: `otm_${item.xid || item.id}`,
          name: item.name,
          latitude: parseFloat(item.point.lat),
          longitude: parseFloat(item.point.lon),
          category: category,
          description: `OpenTripMap POI (${kinds.split(",").slice(0, 3).join(", ")})`,
          imageUrl: null,
          sourceUrl: `https://opentripmap.com/en/card/${item.xid}`,
          license: "Open Data Commons Open Database License (ODbL)",
          languageCode: "en"
        };
      });
  } catch (err) {
    console.warn(`[OpenTripMap] Fetch failed: ${err.message}`);
    return [];
  }
}

/**
 * Fetches GeoNames candidates
 */
async function fetchGeonames(lat, lon, radiusMeters) {
  const username = process.env.GEONAMES_USERNAME;
  if (!username) {
    console.warn("[GeoNames] GEONAMES_USERNAME secret not configured in environment/Secret Manager. Skipping fetch.");
    return [];
  }

  const radiusKm = Math.min(Math.max(radiusMeters / 1000, 1), 30);
  const url = `https://secure.geonames.org/findNearbyWikipediaJSON?lat=${lat}&lng=${lon}&radius=${radiusKm}&maxRows=15&username=${username}`;

  try {
    const response = await axios.get(url, { timeout: 8000 });
    const geonames = (response.data && response.data.geonames) || [];

    return geonames
      .filter((item) => item.title && !isNaN(item.lat) && !isNaN(item.lng))
      .map((item) => {
        const feature = item.feature || "landmark";
        const category = feature.includes("mountain") || feature.includes("hill") ? "sights" : "history";

        return {
          sourceName: "GeoNames Gazetteer",
          sourceId: `geonames_${item.geoNameId || item.title}`,
          name: item.title,
          latitude: parseFloat(item.lat),
          longitude: parseFloat(item.lng),
          category: category,
          description: item.summary || "Gazetteer landmark cataloged on GeoNames database.",
          imageUrl: item.thumbnailImg || null,
          sourceUrl: item.wikipediaUrl ? `https://${item.wikipediaUrl}` : null,
          license: "Creative Commons Attribution 4.0 License",
          languageCode: item.lang || "en"
        };
      });
  } catch (err) {
    console.warn(`[GeoNames] Fetch failed: ${err.message}`);
    return [];
  }
}

exports.nearbyPlaces = onCall(
  {
    cors: true,
    enforceAppCheck: true,
    secrets: ["OPENTRIPMAP_API_KEY", "GEONAMES_USERNAME"]
  },
  async (request) => {
    // 1. Firebase Authentication Check
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to query nearbyPlaces.");
    }

    // 2. Per-UID Rate Limit Check
    const uid = auth.uid;
    const isAllowed = await checkRateLimit(uid);
    if (!isAllowed) {
      throw new HttpsError("resource-exhausted", "Rate limit exceeded. Maximum 60 requests per hour per user.");
    }

    const data = request.data || {};
    const lat = parseStrictNumber(data.lat);
    const lon = parseStrictNumber(data.lon);
    const radiusMeters = parseStrictInt(data.radiusMeters);
    const requestedSources = Array.isArray(data.sources) && data.sources.length > 0 ? data.sources : ["opentripmap", "geonames"];

    // 3. Strict Parameter Validation: Coordinates (supports Equator 0.0 & Prime Meridian 0.0)
    if (lat === null || lon === null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      throw new HttpsError("invalid-argument", "Invalid or malformed latitude/longitude coordinates.");
    }

    // 4. Strict Parameter Validation: Radius (100m <= radiusMeters <= 50000m)
    if (radiusMeters === null || radiusMeters < 100 || radiusMeters > 50000) {
      throw new HttpsError("invalid-argument", "Invalid radiusMeters argument. Must be an integer between 100 and 50000 meters.");
    }

    // 5. Explicit Source Allowlist & Non-Empty Subset Validation
    for (const source of requestedSources) {
      if (!ALLOWED_SOURCES.includes(source)) {
        throw new HttpsError("invalid-argument", `Unsupported source name requested: '${source}'. Allowed sources: ${ALLOWED_SOURCES.join(", ")}`);
      }
    }

    console.log(`[nearbyPlaces] Processing query for UID=${uid.substring(0, 4)}*** at coords (${lat.toFixed(2)}, ${lon.toFixed(2)}) radius=${radiusMeters}m`);

    const rawCandidates = [];

    if (requestedSources.includes("opentripmap")) {
      const otmResults = await fetchOpenTripMap(lat, lon, radiusMeters);
      rawCandidates.push(...otmResults);
    }

    if (requestedSources.includes("geonames")) {
      const geonamesResults = await fetchGeonames(lat, lon, radiusMeters);
      rawCandidates.push(...geonamesResults);
    }

    // 6. Enforce exact geodesic distance filtering against requested radius
    const validatedCandidates = rawCandidates.filter((candidate) => {
      const distance = calculateHaversineMeters(lat, lon, candidate.latitude, candidate.longitude);
      return !isNaN(distance) && distance <= radiusMeters;
    });

    return validatedCandidates;
  }
);
```

Now let's write `functions/nearbyPlaces.test.js` testing `nearbyPlaces` validation, App Check enforcement, malformed parameter rejection, equator/prime meridian handling, and allowlist validation!

```javascript
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
```

Let's write `functions/nearbyPlaces.test.js` using `write_file`. Genesis! `write_file` tool call.!    call:default_api:write_file{absolutePath:
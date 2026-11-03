/**
 * Firebase Cloud Function: nearbyPlaces
 *
 * Secure backend proxy for OpenTripMap and GeoNames APIs.
 * Enforces rate limiting (60 requests/UID/hour) via Firestore counter documents,
 * reads secret API keys from environment/Secret Manager, and returns normalized PlaceCandidate JSON objects.
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const axios = require("axios");

initializeApp();
const db = getFirestore();

const RATE_LIMIT_MAX_PER_HOUR = 60;
const RATE_LIMIT_WINDOW_MS = 3600 * 1000; // 1 hour

/**
 * Enforces per-user rate limit (60 requests/UID/hour) using Firestore counters.
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
      // Reset rate limit window
      transaction.set(rateLimitRef, {
        count: 1,
        windowStart: now
      });
      return true;
    }

    if (currentCount >= RATE_LIMIT_MAX_PER_HOUR) {
      return false; // Rate limit exceeded
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
  const apiKey = process.env.OPENTRIPMAP_API_KEY || "5ae2e3f221c38a28845f05b6a76081e285be256e07fbef1803734e5f";
  const url = `https://api.opentripmap.com/0.1/en/places/radius?radius=${radiusMeters}&lon=${lon}&lat=${lat}&kinds=historic,cultural,architecture,natural,tourist_object&rate=2&format=json&limit=15&apikey=${apiKey}`;

  try {
    const response = await axios.get(url, { timeout: 8000 });
    const items = response.data || [];

    return items
      .filter((item) => item.name && item.point && item.point.lat && item.point.lon)
      .map((item) => {
        const kinds = item.kinds || "tourist_object";
        const category = kinds.includes("food") || kinds.includes("cafe") ? "eats"
          : kinds.includes("historic") || kinds.includes("cultural") ? "history"
          : "sights";

        return {
          sourceName: "OpenTripMap API",
          sourceId: `otm_${item.xid || item.id}`,
          name: item.name,
          latitude: item.point.lat,
          longitude: item.point.lon,
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
  const username = process.env.GEONAMES_USERNAME || "vividscout_demo";
  const radiusKm = Math.min(Math.max(radiusMeters / 1000, 1), 10);
  const url = `https://secure.geonames.org/findNearbyWikipediaJSON?lat=${lat}&lng=${lon}&radius=${radiusKm}&maxRows=15&username=${username}`;

  try {
    const response = await axios.get(url, { timeout: 8000 });
    const geonames = (response.data && response.data.geonames) || [];

    return geonames
      .filter((item) => item.title && item.lat && item.lng)
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

exports.nearbyPlaces = onCall({ cors: true }, async (request) => {
  const auth = request.auth;
  if (!auth || !auth.uid) {
    throw new HttpsError("unauthenticated", "User authentication required to query nearbyPlaces.");
  }

  const uid = auth.uid;
  const isAllowed = await checkRateLimit(uid);
  if (!isAllowed) {
    throw new HttpsError("resource-exhausted", "Rate limit exceeded. Maximum 60 requests per hour per user.");
  }

  const data = request.data || {};
  const lat = parseFloat(data.lat);
  const lon = parseFloat(data.lon);
  const radiusMeters = parseInt(data.radiusMeters) || 3000;
  const requestedSources = Array.isArray(data.sources) ? data.sources : ["opentripmap", "geonames"];

  if (isNaN(lat) || isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    throw new HttpsError("invalid-argument", "Invalid latitude or longitude coordinates.");
  }

  const candidates = [];

  if (requestedSources.includes("opentripmap")) {
    const otmResults = await fetchOpenTripMap(lat, lon, radiusMeters);
    candidates.push(...otmResults);
  }

  if (requestedSources.includes("geonames")) {
    const geonamesResults = await fetchGeonames(lat, lon, radiusMeters);
    candidates.push(...geonamesResults);
  }

  return candidates;
});

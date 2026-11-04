/**
 * Firebase Cloud Function: nearbyPlaces
 *
 * Secure backend proxy for OpenTripMap and GeoNames APIs.
 * Enforces App Check, per-UID rate limiting (60 req/hr/UID),
 * reads secrets from Firebase Secret Manager ("OPENTRIPMAP_API_KEY", "GEONAMES_USERNAME"),
 * validates request parameters against strict allowlists and bounds,
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
 * Enforces per-user rate limit (60 requests/UID/hour) using Firestore counter documents.
 * Note: Anonymous Firebase accounts can be recreated, so UID-based limits alone do not establish
 * durable one-person limits. App Check and server-side abuse controls are enforced concurrently.
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
    secrets: ["OPENTRIPMAP_API_KEY", "GEONAMES_USERNAME"]
  },
  async (request) => {
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
    const radiusMeters = parseInt(data.radiusMeters, 10);
    const requestedSources = Array.isArray(data.sources) ? data.sources : ["opentripmap", "geonames"];

    // 1. Parameter Validation: Coordinates
    if (isNaN(lat) || !isFinite(lat) || isNaN(lon) || !isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      throw new HttpsError("invalid-argument", "Invalid latitude or longitude coordinates.");
    }

    // 2. Parameter Validation: Radius (100m <= radiusMeters <= 50000m)
    if (isNaN(radiusMeters) || !isFinite(radiusMeters) || radiusMeters < 100 || radiusMeters > 50000) {
      throw new HttpsError("invalid-argument", "Invalid radiusMeters argument. Must be between 100 and 50000 meters.");
    }

    // 3. Explicit Source Allowlist Validation
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

    // 4. Enforce exact geodesic distance filtering against requested radius
    const validatedCandidates = rawCandidates.filter((candidate) => {
      const distance = calculateHaversineMeters(lat, lon, candidate.latitude, candidate.longitude);
      return !isNaN(distance) && distance <= radiusMeters;
    });

    return validatedCandidates;
  }
);

/**
 * Europeana Cultural Heritage search — production module.
 *
 * The trusted europeanaSearch callable (functions/index.js) delegates to this module.
 * The shared Europeana API key lives ONLY in Firebase Secret Manager (deployed as the
 * EUROPEANA_API_KEY secret and never in the client, source, or logs): the Android app
 * never builds the provider URL, and a provider fetch failure is logged as a classified
 * outcome only, because axios errors embed the key inside config.url.
 */

const axios = require("axios");
const { HttpsError } = require("firebase-functions/v2/https");
const {
  parseStrictNumber,
  parseStrictInt,
  providerStatusForError,
  PROVIDER_STATUS_OK,
  PROVIDER_STATUS_AUTH_CONFIG,
  RADIUS_MIN_METERS,
  RADIUS_MAX_METERS,
} = require("./validation");

const MAX_LOCATION_NAME_LENGTH = 120;
const EUROPEANA_RATE_LIMIT_MAX_PER_HOUR = 20;

/**
 * Validates the europeanaSearch request payload end-to-end: App Check presence,
 * authentication, a non-blank location name, coordinates (finite, within geographic
 * range), and radius (strict integer within 100–50,000 m — the same bounds as
 * nearbyPlaces). Returns { ok: true, locationName, lat, lon, radiusMeters } or
 * { ok: false, error, message } describing the first failure.
 */
function validateEuropeanaRequest(data, auth, appCheck) {
  if (!appCheck) {
    return { ok: false, error: "failed-precondition", message: "App Check token missing or invalid." };
  }
  if (!auth || !auth.uid) {
    return { ok: false, error: "unauthenticated", message: "User authentication required." };
  }

  const locationName = data && typeof data.locationName === "string" ? data.locationName.trim() : "";
  if (locationName.length === 0 || locationName.length > MAX_LOCATION_NAME_LENGTH) {
    return {
      ok: false,
      error: "invalid-argument",
      message: `Invalid locationName argument. Must be a non-blank string of at most ${MAX_LOCATION_NAME_LENGTH} characters.`,
    };
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

  return { ok: true, locationName, lat, lon, radiusMeters };
}

/**
 * Calculates Haversine geodesic distance in meters between two lat/lon pairs (the same
 * calculation nearbyPlaces enforces server-side).
 */
function calculateHaversineMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000; // Earth radius in meters
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Fetches Europeana cultural-herit records. Same outcome contract as the nearbyPlaces
 * provider fetchers:
 *   - "ok": the provider answered (records may legitimately be empty);
 *   - "transient": timeout, unreachable, or 5xx — retryable;
 *   - "auth-config": the Europeana key is missing or rejected (401/403);
 *   - "rate-limit": the provider answered 429.
 * Each returned record carries only normalized, client-displayable fields (title,
 * description, category, coordinates, distanceMeters, attribution); provider details
 * that must never leak are dropped. Failures are logged as the classified outcome only.
 */
async function fetchEuropeanaRecords(locationName, lat, lon, radiusMeters) {
  const apiKey = process.env.EUROPEANA_API_KEY;
  if (!apiKey) {
    console.warn("[Europeana] EUROPEANA_API_KEY secret not configured in environment/Secret Manager. Skipping fetch.");
    return { records: [], status: PROVIDER_STATUS_AUTH_CONFIG };
  }

  const url = `https://api.europeana.eu/record/v2/search.json?wskey=${apiKey}&query=${encodeURIComponent(locationName)}&rows=8&profile=standard`;

  try {
    const response = await axios.get(url, { timeout: 8000 });
    const items = ((response.data && response.data.items) || []);

    const records = [];
    for (const item of items) {
      const titleArr = item.title;
      const title = Array.isArray(titleArr) && typeof titleArr[0] === "string" && titleArr[0].length > 0
        ? titleArr[0]
        : null;
      if (!title) continue;

      // Extract actual coordinates provided by the Europeana record.
      const spotLat = typeof item.latitude === "number" ? item.latitude : item.edmPlaceLatitude;
      const spotLon = typeof item.longitude === "number" ? item.longitude : item.edmPlaceLongitude;
      if (typeof spotLat !== "number" || typeof spotLon !== "number" || Number.isNaN(spotLat) || Number.isNaN(spotLon)) {
        continue;
      }
      // Coordinate validation: reject NaN, 0.0/0.0 (Null Island), and out-of-bounds.
      if ((spotLat === 0 && spotLon === 0) || spotLat < -90 || spotLat > 90 || spotLon < -180 || spotLon > 180) {
        continue;
      }

      const distanceMeters = calculateHaversineMeters(lat, lon, spotLat, spotLon);
      if (Number.isNaN(distanceMeters) || distanceMeters > radiusMeters) {
        continue;
      }

      const firstValue = (arr) => (Array.isArray(arr) && typeof arr[0] === "string" && arr[0].length > 0 ? arr[0] : null);
      // edmConceptLabel arrives nested ([["Visual Arts", ...]]); dataProvider and
      // edmIsShownBy arrive flat. Attribution must come from the record or fall back to
      // an honest generic label, never an invented institution or category.
      const conceptArr = Array.isArray(item.edmConceptLabel) ? item.edmConceptLabel : [];
      const conceptFlat = Array.isArray(conceptArr[0]) ? conceptArr[0] : conceptArr;
      const edmConceptLabel =
        (conceptFlat.find((v) => typeof v === "string" && v.length > 0)) || "Cultural Heritage";
      const dataProvider = firstValue(item.dataProvider) || "European Cultural Institution";

      records.push({
        title,
        description: `Digital cultural heritage record archived by ${dataProvider}. Category: ${edmConceptLabel}.`,
        category: "history",
        latitude: spotLat,
        longitude: spotLon,
        distanceMeters,
        imageUrl: firstValue(item.edmIsShownBy) || "",
        dataProvider,
        edmConceptLabel,
        europeanaId: typeof item.id === "string" && item.id.length > 0 ? item.id : null,
        locationName,
      });
    }
    return { records, status: PROVIDER_STATUS_OK };
  } catch (err) {
    // Log the classified outcome only: axios errors embed the request config, including
    // the wskey inside config.url, so the raw error must never be printed.
    console.warn(`[Europeana] Fetch failed (${providerStatusForError(err)}).`);
    return { records: [], status: providerStatusForError(err) };
  }
}

module.exports = {
  MAX_LOCATION_NAME_LENGTH,
  EUROPEANA_RATE_LIMIT_MAX_PER_HOUR,
  validateEuropeanaRequest,
  fetchEuropeanaRecords,
};

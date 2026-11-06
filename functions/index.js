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
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const axios = require("axios");
const { validateSharedTripPublishPayload, publishSharedTripDocument } = require("./sharedTrips");
const { finalizePhotoUploadDocument, PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR, reapUnregisteredPhotoObjects } = require("./photos");
const { validateNearbyPlacesRequest } = require("./validation");
const { firestoreDatabaseId } = require("./firestoreDatabase");

const app = initializeApp();
const db = getFirestore(app, firestoreDatabaseId());

const RATE_LIMIT_MAX_PER_HOUR = 60;
const RATE_LIMIT_WINDOW_MS = 3600 * 1000; // 1 hour
const SHARED_TRIP_RATE_LIMIT_MAX_PER_HOUR = 10;

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
 */
async function checkRateLimit(uid, counterKey = `nearby_places_${uid}`, maxPerHour = RATE_LIMIT_MAX_PER_HOUR) {
  const now = Date.now();
  const rateLimitRef = db.collection("rate_limits").doc(counterKey);

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

    if (currentCount >= maxPerHour) {
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

    // 3. Strict end-to-end request validation: App Check, auth, coordinates,
    //    integer radius bounds and the source allowlist (shared with tests via
    //    functions/validation.js — never duplicated).
    const validated = validateNearbyPlacesRequest(data, request.auth, request.app);
    if (!validated.ok) {
      throw new HttpsError(validated.error, validated.message);
    }
    const { lat, lon, radiusMeters, requestedSources } = validated;

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

/**
 * Firebase Cloud Function: publishSharedTrip
 *
 * The only trusted writer of shared_trips documents. Firestore Rules deny direct
 * client creation (the rules language cannot validate list contents), so a shared
 * trip is created here after full server-side schema validation.
 *
 * Server-authoritative persistence (see functions/sharedTrips.js): the document id is
 * an unpredictable server-generated auto-id, createdAt uses the server's clock, and
 * expireAt is exactly 30 days later — the client cannot influence any of them. The
 * trip document is created with create()-only semantics, never an unconditional
 * overwrite, and a client-supplied requestId makes retries idempotent (a replay
 * returns the already-published trip instead of publishing a second one).
 * Enforces authentication, App Check, and a per-UID publishing rate limit
 * (consumed only by genuinely new publishes).
 */
exports.publishSharedTrip = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    // Firebase Authentication Check
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to publish a shared trip.");
    }

    // Server-owned publishing: validation, id generation, timestamps, atomic create,
    // idempotency replay, and the per-UID rate limit (new publishes only).
    return publishSharedTripDocument(db, auth.uid, request.data || {}, {
      shouldRateLimit: () =>
        checkRateLimit(auth.uid, `shared_trips_publish_${auth.uid}`, SHARED_TRIP_RATE_LIMIT_MAX_PER_HOUR)
    });
  }
);

/**
 * Firebase Cloud Function: finalizePhotoUpload
 *
 * The only trusted writer of spot_photos metadata documents. Firestore Rules deny
 * direct client creation, so a photo becomes visible only after its Storage object has
 * been verified server-side: the object must exist in the project's bucket under the
 * authenticated caller's own ownership-aware path spot_photos/{uid}/{spotId}/{filename},
 * stay within the size limit, and be a permitted image type. The Firestore document id
 * is generated server-side; the metadata references the verified object only (an
 * arbitrary external URL is rejected). Replays of the same object are idempotent.
 * Enforces authentication, App Check, and a per-UID registration rate limit
 * (consumed only by genuinely new registrations).
 */
exports.finalizePhotoUpload = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to finalize a photo upload.");
    }

    return finalizePhotoUploadDocument(db, getStorage().bucket(), auth.uid, request.data || {}, {
      shouldRateLimit: () =>
        checkRateLimit(auth.uid, `photo_finalize_${auth.uid}`, PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR)
    });
  }
);

/**
 * Firebase Cloud Function: reapOrphanedPhotoUploads (scheduled, hourly)
 *
 * Server-side cleanup of Storage objects under spot_photos/ whose upload never reached
 * a registered photo document — a client crash or lost connection between the Storage
 * upload and the finalizePhotoUpload call otherwise leaves the object behind forever.
 * Safety: candidates must be older than 24 hours (in-flight retries are never touched),
 * follow the ownership-aware path structure with matching ownerUid metadata, and have
 * no spot_photos Firestore record referencing them (registered photos are never
 * deleted). Deletion failures are logged and retried by the next run; the operation is
 * idempotent and bounded (at most 200 objects per invocation).
 */
exports.reapOrphanedPhotoUploads = onSchedule("every 60 minutes", async () => {
  const result = await reapUnregisteredPhotoObjects(db, getStorage().bucket());
  if (result.processed > 0) {
    console.log(
      `[reapOrphanedPhotoUploads] processed=${result.processed} deleted=${result.deleted} kept=${result.kept}`
    );
  }
});

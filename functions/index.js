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
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const axios = require("axios");
const { validateSharedTripPublishPayload, publishSharedTripDocument } = require("./sharedTrips");
const { finalizePhotoUploadDocument, PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR, reapUnregisteredPhotoObjects, moderateReportedPhotos } = require("./photos");
const { validateNearbyPlacesRequest } = require("./validation");
const { firestoreDatabaseId } = require("./firestoreDatabase");
const { decideRateLimitInTransaction, RATE_LIMIT_WINDOW_MS } = require("./rateLimits");

const app = initializeApp();
const db = getFirestore(app, firestoreDatabaseId());

const RATE_LIMIT_MAX_PER_HOUR = 60;
const SHARED_TRIP_RATE_LIMIT_MAX_PER_HOUR = 10;

// Provider outcome contract (see functions/validation.js): a successful response —
// including a legitimate empty one — is "ok"; anything else names the failure kind so
// the app never mistakes a provider outage for "no places around here".
const {
  PROVIDER_STATUS_OK: PROVIDER_OK,
  PROVIDER_STATUS_TRANSIENT: PROVIDER_TRANSIENT,
  PROVIDER_STATUS_AUTH_CONFIG: PROVIDER_AUTH_CONFIG,
  PROVIDER_STATUS_RATE_LIMIT: PROVIDER_RATE_LIMIT,
  providerStatusForError
} = require("./validation");

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
 * The decision and increment run inside one transaction, so a slot is consumed exactly
 * when the caller's operation is counted.
 */
async function checkRateLimit(uid, counterKey = `nearby_places_${uid}`, maxPerHour = RATE_LIMIT_MAX_PER_HOUR) {
  const rateLimitRef = db.collection("rate_limits").doc(counterKey);
  return db.runTransaction((transaction) =>
    decideRateLimitInTransaction(transaction, rateLimitRef, maxPerHour, Date.now())
  );
}

/**
 * Fetches OpenTripMap candidates.
 *
 * Returns { records, status } where status is exactly one of:
 *   - "ok": the provider answered (records may legitimately be empty — a successful
 *     empty response is a real coverage signal, not a failure);
 *   - "transient": the provider timed out, was unreachable, or answered 5xx — retryable;
 *   - "auth-config": the provider credentials are missing or rejected (401/403) — not
 *     retryable by re-calling, the deployment must be fixed;
 *   - "rate-limit": the provider answered 429.
 * Failures must never be flattened into an empty "ok" result: an empty result proves
 * "no places here" only when the provider actually answered.
 */
async function fetchOpenTripMap(lat, lon, radiusMeters) {
  const apiKey = process.env.OPENTRIPMAP_API_KEY;
  if (!apiKey) {
    console.warn("[OpenTripMap] OPENTRIPMAP_API_KEY secret not configured in environment/Secret Manager. Skipping fetch.");
    return { records: [], status: PROVIDER_AUTH_CONFIG };
  }

  const url = `https://api.opentripmap.com/0.1/en/places/radius?radius=${radiusMeters}&lon=${lon}&lat=${lat}&kinds=historic,cultural,architecture,natural,tourist_object&rate=2&format=json&limit=15&apikey=${apiKey}`;

  try {
    const response = await axios.get(url, { timeout: 8000 });
    const items = response.data || [];

    const records = items
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
    return { records, status: PROVIDER_OK };
  } catch (err) {
    console.warn(`[OpenTripMap] Fetch failed: ${err.message}`);
    return { records: [], status: providerStatusForError(err) };
  }
}

/**
 * Fetches GeoNames candidates. Same outcome contract as [fetchOpenTripMap]: a provider
 * failure is reported as such (transient / auth-config / rate-limit), never hidden as
 * an empty success.
 */
async function fetchGeonames(lat, lon, radiusMeters) {
  const username = process.env.GEONAMES_USERNAME;
  if (!username) {
    console.warn("[GeoNames] GEONAMES_USERNAME secret not configured in environment/Secret Manager. Skipping fetch.");
    return { records: [], status: PROVIDER_AUTH_CONFIG };
  }

  const radiusKm = Math.min(Math.max(radiusMeters / 1000, 1), 30);
  const url = `https://secure.geonames.org/findNearbyWikipediaJSON?lat=${lat}&lng=${lon}&radius=${radiusKm}&maxRows=15&username=${username}`;

  try {
    const response = await axios.get(url, { timeout: 8000 });
    const geonames = (response.data && response.data.geonames) || [];

    const records = geonames
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
    return { records, status: PROVIDER_OK };
  } catch (err) {
    console.warn(`[GeoNames] Fetch failed: ${err.message}`);
    return { records: [], status: providerStatusForError(err) };
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

    // 4. Fetch each requested provider, isolating its outcome: one provider's failure
    //    never discards another provider's results. The per-provider status is reported
    //    alongside the merged places so the app can distinguish a genuine "no places
    //    here" answer from a provider outage — an empty successful response is valid
    //    coverage data, a failed one is not.
    const rawCandidates = [];
    const providerStatus = {};

    if (requestedSources.includes("opentripmap")) {
      const otm = await fetchOpenTripMap(lat, lon, radiusMeters);
      rawCandidates.push(...otm.records);
      providerStatus["opentripmap"] = otm.status;
    }

    if (requestedSources.includes("geonames")) {
      const geonames = await fetchGeonames(lat, lon, radiusMeters);
      rawCandidates.push(...geonames.records);
      providerStatus["geonames"] = geonames.status;
    }

    // 5. Enforce exact geodesic distance filtering against requested radius
    const validatedCandidates = rawCandidates.filter((candidate) => {
      const distance = calculateHaversineMeters(lat, lon, candidate.latitude, candidate.longitude);
      return !isNaN(distance) && distance <= radiusMeters;
    });

    return { places: validatedCandidates, providerStatus };
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
 * expireAt is exactly 30 days later — the client cannot influence any of them.
 * Validation happens first; then ONE Firestore transaction atomically reads the
 * idempotency marker (replaying the already-published trip for the same owner,
 * rejecting reuse by another owner), reads and increments the per-UID rate-limit
 * counter, and creates exactly one trip document together with its idempotency record.
 * A slot is consumed exactly when the publish commits; concurrent duplicates of the
 * same requestId all resolve to the single published trip, with no orphaned documents.
 * Enforces authentication, App Check, and the per-UID publishing rate limit
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

    // Server-owned publishing: validation, id generation, timestamps, and the atomic
    // create of exactly one trip document + its idempotency record + the per-UID rate
    // limit (new publishes only) — all committed in a single Firestore transaction.
    return publishSharedTripDocument(db, auth.uid, request.data || {}, {
      rateLimit: {
        ref: db.collection("rate_limits").doc(`shared_trips_publish_${auth.uid}`),
        maxPerHour: SHARED_TRIP_RATE_LIMIT_MAX_PER_HOUR,
      },
    });
  }
);

/**
 * Firebase Cloud Function: finalizePhotoUpload
 *
 * The only trusted writer of spot_photos metadata documents. Firestore Rules deny
 * direct client creation, so a photo becomes visible only after its Storage object has
 * been verified server-side: the object must exist in the project's bucket under the
 * authenticated caller's own ownership-aware path spot_photos/{uid}/{spotId}/{filename}
 * whose ownerUid/spotId custom metadata agrees with that path, and — above all — its
 * ACTUAL BYTES must be genuine JPEG or PNG image data within the decompression-bomb
 * safety bounds; the declared content type is not trusted (see
 * validateAndNormalizeImageObject). Accepted objects are re-encoded in place into the
 * canonical published JPEG with all metadata (including EXIF/GPS) stripped. The
 * Firestore document id is server-derived — the SHA-256 digest of the canonical object
 * path — and the registration itself is one Firestore transaction that atomically reads
 * the existing registration (replaying it for the same owner, rejecting a conflicting
 * record) and commits the photo document together with the rate-limit increment, so
 * concurrent finalizations of the same object can never create duplicate or partial
 * metadata. Replays of the same object are idempotent. Enforces authentication, App
 * Check, and a per-UID registration rate limit (consumed only by genuinely new
 * registrations).
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
      rateLimit: {
        ref: db.collection("rate_limits").doc(`photo_finalize_${auth.uid}`),
        maxPerHour: PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR,
      },
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
 * follow the ownership-aware path structure with an ownerUid metadata that EXACTLY
 * matches the path owner, and be claimed for deletion inside a Firestore transaction
 * that re-verifies the registration is missing — concurrent finalization locks on the
 * same claim, so the reaper never deletes a photo that is being registered. The listing
 * cursor is persisted between runs, so repeated hourly invocations advance through the
 * whole listing instead of restarting at a head full of retained objects. Deletion
 * failures leave a claim and are retried on later runs; the operation is idempotent and
 * bounded (at most 200 listing objects plus 50 claim retries per invocation).
 */
exports.reapOrphanedPhotoUploads = onSchedule("every 60 minutes", async () => {
  const result = await reapUnregisteredPhotoObjects(db, getStorage().bucket());
  console.log(
    `[reapOrphanedPhotoUploads] processed=${result.processed} deleted=${result.deleted} ` +
      `kept=${result.kept} failed=${result.failed} retried=${result.retried}`
  );
});

/**
 * Firebase Cloud Function: moderateReportedPhotos (scheduled, every 15 minutes)
 *
 * Authoritative moderation of community photos. The 3-report threshold only hides a
 * photo in the app UI; anyone who already holds the object's public download URL could
 * still fetch the image. This sweep deletes the Storage object (revoking the download
 * URL with it) and marks the Firestore record "removed", making a moderated photo
 * genuinely inaccessible. Objects already gone (a concurrent reaper) are tolerated; a
 * failed delete leaves the record unmarked so the next sweep retries. Idempotent:
 * already-removed records are skipped.
 */
exports.moderateReportedPhotos = onSchedule("every 15 minutes", async () => {
  const result = await moderateReportedPhotos(db, getStorage().bucket(), { nowMs: () => Date.now() });
  if (result.processed > 0) {
    console.log(
      `[moderateReportedPhotos] processed=${result.processed} removed=${result.removed} kept=${result.kept} failed=${result.failed}`
    );
  }
});

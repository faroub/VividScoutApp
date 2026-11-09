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
const { getFirestore } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");
const axios = require("axios");
const { validateSharedTripPublishPayload, publishSharedTripDocument } = require("./sharedTrips");
const {
  finalizePhotoUploadDocument,
  reportPhotoDocument,
  PHOTO_FINALIZE_RATE_LIMIT_MAX_PER_HOUR,
  PHOTO_REPORT_RATE_LIMIT_MAX_PER_HOUR,
  reapUnregisteredPhotoObjects,
  moderateReportedPhotos
} = require("./photos");
const { validateNearbyPlacesRequest } = require("./validation");
const { validateEuropeanaRequest, fetchEuropeanaRecords, EUROPEANA_RATE_LIMIT_MAX_PER_HOUR } = require("./europeana");
const { firestoreDatabaseId } = require("./firestoreDatabase");
const { decideRateLimitInTransaction, RATE_LIMIT_WINDOW_MS } = require("./rateLimits");
const {
  postCommunityTipDocument,
  submitCommunitySpotDocument,
  castTipVoteDocument,
  reportCommunityTipDocument,
  reportCommunitySubmissionDocument,
  moderateReportedCommunityContent,
  TIP_RATE_LIMIT_MAX_PER_DAY,
  SUBMISSION_RATE_LIMIT_MAX_PER_DAY,
  COMMUNITY_RATE_LIMIT_WINDOW_MS,
  TIP_VOTE_RATE_LIMIT_MAX_PER_HOUR,
  COMMUNITY_REPORT_RATE_LIMIT_MAX_PER_HOUR,
} = require("./community");

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
    // Log the classified outcome only: axios errors embed the request config,
    // including the API key inside config.url, so the raw error must never be
    // printed (see the log-hygiene tests in nearbyPlacesCallable.test.js).
    console.warn(`[OpenTripMap] Fetch failed (${providerStatusForError(err)}).`);
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
    // Same sanitization as fetchOpenTripMap: never print the raw axios error, whose
    // config carries the provider username in the URL.
    console.warn(`[GeoNames] Fetch failed (${providerStatusForError(err)}).`);
    return { records: [], status: providerStatusForError(err) };
  }
}

// Exported for the log-hygiene tests in nearbyPlacesCallable.test.js: the functions
// themselves are unchanged.
exports.fetchOpenTripMap = fetchOpenTripMap;
exports.fetchGeonames = fetchGeonames;

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

    // 2. Strict end-to-end request validation: App Check, auth, coordinates,
    //    integer radius bounds and the source allowlist (shared with tests via
    //    functions/validation.js — never duplicated). Runs BEFORE the rate limit so a
    //    malformed request cannot consume the caller's hourly quota.
    const uid = auth.uid;
    const data = request.data || {};
    const validated = validateNearbyPlacesRequest(data, request.auth, request.app);
    if (!validated.ok) {
      throw new HttpsError(validated.error, validated.message);
    }
    const { lat, lon, radiusMeters, requestedSources } = validated;

    // 3. Per-UID Rate Limit Check (60 requests/UID/hour): a slot is consumed exactly
    //    when a request that passed validation is counted.
    const isAllowed = await checkRateLimit(uid);
    if (!isAllowed) {
      throw new HttpsError("resource-exhausted", "Rate limit exceeded. Maximum 60 requests per hour per user.");
    }

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
 * Firebase Cloud Function: europeanaSearch
 *
 * Secure backend proxy for the Europeana Cultural Heritage API. The Europeana API key
 * (EUROPEANA_API_KEY) lives only in Firebase Secret Manager — it is never shipped in
 * the app, and the client never builds the provider URL, so it can never leak from a
 * device. Enforces authentication and App Check, validates the request strictly
 * (location name, coordinates, radius) BEFORE the per-UID rate limit so a malformed
 * request cannot consume the caller's quota, and reports the provider outcome
 * alongside the heritage items so the app distinguishes a genuine "no heritage items
 * here" from a provider outage. See functions/europeana.js.
 */
exports.europeanaSearch = onCall(
  {
    cors: true,
    enforceAppCheck: true,
    secrets: ["EUROPEANA_API_KEY"]
  },
  async (request) => {
    // 1. Firebase Authentication Check
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to query europeanaSearch.");
    }

    // 2. Strict end-to-end request validation: App Check, auth, location name,
    //    coordinates and radius bounds. Runs BEFORE the rate limit so a malformed
    //    request cannot consume the caller's hourly quota.
    const validated = validateEuropeanaRequest(request.data || {}, request.auth, request.app);
    if (!validated.ok) {
      throw new HttpsError(validated.error, validated.message);
    }
    const { locationName, lat, lon, radiusMeters } = validated;

    // 3. Per-UID rate limit (20 searches/UID/hour).
    const isAllowed = await checkRateLimit(auth.uid, `europeana_search_${auth.uid}`, EUROPEANA_RATE_LIMIT_MAX_PER_HOUR);
    if (!isAllowed) {
      throw new HttpsError(
        "resource-exhausted",
        `Rate limit exceeded. Maximum ${EUROPEANA_RATE_LIMIT_MAX_PER_HOUR} Europeana searches per hour per user.`
      );
    }

    // 4. Fetch through the trusted boundary: the provider call, the coordinate
    //    verification, and the geodesic radius filtering all run server-side.
    const { records, status } = await fetchEuropeanaRecords(locationName, lat, lon, radiusMeters);
    return { items: records, providerStatus: { europeana: status } };
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
 * Firebase Cloud Function: postCommunityTip
 *
 * The only trusted writer of community_tips documents. Firestore Rules deny direct
 * client creation, so a tip becomes public only after strict server-side validation
 * (the exact field contract of firestore.rules, enforced here because the rules
 * language is the schema boundary of last resort). The caller's UID never reaches the
 * public document: ownership is recorded in the server-written ownership index, which
 * the rules read to enforce owner-only edits and to block self-upvotes
 * (see functions/ownershipIndex.js). Posts are idempotent — a retry of the same tip id
 * replays and refreshes the verified owner's document — and the per-UID limit
 * (20 tips per rolling 24 hours) is consumed inside the same transaction as the create,
 * so it is consumed only by genuinely new tips. Enforces authentication and App Check.
 */
exports.postCommunityTip = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to post a community tip.");
    }

    return postCommunityTipDocument(db, auth.uid, request.data || {}, {
      rateLimit: {
        ref: db.collection("rate_limits").doc(`community_tips_post_${auth.uid}`),
        maxPerWindow: TIP_RATE_LIMIT_MAX_PER_DAY,
        windowMs: COMMUNITY_RATE_LIMIT_WINDOW_MS,
      },
    });
  }
);

/**
 * Firebase Cloud Function: submitCommunitySpot
 *
 * The only trusted writer of community_submissions documents. Same guarantees as
 * postCommunityTip for scout-spot submissions: strict server-side validation (including
 * the photoUri restriction to the caller's own Storage paths), no ownerUid in the public
 * document, ownership through the index, idempotent replays of the same submission id,
 * and the per-UID limit (10 submissions per rolling 24 hours) consumed atomically with a
 * genuinely new submission only. Enforces authentication and App Check.
 */
exports.submitCommunitySpot = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to submit a community spot.");
    }

    return submitCommunitySpotDocument(db, auth.uid, request.data || {}, {
      rateLimit: {
        ref: db.collection("rate_limits").doc(`community_submissions_post_${auth.uid}`),
        maxPerWindow: SUBMISSION_RATE_LIMIT_MAX_PER_DAY,
        windowMs: COMMUNITY_RATE_LIMIT_WINDOW_MS,
      },
    });
  }
);

/**
 * Firebase Cloud Function: reportPhoto
 *
 * The trusted writer of photo reports (spot_photos/{photoId}/reports/{reporterUid}).
 * Reports must come from a signed-in, NON-ANONYMOUS account: anonymous sign-in is free
 * and unverified, so allowing it would let one person mint UIDs until a photo trips the
 * moderation threshold. Inside one Firestore transaction the callable re-checks the
 * photo exists and is not already hidden/removed, rejects duplicate reports by the same
 * account, consumes a per-UID rate limit (20/hour), and creates the report document
 * together with the exact +1 reportsCount increment — the same atomic shape the rules'
 * direct-write path enforces while older app versions are in the field. Enforces
 * authentication and App Check. See reportPhotoDocument (functions/photos.js).
 */
exports.reportPhoto = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to report a photo.");
    }
    // A real (non-anonymous) sign-in provider: flag weight must not be farmable.
    const signInProvider = auth.token && auth.token.firebase && auth.token.firebase.sign_in_provider;
    if (signInProvider === "anonymous") {
      throw new HttpsError(
        "unauthenticated",
        "Sign in with a real account to report photos."
      );
    }

    return reportPhotoDocument(db, auth.uid, request.data || {}, {
      rateLimit: {
        ref: db.collection("rate_limits").doc(`photo_report_${auth.uid}`),
        maxPerHour: PHOTO_REPORT_RATE_LIMIT_MAX_PER_HOUR,
      },
    });
  }
);

/**
 * Firebase Cloud Function: castTipVote
 *
 * The trusted writer of tip votes (community_tips/{tipId}/votes/{voterUid}). Inside one
 * Firestore transaction it re-checks the tip exists, refuses the tip owner's own vote,
 * rejects a second vote by the same account, consumes a per-UID vote rate limit
 * (100/hour), and creates the vote document together with the exact +1 upvotes
 * increment — the same atomic shape the rules' direct-write path enforces while older
 * app versions are in the field. Enforces authentication and App Check. See
 * castTipVoteDocument (functions/community.js).
 */
exports.castTipVote = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to vote on a tip.");
    }

    return castTipVoteDocument(db, auth.uid, request.data || {}, {
      rateLimit: {
        ref: db.collection("rate_limits").doc(`community_tips_vote_${auth.uid}`),
        maxPerWindow: TIP_VOTE_RATE_LIMIT_MAX_PER_HOUR,
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
 * Soft-moderation sweep: a photo whose reports reach the threshold is HIDDEN
 * (moderationState "hidden") so it leaves the app immediately, but its Storage object
 * is deliberately kept — a report count alone never makes content permanently
 * unreachable. Irreversible removal stays a separate, reviewed step. Records already
 * hidden or removed are skipped (idempotent); a failed hide mark is retried by the
 * next sweep.
 */
exports.moderateReportedPhotos = onSchedule("every 15 minutes", async () => {
  const result = await moderateReportedPhotos(db, getStorage().bucket(), { nowMs: () => Date.now() });
  if (result.processed > 0) {
    console.log(
      `[moderateReportedPhotos] processed=${result.processed} hidden=${result.hidden} kept=${result.kept} failed=${result.failed}`
    );
  }
});

/**
 * Firebase Cloud Function: reportCommunityTip
 *
 * The trusted writer of community tip reports
 * (community_tips/{tipId}/reports/{reporterUid}). Reports must come from a signed-in,
 * NON-ANONYMOUS account: anonymous sign-in is free and unverified, so allowing it would
 * let one person mint UIDs until the moderation threshold trips. Inside one Firestore
 * transaction the callable re-checks the tip exists and is not already hidden/removed,
 * refuses the tip owner's own report, rejects duplicate reports by the same account,
 * consumes a per-UID rate limit (20/hour), and creates the report document together
 * with the exact +1 reportsCount increment — the same atomic shape the rules'
 * direct-write path enforces while older app versions are in the field. Enforces
 * authentication and App Check. See reportCommunityTipDocument
 * (functions/community.js) and docs/COMMUNITY_REPORTING_DESIGN.md.
 */
exports.reportCommunityTip = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to report a community tip.");
    }
    // A real (non-anonymous) sign-in provider: flag weight must not be farmable.
    const signInProvider = auth.token && auth.token.firebase && auth.token.firebase.sign_in_provider;
    if (signInProvider === "anonymous") {
      throw new HttpsError(
        "unauthenticated",
        "Sign in with a real account to report community content."
      );
    }

    return reportCommunityTipDocument(db, auth.uid, request.data || {}, {
      rateLimit: {
        ref: db.collection("rate_limits").doc(`community_tip_report_${auth.uid}`),
        maxPerWindow: COMMUNITY_REPORT_RATE_LIMIT_MAX_PER_HOUR,
      },
    });
  }
);

/**
 * Firebase Cloud Function: reportCommunitySubmission
 *
 * The trusted writer of community submission reports
 * (community_submissions/{submissionId}/reports/{reporterUid}), with the identical
 * contract as reportCommunityTip. See reportCommunitySubmissionDocument
 * (functions/community.js) and docs/COMMUNITY_REPORTING_DESIGN.md.
 */
exports.reportCommunitySubmission = onCall(
  {
    cors: true,
    enforceAppCheck: true
  },
  async (request) => {
    const auth = request.auth;
    if (!auth || !auth.uid) {
      throw new HttpsError("unauthenticated", "User authentication required to report a community submission.");
    }
    const signInProvider = auth.token && auth.token.firebase && auth.token.firebase.sign_in_provider;
    if (signInProvider === "anonymous") {
      throw new HttpsError(
        "unauthenticated",
        "Sign in with a real account to report community content."
      );
    }

    return reportCommunitySubmissionDocument(db, auth.uid, request.data || {}, {
      rateLimit: {
        ref: db.collection("rate_limits").doc(`community_submission_report_${auth.uid}`),
        maxPerWindow: COMMUNITY_REPORT_RATE_LIMIT_MAX_PER_HOUR,
      },
    });
  }
);

/**
 * Firebase Cloud Function: moderateReportedCommunityContent (scheduled, every 15 minutes)
 *
 * Soft-moderation sweep for community text content, mirroring the photo sweep: a tip or
 * submission whose reports from distinct accounts reach the threshold is HIDDEN
 * (moderationState "hidden") so it leaves the app immediately, but the document, its
 * votes, and its reports are deliberately kept — a report count alone never makes
 * content permanently unreachable, and the author's content can be restored after a
 * mistaken or abusive flagging campaign. Irreversible removal stays a separate,
 * human-reviewed step. Documents already hidden or removed are skipped (idempotent); a
 * failed hide mark is retried by the next sweep. See moderateReportedCommunityContent
 * (functions/community.js).
 */
exports.moderateReportedCommunityContent = onSchedule("every 15 minutes", async () => {
  const result = await moderateReportedCommunityContent(db, { nowMs: () => Date.now() });
  if (result.processed > 0) {
    console.log(
      `[moderateReportedCommunityContent] processed=${result.processed} hidden=${result.hidden} kept=${result.kept} failed=${result.failed}`
    );
  }
});

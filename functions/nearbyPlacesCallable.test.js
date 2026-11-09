/**
 * Tests for the nearbyPlaces callable handler wiring (functions/index.js).
 *
 * These tests exercise the REAL exported handler (index.js is imported — never
 * reimplemented), calling its .run(request) entry point the same way the Cloud
 * Functions framework does. What is under test here is handler-level production
 * behavior that the validation module alone cannot prove: most importantly the
 * ORDER of validation and rate limiting — a malformed request must be rejected
 * before it consumes the caller's hourly quota.
 *
 * Rate limiting is backed by the real Firestore emulator (same harness as
 * community.test.js — the suite refuses to start without the emulator host set so it
 * can never touch a production project). No network provider is contacted: no API
 * key is configured, so both provider fetchers return the auth-config outcome
 * without an HTTP request. The log-hygiene tests temporarily replace axios.get with
 * a failure carrying the same config.url a real axios error embeds (including the
 * API key) to verify the sanitized failure log never prints it.
 */

const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("firebase-admin");
const axios = require("axios");

// index.js initializes the default admin app at require time; the emulator
// environment (checked below) keeps it local. Required before any admin call so the
// handler and this test share the same default app and Firestore instance.
process.env.GCLOUD_PROJECT = process.env.GCLOUD_PROJECT || "vividscout-rules-emulator";
const { nearbyPlaces, fetchOpenTripMap, fetchGeonames } = require("./index");

const uid = "nearby-places-ordering-uid";
const rateLimitDoc = () => admin.firestore().collection("rate_limits").doc(`nearby_places_${uid}`);

const VALID_APP_CHECK = { appId: "app_123" };
const VALID_AUTH = { uid };

let db = null;

before(() => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error(
      "Refusing to run: FIRESTORE_EMULATOR_HOST must be set. " +
        'Run via: firebase emulators:exec --only firestore,storage "npm --prefix functions test"'
    );
  }
  db = admin.firestore();
});

after(async () => {
  // Remove the quota counter so this file's slots do not leak into other suites
  // sharing the emulator instance.
  await rateLimitDoc().delete();
  if (admin.apps.length > 0) {
    await admin.app().delete();
  }
});

function callNearbyPlaces(data, auth = VALID_AUTH, app = VALID_APP_CHECK) {
  return nearbyPlaces.run({ auth, app, data });
}

describe("nearbyPlaces handler — validation and rate-limit ordering", () => {
  it("rejects an invalid request without consuming the rate-limit quota", async () => {
    // radiusMeters 50 is below the 100 minimum: strict validation fails.
    await assert.rejects(
      () => callNearbyPlaces({ lat: 38.7112, lon: -9.1423, radiusMeters: 50, sources: ["opentripmap"] }),
      (err) => {
        assert.ok(
          err.code === "invalid-argument" || err.message.includes("invalid-argument"),
          `expected invalid-argument, got: ${err.code || err.message}`
        );
        return true;
      }
    );

    const snapshot = await rateLimitDoc().get();
    assert.ok(!snapshot.exists, "a malformed request must not consume any hourly quota");
  });

  it("rejects an unauthenticated request before touching the rate limit", async () => {
    await assert.rejects(
      () => callNearbyPlaces({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000 }, null),
      (err) => {
        assert.ok(
          err.code === "unauthenticated" || err.message.includes("unauthenticated"),
          `expected unauthenticated, got: ${err.code || err.message}`
        );
        return true;
      }
    );

    const snapshot = await rateLimitDoc().get();
    assert.ok(!snapshot.exists, "an unauthenticated request must not consume any quota");
  });

  it("consumes exactly one quota slot for a request that passes validation", async () => {
    // No provider key is configured in the emulator, so both fetchers return the
    // auth-config outcome WITHOUT any network request; the result contract keeps
    // that failure visible to the client instead of an empty "ok".
    const result = await callNearbyPlaces({ lat: 38.7112, lon: -9.1423, radiusMeters: 3000 });

    assert.deepStrictEqual(result.places, []);
    assert.strictEqual(result.providerStatus.opentripmap, "auth-config");
    assert.strictEqual(result.providerStatus.geonames, "auth-config");

    const snapshot = await rateLimitDoc().get();
    assert.ok(snapshot.exists, "a valid request must consume one rate-limit slot");
    assert.strictEqual(snapshot.data().count, 1);
  });
});

describe("provider fetch failure logs never leak the API key", () => {
  // axios errors carry the full request config — including the API key embedded in
  // config.url. The fetch failure logs must print only the classified outcome.
  const captured = [];
  let originalWarn = null;
  let originalGet = null;

  before(() => {
    originalWarn = console.warn;
    originalGet = axios.get;
  });

  after(() => {
    console.warn = originalWarn;
    axios.get = originalGet;
    delete process.env.OPENTRIPMAP_API_KEY;
    delete process.env.GEONAMES_USERNAME;
  });

  function stubFailingAxios() {
    axios.get = async (url) => {
      const err = new Error("Request failed with status code 401");
      // Mirrors the shape of a genuine axios error.
      err.config = { url, method: "get" };
      err.response = { status: 401 };
      throw err;
    };
  }

  it("OpenTripMap failure log excludes the key from the URL and the error message", async () => {
    process.env.OPENTRIPMAP_API_KEY = "leak-canary-otm-key";
    stubFailingAxios();
    captured.length = 0;
    console.warn = (...args) => captured.push(args.join(" "));

    const outcome = await fetchOpenTripMap(38.7112, -9.1423, 1500);

    assert.strictEqual(outcome.status, "auth-config");
    assert.strictEqual(outcome.records.length, 0);
    assert.ok(captured.some((line) => line.includes("[OpenTripMap]")), "the classified failure must be logged");
    for (const line of captured) {
      assert.ok(!line.includes("apikey="), `log line must not contain the API key query: ${line}`);
      assert.ok(!line.includes("leak-canary-otm-key"), `log line must not contain the API key value: ${line}`);
      assert.ok(!line.includes("Request failed"), `log line must not quote the raw error message: ${line}`);
    }
  });

  it("GeoNames failure log excludes the username from the URL and the error message", async () => {
    process.env.GEONAMES_USERNAME = "leak-canary-gn-user";
    stubFailingAxios();
    captured.length = 0;
    console.warn = (...args) => captured.push(args.join(" "));

    const outcome = await fetchGeonames(38.7112, -9.1423, 1500);

    assert.strictEqual(outcome.status, "auth-config");
    assert.strictEqual(outcome.records.length, 0);
    assert.ok(captured.some((line) => line.includes("[GeoNames]")), "the classified failure must be logged");
    for (const line of captured) {
      assert.ok(!line.includes("username="), `log line must not contain the username query: ${line}`);
      assert.ok(!line.includes("leak-canary-gn-user"), `log line must not contain the username value: ${line}`);
      assert.ok(!line.includes("Request failed"), `log line must not quote the raw error message: ${line}`);
    }
  });
});

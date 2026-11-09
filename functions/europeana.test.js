/**
 * Tests for the Europeana Cultural Heritage trusted boundary (functions/europeana.js).
 *
 * These tests import the REAL production module: payload validation and provider-fetch
 * normalization (coordinate verification, radius filtering, attribution, outcome
 * classification, log hygiene) are exercised directly. No network request is made —
 * axios.get is temporarily replaced by test doubles exactly the way
 * nearbyPlacesCallable.test.js does, and no Firestore is touched, so this file needs no
 * emulator (it runs inside the same suite regardless).
 */

const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const axios = require("axios");
const {
  validateEuropeanaRequest,
  fetchEuropeanaRecords,
  MAX_LOCATION_NAME_LENGTH,
} = require("./europeana");

const VALID_AUTH = { uid: "culture-uid" };
const VALID_APP_CHECK = { appId: "app_123" };

const validRequest = (overrides = {}) => ({
  locationName: "Lisbon",
  lat: 38.7112,
  lon: -9.1423,
  radiusMeters: 15000,
  ...overrides,
});

describe("validateEuropeanaRequest", () => {
  it("accepts a well-formed request and trims the location name", () => {
    const result = validateEuropeanaRequest(
      validRequest({ locationName: "  Lisbon  " }),
      VALID_AUTH,
      VALID_APP_CHECK
    );
    assert.ok(result.ok);
    assert.strictEqual(result.locationName, "Lisbon");
    assert.strictEqual(result.lat, 38.7112);
    assert.strictEqual(result.lon, -9.1423);
    assert.strictEqual(result.radiusMeters, 15000);
  });

  it("rejects a missing App Check token", () => {
    const result = validateEuropeanaRequest(validRequest(), VALID_AUTH, null);
    assert.ok(!result.ok);
    assert.strictEqual(result.error, "failed-precondition");
  });

  it("rejects an unauthenticated request", () => {
    const result = validateEuropeanaRequest(validRequest(), null, VALID_APP_CHECK);
    assert.ok(!result.ok);
    assert.strictEqual(result.error, "unauthenticated");
  });

  it("rejects a blank or over-long location name", () => {
    for (const locationName of ["", "   ", "a".repeat(MAX_LOCATION_NAME_LENGTH + 1)]) {
      const result = validateEuropeanaRequest(validRequest({ locationName }), VALID_AUTH, VALID_APP_CHECK);
      assert.ok(!result.ok, `locationName '${locationName}' must be rejected`);
      assert.strictEqual(result.error, "invalid-argument");
    }
  });

  it("rejects malformed or out-of-range coordinates", () => {
    for (const overrides of [{ lat: "abc" }, { lat: 91 }, { lon: -181 }, { lat: NaN }, { lat: "38.7x" }]) {
      const result = validateEuropeanaRequest(validRequest(overrides), VALID_AUTH, VALID_APP_CHECK);
      assert.ok(!result.ok, `coordinates ${JSON.stringify(overrides)} must be rejected`);
      assert.strictEqual(result.error, "invalid-argument");
    }
  });

  it("rejects a radius outside the shared 100–50,000 m bounds or not an integer", () => {
    for (const radiusMeters of [99, 50001, 100.5, 0, -1000]) {
      const result = validateEuropeanaRequest(validRequest({ radiusMeters }), VALID_AUTH, VALID_APP_CHECK);
      assert.ok(!result.ok, `radiusMeters ${radiusMeters} must be rejected`);
      assert.strictEqual(result.error, "invalid-argument");
    }
  });
});

describe("fetchEuropeanaRecords", () => {
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
    delete process.env.EUROPEANA_API_KEY;
  });

  /** Stubs axios.get with a Europeana-shaped JSON payload. */
  const stubResponse = (items) => {
    axios.get = async () => ({ data: { items } });
  };

  /** Stubs axios.get to fail exactly like a real axios error (config.url embeds the key). */
  const stubFailingAxios = (status) => {
    axios.get = async (url) => {
      const err = new Error(status ? `Request failed with status code ${status}` : "network unreachable");
      err.config = { url, method: "get" };
      if (status) {
        err.response = { status };
      }
      throw err;
    };
  };

  it("reports auth-config without any network request when the secret is missing", async () => {
    delete process.env.EUROPEANA_API_KEY;
    let called = false;
    axios.get = async () => {
      called = true;
    };

    const outcome = await fetchEuropeanaRecords("Lisbon", 38.7112, -9.1423, 15000);

    assert.strictEqual(outcome.status, "auth-config");
    assert.deepStrictEqual(outcome.records, []);
    assert.strictEqual(called, false, "no provider request must be made without the key");
  });

  it("normalizes, verifies coordinates, and radius-filters the provider answer", async () => {
    process.env.EUROPEANA_API_KEY = "test-key";
    stubResponse([
      // Valid record: within 15 km of the request center.
      {
        id: "/09102/40C3A4DB5B69ADC12D8A2B9D4E69A4A2B0BD3B74",
        title: ["Azulejo Panel"],
        edmIsShownBy: ["https://images.example.com/azulejo.jpg"],
        edmConceptLabel: [["Visual Arts"]],
        dataProvider: ["Museu Nacional do Azulejo"],
        latitude: 38.7150,
        longitude: -9.1400,
      },
      // Null Island record: rejected.
      {
        id: "/bad/null-island",
        title: ["Null Island Artifact"],
        dataProvider: ["Museum"],
        latitude: 0,
        longitude: 0,
      },
      // Out-of-range record (Porto, ~300 km away): rejected by the radius filter.
      {
        id: "/bad/far-away",
        title: ["Porto Tile Workshop"],
        dataProvider: ["Museum"],
        latitude: 41.1496,
        longitude: -8.6109,
      },
      // No title: rejected.
      {
        id: "/bad/no-title",
        edmConceptLabel: [["Visual Arts"]],
        dataProvider: ["Museum"],
        latitude: 38.7150,
        longitude: -9.1400,
      },
      // Legacy record carrying only edmPlaceLatitude/edmPlaceLongitude: accepted.
      {
        id: "/legacy/edm-place",
        title: ["Lisbon Cathedral Record"],
        edmPlaceLatitude: 38.7135,
        edmPlaceLongitude: -9.1330,
      },
    ]);

    const outcome = await fetchEuropeanaRecords("Lisbon", 38.7112, -9.1423, 15000);

    assert.strictEqual(outcome.status, "ok");
    assert.strictEqual(outcome.records.length, 2, "only the in-range, titled, coordinate-verified records survive");

    const first = outcome.records[0];
    assert.strictEqual(first.title, "Azulejo Panel");
    assert.strictEqual(first.description, "Digital cultural heritage record archived by Museu Nacional do Azulejo. Category: Visual Arts.");
    assert.strictEqual(first.category, "history");
    assert.strictEqual(first.latitude, 38.7150);
    assert.strictEqual(first.longitude, -9.14);
    assert.ok(Number.isFinite(first.distanceMeters) && first.distanceMeters > 0 && first.distanceMeters <= 15000);
    assert.strictEqual(first.imageUrl, "https://images.example.com/azulejo.jpg");
    assert.strictEqual(first.dataProvider, "Museu Nacional do Azulejo");
    assert.strictEqual(first.edmConceptLabel, "Visual Arts");
    assert.strictEqual(first.europeanaId, "/09102/40C3A4DB5B69ADC12D8A2B9D4E69A4A2B0BD3B74");
    assert.strictEqual(first.locationName, "Lisbon");

    const legacy = outcome.records[1];
    assert.strictEqual(legacy.title, "Lisbon Cathedral Record");
    assert.strictEqual(legacy.dataProvider, "European Cultural Institution", "attribution defaults are honest, never invented");
  });

  it("classifies 401 as auth-config, 429 as rate-limit, and network errors as transient", async () => {
    process.env.EUROPEANA_API_KEY = "leak-canary-europeana-key";
    for (const [status, expected] of [[401, "auth-config"], [403, "auth-config"], [429, "rate-limit"], [500, "transient"], [null, "transient"]]) {
      stubFailingAxios(status);
      const outcome = await fetchEuropeanaRecords("Lisbon", 38.7112, -9.1423, 15000);
      assert.strictEqual(outcome.status, expected, `status ${status} must classify as ${expected}`);
      assert.deepStrictEqual(outcome.records, []);
    }
  });

  it("never prints the API key or the raw error message on failure", async () => {
    process.env.EUROPEANA_API_KEY = "leak-canary-europeana-key";
    stubFailingAxios(401);
    captured.length = 0;
    console.warn = (...args) => captured.push(args.join(" "));

    await fetchEuropeanaRecords("Lisbon", 38.7112, -9.1423, 15000);

    assert.ok(captured.some((line) => line.includes("[Europeana]")), "the classified failure must be logged");
    for (const line of captured) {
      assert.ok(!line.includes("wskey="), `log line must not contain the key query: ${line}`);
      assert.ok(!line.includes("leak-canary-europeana-key"), `log line must not contain the API key value: ${line}`);
      assert.ok(!line.includes("Request failed"), `log line must not quote the raw error message: ${line}`);
    }
  });
});

/**
 * Tests for the production shared-trip validation helper (functions/sharedTrips.js).
 *
 * These tests import the REAL production module — they never copy or reimplement
 * its validation logic. firestore.rules denies direct client creation of shared
 * trips, so this module is the trusted schema boundary exercised here.
 *
 * Runs as part of:
 *   firebase emulators:exec --only firestore,storage "npm --prefix functions test"
 * (co-resident with the rules emulator suite, but this file itself is pure JS).
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  validateSharedTripPayload,
  validateSharedTripSpot,
  MAX_SPOTS,
  MAX_TITLE_LENGTH,
} = require('./sharedTrips');

const NOW = 1_750_000_000_000;

const validSpot = (index) => ({
  spotId: `spot-${index}`,
  title: `Waypoint ${index}`,
  category: 'viewpoints',
  latitude: 38.71 + index * 0.001,
  longitude: -9.12 - index * 0.001,
  imageUrl: '',
});

const validPayload = (id, overrides = {}) => {
  const payload = {
    id,
    title: 'Alfama Sunrise Walk',
    spots: [validSpot(1), validSpot(2)],
    createdAt: NOW,
    expireAt: NOW + 30 * 24 * 3600 * 1000,
    ...overrides,
  };
  return payload;
};

const expectInvalid = (payload, now = NOW) => {
  assert.throws(() => validateSharedTripPayload(payload, now), (error) => {
    assert.strictEqual(error.code, 'invalid-argument', `expected invalid-argument, got ${error.code}`);
    return true;
  });
};

describe('validateSharedTripPayload', () => {
  it('accepts and normalizes a valid payload', () => {
    const payload = validPayload('trip-1');
    const validated = validateSharedTripPayload(payload, NOW);
    assert.strictEqual(validated.id, 'trip-1');
    assert.strictEqual(validated.title, 'Alfama Sunrise Walk');
    assert.strictEqual(validated.spots.length, 2);
    assert.strictEqual(validated.createdAt, NOW);
    assert.strictEqual(validated.expireAt, NOW + 30 * 24 * 3600 * 1000);
  });

  it('accepts a payload with a full 50-spot list', () => {
    const payload = validPayload('trip-1', {
      spots: Array.from({ length: MAX_SPOTS }, (_, i) => validSpot(i + 1)),
    });
    const validated = validateSharedTripPayload(payload, NOW);
    assert.strictEqual(validated.spots.length, MAX_SPOTS);
  });

  it('accepts a lifetime just under 30 days', () => {
    const payload = validPayload('trip-1', { expireAt: NOW + 30 * 24 * 3600 * 1000 - 1 });
    assert.strictEqual(validateSharedTripPayload(payload, NOW).expireAt, NOW + 30 * 24 * 3600 * 1000 - 1);
  });

  it('rejects a non-object payload', () => {
    expectInvalid('not-an-object');
    expectInvalid(null);
    expectInvalid(42);
  });

  it('rejects an unexpected top-level field', () => {
    expectInvalid({ ...validPayload('trip-1'), ownerUid: 'smuggled' });
  });

  it('rejects every missing mandatory top-level field', () => {
    for (const key of ['id', 'title', 'spots', 'createdAt', 'expireAt']) {
      const payload = validPayload('trip-1');
      delete payload[key];
      expectInvalid(payload);
    }
  });

  it('rejects an empty or oversized title', () => {
    expectInvalid(validPayload('trip-1', { title: '' }));
    expectInvalid(validPayload('trip-1', { title: 'x'.repeat(MAX_TITLE_LENGTH + 1) }));
  });

  it('rejects an empty or oversized id', () => {
    expectInvalid(validPayload(''));
    expectInvalid(validPayload('x'.repeat(129)));
  });

  it('rejects spots when it is not a list', () => {
    expectInvalid(validPayload('trip-1', { spots: { 0: validSpot(1) } }));
    expectInvalid(validPayload('trip-1', { spots: 'waypoints' }));
  });

  it('rejects a spot list with more than 50 entries', () => {
    expectInvalid(
      validPayload('trip-1', { spots: Array.from({ length: MAX_SPOTS + 1 }, (_, i) => validSpot(i + 1)) })
    );
  });

  it('rejects malformed spot entries', () => {
    const malformedTitle = validSpot(1);
    delete malformedTitle.title;
    expectInvalid(validPayload('trip-1', { spots: [malformedTitle] }));

    const extraField = { ...validSpot(1), smuggled: 'field' };
    expectInvalid(validPayload('trip-1', { spots: [extraField] }));

    const missingImageUrl = validSpot(1);
    delete missingImageUrl.imageUrl;
    expectInvalid(validPayload('trip-1', { spots: [missingImageUrl] }));

    expectInvalid(validPayload('trip-1', { spots: [validSpot(1), { ...validSpot(2), latitude: 95.5 }] }));
    expectInvalid(validPayload('trip-1', { spots: [{ ...validSpot(2), longitude: -200.0 }] }));
    expectInvalid(validPayload('trip-1', { spots: [{ ...validSpot(2), latitude: 0, longitude: 0 }] }));
    expectInvalid(validPayload('trip-1', { spots: [{ ...validSpot(2), title: 'x'.repeat(121) }] }));
    expectInvalid(validPayload('trip-1', { spots: [{ ...validSpot(2), imageUrl: 'x'.repeat(2049) }] }));
  });

  it('rejects NaN and Infinity coordinates', () => {
    expectInvalid(validPayload('trip-1', { spots: [{ ...validSpot(2), latitude: NaN }] }));
    expectInvalid(validPayload('trip-1', { spots: [{ ...validSpot(2), longitude: Infinity }] }));
  });

  it('rejects timestamps that are not epoch milliseconds', () => {
    expectInvalid(validPayload('trip-1', { createdAt: 'yesterday' }));
    expectInvalid(validPayload('trip-1', { createdAt: NaN }));
    expectInvalid(validPayload('trip-1', { expireAt: 'tomorrow' }));
    expectInvalid(validPayload('trip-1', { expireAt: null }));
  });

  it('rejects an already-expired trip', () => {
    expectInvalid(validPayload('trip-1', { expireAt: NOW - 60 * 1000 }));
    expectInvalid(validPayload('trip-1', { expireAt: NOW }));
  });

  it('rejects a trip exceeding the 30-day maximum lifetime', () => {
    expectInvalid(validPayload('trip-1', { expireAt: NOW + 31 * 24 * 3600 * 1000 }));
    expectInvalid(validPayload('trip-1', { expireAt: NOW + 30 * 24 * 3600 * 1000 + 1 }));
  });
});

describe('validateSharedTripSpot', () => {
  it('accepts a fully valid spot and normalizes its fields', () => {
    const spot = validSpot(7);
    const validated = validateSharedTripSpot(spot);
    assert.deepStrictEqual(validated, spot);
  });
});

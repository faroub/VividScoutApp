/**
 * Transactional rate-limit helper — production module.
 *
 * The rate limit lives in Firestore counter documents (rate_limits/{key} with
 * { count, windowStart }). Enforcing it INSIDE a caller's Firestore transaction makes
 * the slot consumption commit atomically with the operation it gates: the budget is
 * consumed exactly when the protected operation commits and never when it fails or
 * retries. Standalone enforcement wraps the same decision in its own transaction.
 */

const { FieldValue } = require("firebase-admin/firestore");

const RATE_LIMIT_WINDOW_MS = 3600 * 1000; // 1 hour

/**
 * Rate-limit decision for use INSIDE a Firestore transaction: reads the counter
 * document and, when the request is allowed, increments it within the same
 * transaction. Must be called before any writes in the surrounding callback (Firestore
 * requires all transaction reads to precede writes). Returns true when the request is
 * allowed.
 *
 * Window semantics: the counter covers a fixed window of [windowMs] (default one hour)
 * from its first increment; once the window is older than windowMs it resets to a fresh
 * count of 1. Callers enforcing a 24-hour community limit pass a 24-hour window — the
 * shared helper is the single place window semantics live.
 */
async function decideRateLimitInTransaction(transaction, ref, maxPerWindow, nowMs, windowMs = RATE_LIMIT_WINDOW_MS) {
  const doc = await transaction.get(ref);
  if (!doc.exists) {
    transaction.set(ref, { count: 1, windowStart: nowMs });
    return true;
  }

  const data = doc.data();
  const windowStart = data.windowStart || nowMs;
  const currentCount = data.count || 0;

  if (nowMs - windowStart > windowMs) {
    transaction.set(ref, { count: 1, windowStart: nowMs });
    return true;
  }

  if (currentCount >= maxPerWindow) {
    return false;
  }

  transaction.update(ref, { count: FieldValue.increment(1) });
  return true;
}

module.exports = {
  RATE_LIMIT_WINDOW_MS,
  decideRateLimitInTransaction,
};

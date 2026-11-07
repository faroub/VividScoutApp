package com.example.data.places

/**
 * Abstraction interface for external touristic place data providers.
 *
 * [sourceId] is the canonical provider identifier used by the multi-source
 * orchestration (and by PlaceRepository filtering), e.g. "open_trip_map".
 * Each concrete source maps to exactly one authoritative implementation.
 *
 * Contract of [fetchNear]: a provider that was queried and ANSWERED is reported as
 * `Result.success(...)` — including a legitimately empty answer, which is valid
 * coverage information ("no places in this area"). Any condition that means the
 * provider did NOT answer is reported as `Result.failure(...)` carrying one of the
 * [PlaceSourceFailure] kinds, so the repository never mistakes a provider outage for
 * the absence of nearby places.
 */
interface PlaceSource {
    val sourceId: String
    suspend fun fetchNear(lat: Double, lon: Double, radiusMeters: Int): Result<List<PlaceCandidate>>
}

/**
 * A provider failure, distinguished by kind so the app can present partial results and
 * decide whether retrying makes sense (a transient failure might resolve; an
 * authentication/configuration failure will not).
 */
sealed class PlaceSourceFailure(message: String) : Exception(message) {
    /** Timeout, unreachable network, or a 5xx server error — retrying may help. */
    class TemporaryFailure(message: String = "The place provider is temporarily unavailable.") :
        PlaceSourceFailure(message)

    /**
     * The backend has no credentials for this provider, or the provider rejected them
     * (401/403) — a deployment/configuration problem, not a retryable condition.
     */
    class AuthConfigFailure(message: String = "The place provider is not configured.") :
        PlaceSourceFailure(message)

    /** The provider answered 429 — the caller must back off. */
    class RateLimited(message: String = "The place provider rate limited the request.") :
        PlaceSourceFailure(message)
}

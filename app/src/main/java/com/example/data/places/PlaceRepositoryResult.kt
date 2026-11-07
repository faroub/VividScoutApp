package com.example.data.places

/**
 * Result wrapper containing merged places, cache freshness state, per-provider failure
 * information, and geohash tile key.
 *
 * [providerFailures] lists the requested providers that did NOT answer (a provider
 * outage is not "no places here"): the result is [isPartial] and places from the other
 * providers are still served. [isStale] means no fresh network data backed the result.
 */
data class PlaceRepositoryResult(
    val places: List<MergedPlace>,
    val isStale: Boolean = false,
    val sourceTileKey: String,
    val providerFailures: List<ProviderFailure> = emptyList()
) {
    /** At least one requested provider failed while others delivered results. */
    val isPartial: Boolean
        get() = providerFailures.isNotEmpty()
}

/** A requested provider that did not answer the query. */
data class ProviderFailure(val providerId: String, val kind: ProviderFailureKind)

enum class ProviderFailureKind { TEMPORARY, AUTH_CONFIG, RATE_LIMITED }

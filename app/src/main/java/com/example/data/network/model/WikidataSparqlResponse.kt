package com.example.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Moshi JSON data structures representing SPARQL query response format
 * returned by query.wikidata.org/sparql?format=json.
 */
@JsonClass(generateAdapter = true)
data class WikidataSparqlResponse(
    @Json(name = "head") val head: SparqlHead? = null,
    @Json(name = "results") val results: SparqlResults? = null
)

@JsonClass(generateAdapter = true)
data class SparqlHead(
    @Json(name = "vars") val vars: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class SparqlResults(
    @Json(name = "bindings") val bindings: List<Map<String, SparqlBindingValue>>? = null
)

@JsonClass(generateAdapter = true)
data class SparqlBindingValue(
    @Json(name = "type") val type: String? = null,
    @Json(name = "value") val value: String? = null,
    @Json(name = "xml:lang") val xmlLang: String? = null
)

package com.example.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Moshi-compatible data models for OpenStreetMap Overpass API JSON responses.
 */
@JsonClass(generateAdapter = true)
data class OverpassResponse(
    @field:Json(name = "version") val version: Double? = null,
    @field:Json(name = "generator") val generator: String? = null,
    @field:Json(name = "elements") val elements: List<OverpassElement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OverpassElement(
    @field:Json(name = "type") val type: String, // "node" or "way"
    @field:Json(name = "id") val id: Long,
    @field:Json(name = "lat") val lat: Double? = null,
    @field:Json(name = "lon") val lon: Double? = null,
    @field:Json(name = "center") val center: OverpassCenter? = null,
    @field:Json(name = "tags") val tags: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class OverpassCenter(
    @field:Json(name = "lat") val lat: Double,
    @field:Json(name = "lon") val lon: Double
)

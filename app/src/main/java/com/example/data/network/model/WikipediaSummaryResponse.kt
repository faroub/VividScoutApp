package com.example.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Moshi JSON data structures representing Wikipedia REST API page summary response
 * returned by https://en.wikipedia.org/api/rest_v1/page/summary/{title}.
 */
@JsonClass(generateAdapter = true)
data class WikipediaSummaryResponse(
    @Json(name = "title") val title: String? = null,
    @Json(name = "extract") val extract: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "thumbnail") val thumbnail: WikipediaThumbnail? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaThumbnail(
    @Json(name = "source") val source: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

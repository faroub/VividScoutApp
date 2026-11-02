package com.example.data.network

import com.example.data.network.model.WikipediaSummaryResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit API interface for fetching page summary extracts from the Wikipedia REST API.
 */
interface WikipediaApi {

    /**
     * Fetches structured plain-text page summary for a Wikipedia article title.
     * Endpoint: https://en.wikipedia.org/api/rest_v1/page/summary/{title}
     */
    @GET("api/rest_v1/page/summary/{title}")
    suspend fun getPageSummary(
        @Path("title") title: String
    ): WikipediaSummaryResponse
}

package com.example.data.network

import com.example.data.network.model.WikidataSparqlResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API interface for executing SPARQL queries on the Wikidata Knowledge Graph endpoint.
 */
interface WikidataSparqlApi {

    /**
     * Executes a SPARQL query via GET query parameters and returns parsed WikidataSparqlResponse.
     */
    @GET("sparql")
    suspend fun querySparql(
        @Query("query") sparqlQuery: String,
        @Query("format") format: String = "json"
    ): WikidataSparqlResponse
}

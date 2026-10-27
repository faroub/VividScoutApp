package com.example.data.network

import com.example.data.network.model.OverpassResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API interface for the OpenStreetMap Overpass API interpreter.
 * Supports querying viewpoints, bakeries, shortcuts, and historic landmarks.
 */
interface OverpassApi {

    /**
     * Executes an Overpass QL query via POST with urlencoded data field.
     */
    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun queryOverpass(
        @Field("data") query: String
    ): OverpassResponse

    /**
     * Executes an Overpass QL query via GET with query parameter data.
     */
    @GET("api/interpreter")
    suspend fun queryOverpassGet(
        @Query("data") query: String
    ): OverpassResponse
}

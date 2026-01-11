package com.example.vishnu.api

import com.example.vishnu.model.DirectionsResponse
import retrofit2.http.GET
import retrofit2.http.Query


interface DirectionsApiService {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "driving"
    ): DirectionsResponse
}
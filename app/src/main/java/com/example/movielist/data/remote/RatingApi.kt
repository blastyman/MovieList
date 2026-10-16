package com.example.movielist.data.remote

import com.example.movielist.data.model.RatingStatsDto
import com.example.movielist.data.model.SetRatingRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface RatingApi {
    /** The movie details endpoint; only its rating fields are read here. */
    @GET("movies/{id}") suspend fun stats(@Path("id") movieId: Int): RatingStatsDto

    @PUT("movies/{id}/rating")
    suspend fun setRating(@Path("id") movieId: Int, @Body request: SetRatingRequest): RatingStatsDto

    @DELETE("movies/{id}/rating") suspend fun removeRating(@Path("id") movieId: Int): RatingStatsDto
}

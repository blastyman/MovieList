package com.example.movielist.data.remote

import com.example.movielist.data.model.TrailerDto
import retrofit2.http.GET
import retrofit2.http.Path

fun interface TrailerApi {
    @GET("movies/{id}/trailers") suspend fun trailers(@Path("id") movieId: Int): List<TrailerDto>
}

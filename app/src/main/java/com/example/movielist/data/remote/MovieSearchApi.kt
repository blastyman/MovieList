package com.example.movielist.data.remote

import com.example.movielist.data.model.MoviePageDto
import retrofit2.http.GET
import retrofit2.http.Query

fun interface MovieSearchApi {
    @GET("movies/search")
    suspend fun search(@Query("q") query: String, @Query("page") page: Int): MoviePageDto
}

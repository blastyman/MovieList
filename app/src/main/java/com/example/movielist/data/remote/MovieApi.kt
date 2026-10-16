package com.example.movielist.data.remote

import com.example.movielist.data.model.MoviePageDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Movies through our server, which calls TMDB with its own token. */
fun interface MovieApi {
    @GET("movies/discover") suspend fun discover(@Query("page") page: Int): MoviePageDto
}

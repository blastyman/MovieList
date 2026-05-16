package com.example.movielist.data.remote

import com.example.movielist.data.model.MovieResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TmdbApi {
    @GET("discover/movie")
    suspend fun getMovies(
        @Header("Authorization") token: String,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): MovieResponse
}
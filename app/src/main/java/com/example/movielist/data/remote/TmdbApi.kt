package com.example.movielist.data.remote

import com.example.movielist.data.model.MovieResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApi {
    @GET("discover/movie")
    suspend fun getMovies(
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int,
    ): MovieResponse
}

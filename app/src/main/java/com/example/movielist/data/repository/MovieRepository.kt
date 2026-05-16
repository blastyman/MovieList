package com.example.movielist.data.repository

import com.example.movielist.data.model.Movie
import com.example.movielist.data.remote.RetrofitInstance

class MovieRepository {
    suspend fun getMovies(token: String): List<Movie> {
        return RetrofitInstance.api.getMovies(token).results
    }
}
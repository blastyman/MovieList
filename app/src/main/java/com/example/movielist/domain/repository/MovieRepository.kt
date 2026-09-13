package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Movie

fun interface MovieRepository {
    suspend fun getMovies(): List<Movie>
}

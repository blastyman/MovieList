package com.example.movielist.data.repository

import com.example.movielist.data.model.Movie
import com.example.movielist.data.remote.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MovieRepository {
    suspend fun getMovies(token: String): List<Movie> = withContext(Dispatchers.IO) {
        val allMovies = mutableListOf<Movie>()

        repeat(5) {
            val randomPage = (1..30).random()
            val response = RetrofitInstance.api.getMovies(
                token = token,
                page = randomPage
            )

            allMovies.addAll(response.results)
        }

        allMovies.distinctBy { it.id }.shuffled()
    }
}
package com.example.movielist.domain.model

import com.example.movielist.data.model.Movie

class MovieGroup {
    val users = mutableListOf<AppUser>()

    fun getMatches(movies: List<Movie>): List<Movie> {
        if (users.isEmpty()) return emptyList()

        val commonLikes = users
            .map { it.likedMovies }
            .reduce { acc, likes ->
                acc.intersect(likes).toMutableSet()
            }

        return movies.filter { it.id in commonLikes }
    }
}
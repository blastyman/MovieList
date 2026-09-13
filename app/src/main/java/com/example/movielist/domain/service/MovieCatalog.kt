package com.example.movielist.domain.service

import com.example.movielist.domain.model.AppUser
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory

object MovieCatalog {
    fun categorize(movies: List<Movie>): Map<MovieCategory, List<Movie>> {
        val usedMovieIds = mutableSetOf<Int>()
        return MovieCategory.entries
            .filter { it != MovieCategory.LIKED && it != MovieCategory.DISLIKED }
            .associateWith { category ->
                when (category) {
                    MovieCategory.RECOMMENDED -> movies.shuffled()
                    MovieCategory.POPULAR -> movies
                    else ->
                        movies
                            .filter { movie ->
                                category.genreId in movie.genreIds && movie.id !in usedMovieIds
                            }
                            .shuffled()
                            .also { categoryMovies ->
                                usedMovieIds.addAll(categoryMovies.map(Movie::id))
                            }
                }
            }
    }

    fun findMatches(
        movies: List<Movie>,
        users: List<AppUser>,
        selectedUserIndex: Int,
    ): List<Match> {
        val currentUser = users[selectedUserIndex]
        return users
            .filterIndexed { index, _ -> index != selectedUserIndex }
            .flatMap { otherUser ->
                movies
                    .filter { movie ->
                        movie.id in currentUser.likedMovieIds && movie.id in otherUser.likedMovieIds
                    }
                    .map { movie -> Match(otherUser.name, movie.id, movie.title, movie.posterPath) }
            }
    }
}

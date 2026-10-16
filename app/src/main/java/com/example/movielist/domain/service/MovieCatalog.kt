package com.example.movielist.domain.service

import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory

object MovieCatalog {
    fun categorize(movies: List<Movie>): Map<MovieCategory, List<Movie>> {
        val usedMovieIds = mutableSetOf<Int>()
        return MovieCategory.entries
            .filter { it.isHomeShelf }
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
}

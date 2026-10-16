package com.example.movielist.ui.home

import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.model.RatingStats
import com.example.movielist.domain.model.Reaction
import com.example.movielist.ui.details.SelectedMovieState
import com.example.movielist.ui.lists.MovieListFilter

data class HomeUiState(
    val movies: List<Movie> = emptyList(),
    val categoryMovies: Map<MovieCategory, List<Movie>> = emptyMap(),
    val categoryIndexes: Map<MovieCategory, Int> = emptyMap(),
    /** The account's likes/dislikes by movie id; updated before the server confirms. */
    val reactions: Map<Int, Reaction> = emptyMap(),
    /** Newest first, as the server lists them, plus local changes on top. */
    val likedMovies: List<Movie> = emptyList(),
    val dislikedMovies: List<Movie> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedMovie: SelectedMovieState? = null,
    /** Rating of [selectedMovie]; null while it loads. */
    val selectedRating: RatingStats? = null,
    val listFilter: MovieListFilter = MovieListFilter.LIKED,
    /** A save that failed and was undone. Shown once, then cleared with [HomeViewModel.messageShown]. */
    val message: String? = null,
) {
    fun reactionTo(movieId: Int): Reaction? = reactions[movieId]
}

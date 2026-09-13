package com.example.movielist.ui.home

import com.example.movielist.domain.model.AppUser
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.service.MovieCatalog
import com.example.movielist.ui.details.SelectedMovieState
import com.example.movielist.ui.lists.MovieListFilter
import com.example.movielist.ui.navigation.AppSection

data class HomeUiState(
    val movies: List<Movie> = emptyList(),
    val categoryMovies: Map<MovieCategory, List<Movie>> = emptyMap(),
    val categoryIndexes: Map<MovieCategory, Int> = emptyMap(),
    val users: List<AppUser> = listOf(AppUser("Tester 67"), AppUser("Tester 69")),
    val selectedUserIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedMovie: SelectedMovieState? = null,
    val currentSection: AppSection = AppSection.HOME,
    val listFilter: MovieListFilter = MovieListFilter.LIKED,
    val matchPopup: Match? = null,
) {
    val currentUser: AppUser
        get() = users[selectedUserIndex]

    val likedMovies: List<Movie>
        get() = movies.filter { it.id in currentUser.likedMovieIds }

    val dislikedMovies: List<Movie>
        get() = movies.filter { it.id in currentUser.dislikedMovieIds }

    val matches: List<Match>
        get() = MovieCatalog.findMatches(movies, users, selectedUserIndex)
}

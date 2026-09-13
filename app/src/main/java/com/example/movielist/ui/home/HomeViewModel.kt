package com.example.movielist.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movielist.BuildConfig
import com.example.movielist.data.remote.TmdbClient
import com.example.movielist.data.repository.TmdbMovieRepository
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.repository.MovieRepository
import com.example.movielist.domain.service.MovieCatalog
import com.example.movielist.ui.details.SelectedMovieState
import com.example.movielist.ui.lists.MovieListFilter
import com.example.movielist.ui.navigation.AppSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MovieRepository) : ViewModel() {
    var uiState by mutableStateOf(HomeUiState())
        private set

    private var loadingJob: Job? = null

    init {
        loadMovies()
    }

    fun loadMovies() {
        if (loadingJob?.isActive == true) return
        loadingJob =
            viewModelScope.launch {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                try {
                    val movies = repository.getMovies()
                    uiState =
                        uiState.copy(
                            movies = movies,
                            categoryMovies = MovieCatalog.categorize(movies),
                            categoryIndexes = emptyMap(),
                            selectedMovie = null,
                            isLoading = false,
                        )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    uiState =
                        uiState.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "Unable to load movies.",
                        )
                }
            }
    }

    fun selectSection(section: AppSection) {
        uiState = uiState.copy(currentSection = section, selectedMovie = null)
    }

    fun selectFilter(filter: MovieListFilter) {
        uiState = uiState.copy(listFilter = filter)
    }

    fun selectUser(index: Int) {
        if (index !in uiState.users.indices) return
        uiState = uiState.copy(selectedUserIndex = index, selectedMovie = null, matchPopup = null)
    }

    fun selectMovie(category: MovieCategory, movie: Movie) {
        val index = uiState.categoryMovies[category].orEmpty().indexOfFirst { it.id == movie.id }
        uiState =
            uiState.copy(
                selectedMovie = SelectedMovieState(category, movie),
                categoryIndexes =
                    if (index >= 0) uiState.categoryIndexes + (category to index)
                    else uiState.categoryIndexes,
            )
    }

    fun closeDetails() {
        uiState = uiState.copy(selectedMovie = null)
    }

    fun dismissMatch() {
        uiState = uiState.copy(matchPopup = null)
    }

    fun rateSelectedMovie(liked: Boolean) {
        val selection = uiState.selectedMovie ?: return
        rateMovie(selection.movie, liked)
        advanceCategory(selection.category, showDetails = true)
    }

    fun rateCategoryMovie(category: MovieCategory, liked: Boolean) {
        val movies = uiState.categoryMovies[category].orEmpty()
        if (movies.isEmpty()) return
        val index = uiState.categoryIndexes[category] ?: 0
        rateMovie(movies[index % movies.size], liked)
        advanceCategory(category, showDetails = false)
    }

    private fun rateMovie(movie: Movie, liked: Boolean) {
        val wasLiked = movie.id in uiState.currentUser.likedMovieIds
        val users =
            uiState.users.mapIndexed { index, user ->
                if (index == uiState.selectedUserIndex) user.rateMovie(movie.id, liked) else user
            }
        val updated = uiState.copy(users = users)
        uiState =
            updated.copy(
                matchPopup =
                    if (liked && !wasLiked) {
                        updated.matches.firstOrNull { it.movieId == movie.id }
                    } else updated.matchPopup
            )
    }

    private fun advanceCategory(category: MovieCategory, showDetails: Boolean) {
        val movies = uiState.categoryMovies[category].orEmpty()
        if (movies.isEmpty()) return
        val nextIndex = ((uiState.categoryIndexes[category] ?: 0) + 1) % movies.size
        uiState =
            uiState.copy(
                categoryIndexes = uiState.categoryIndexes + (category to nextIndex),
                selectedMovie =
                    if (showDetails) SelectedMovieState(category, movies[nextIndex])
                    else uiState.selectedMovie,
            )
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    val repository =
                        TmdbMovieRepository(TmdbClient.createApi(BuildConfig.TMDB_TOKEN))
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(repository) as T
                }
            }
    }
}

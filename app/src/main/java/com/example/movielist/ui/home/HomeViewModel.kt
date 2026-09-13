package com.example.movielist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.repository.InMemoryUserPreferencesRepository
import com.example.movielist.domain.repository.MovieRepository
import com.example.movielist.domain.repository.UserPreferencesRepository
import com.example.movielist.domain.service.MovieCatalog
import com.example.movielist.ui.details.SelectedMovieState
import com.example.movielist.ui.lists.MovieListFilter
import com.example.movielist.ui.navigation.AppSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

class HomeViewModel(
    private val repository: MovieRepository,
    private val userPreferences: UserPreferencesRepository = InMemoryUserPreferencesRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiStateFlow: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val uiState: HomeUiState
        get() = _uiState.value

    private var loadingJob: Job? = null
    private val saveMutex = Mutex()

    init {
        loadMovies()
    }

    fun loadMovies() {
        if (loadingJob?.isActive == true) return
        loadingJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val (movies, users) =
                        supervisorScope {
                            val moviesDeferred = async {
                                try {
                                    Result.success(repository.getMovies())
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    Result.failure<List<Movie>>(exception)
                                }
                            }
                            val usersDeferred = async {
                                try {
                                    Result.success(userPreferences.loadUsers(HomeUiState().users))
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    Result.failure(exception)
                                }
                            }
                            moviesDeferred.await().getOrThrow() to
                                usersDeferred.await().getOrThrow()
                        }
                    _uiState.update { state ->
                        state.copy(
                            movies = movies,
                            categoryMovies = MovieCatalog.categorize(movies),
                            categoryIndexes = emptyMap(),
                            selectedMovie = null,
                            isLoading = false,
                            users = users,
                            matches =
                                MovieCatalog.findMatches(movies, users, state.selectedUserIndex),
                        )
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = exception.toUserMessage())
                    }
                }
            }
    }

    private fun Exception.toUserMessage(): String =
        when {
            this is HttpException && code() == 401 ->
                "TMDB authentication failed. Check your token."
            this is HttpException && code() == 429 -> "TMDB rate limit reached. Try again shortly."
            this is HttpException && code() >= 500 -> "TMDB is temporarily unavailable."
            message.isNullOrBlank() -> "Unable to load movies."
            else -> message.orEmpty()
        }

    fun selectSection(section: AppSection) {
        _uiState.update { it.copy(currentSection = section, selectedMovie = null) }
    }

    fun selectFilter(filter: MovieListFilter) {
        _uiState.update { it.copy(listFilter = filter) }
    }

    fun selectUser(index: Int) {
        if (index !in uiState.users.indices) return
        _uiState.update { state ->
            state.copy(
                selectedUserIndex = index,
                selectedMovie = null,
                matchPopup = null,
                matches = MovieCatalog.findMatches(state.movies, state.users, index),
            )
        }
    }

    fun selectMovie(category: MovieCategory, movie: Movie) {
        val index = uiState.categoryMovies[category].orEmpty().indexOfFirst { it.id == movie.id }
        _uiState.update { state ->
            state.copy(
                selectedMovie = SelectedMovieState(category, movie),
                categoryIndexes =
                    if (index >= 0) state.categoryIndexes + (category to index)
                    else state.categoryIndexes,
            )
        }
    }

    fun closeDetails() {
        _uiState.update { it.copy(selectedMovie = null) }
    }

    fun dismissMatch() {
        _uiState.update { it.copy(matchPopup = null) }
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
        val previous = uiState
        val wasLiked = movie.id in previous.currentUser.likedMovieIds
        val users =
            previous.users.mapIndexed { index, user ->
                if (index == previous.selectedUserIndex) user.rateMovie(movie.id, liked) else user
            }
        val matches = MovieCatalog.findMatches(previous.movies, users, previous.selectedUserIndex)
        _uiState.update { state ->
            state.copy(
                users = users,
                matches = matches,
                matchPopup =
                    if (liked && !wasLiked) {
                        matches.firstOrNull { it.movieId == movie.id }
                    } else state.matchPopup,
            )
        }
        viewModelScope.launch { saveMutex.withLock { userPreferences.saveUsers(users) } }
    }

    private fun advanceCategory(category: MovieCategory, showDetails: Boolean) {
        val movies = uiState.categoryMovies[category].orEmpty()
        if (movies.isEmpty()) return
        val nextIndex = ((uiState.categoryIndexes[category] ?: 0) + 1) % movies.size
        _uiState.update { state ->
            state.copy(
                categoryIndexes = state.categoryIndexes + (category to nextIndex),
                selectedMovie =
                    if (showDetails) {
                        SelectedMovieState(category, movies[nextIndex])
                    } else {
                        state.selectedMovie
                    },
            )
        }
    }
}

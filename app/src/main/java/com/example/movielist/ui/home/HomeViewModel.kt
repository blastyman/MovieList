package com.example.movielist.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.model.RatingStats
import com.example.movielist.domain.model.Reaction
import com.example.movielist.domain.repository.InMemoryRatingRepository
import com.example.movielist.domain.repository.InMemoryReactionRepository
import com.example.movielist.domain.repository.MovieRepository
import com.example.movielist.domain.repository.RatingRepository
import com.example.movielist.domain.repository.ReactionRepository
import com.example.movielist.domain.service.MovieCatalog
import com.example.movielist.ui.details.SelectedMovieState
import com.example.movielist.ui.lists.MovieListFilter
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

class HomeViewModel(
    private val repository: MovieRepository,
    private val reactions: ReactionRepository = InMemoryReactionRepository(),
    private val ratings: RatingRepository = InMemoryRatingRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiStateFlow: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val uiState: HomeUiState
        get() = _uiState.value

    private var loadingJob: Job? = null

    /** Loads the stars for the movie in details. Cancelled when another movie is shown; saves never are. */
    private var statsJob: Job? = null

    /** Saves go out one at a time, in order, so a quick like-then-dislike ends as a dislike. */
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
                    coroutineScope {
                        val movies = async { repository.getMovies() }
                        val reactionMap = async { reactions.loadReactions() }
                        val liked = async { reactions.loadMovies(Reaction.LIKE) }
                        val disliked = async { reactions.loadMovies(Reaction.DISLIKE) }
                        val loadedMovies = movies.await()
                        _uiState.update { state ->
                            state.copy(
                                movies = loadedMovies,
                                categoryMovies = MovieCatalog.categorize(loadedMovies),
                                categoryIndexes = emptyMap(),
                                selectedMovie = null,
                                selectedRating = null,
                                reactions = reactionMap.await(),
                                likedMovies = liked.await(),
                                dislikedMovies = disliked.await(),
                                isLoading = false,
                            )
                        }
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

    fun selectFilter(filter: MovieListFilter) {
        _uiState.update { it.copy(listFilter = filter) }
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
        loadRating(movie.id)
    }

    fun closeDetails() {
        statsJob?.cancel()
        _uiState.update { it.copy(selectedMovie = null, selectedRating = null) }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun rateSelectedMovie(liked: Boolean) {
        val selection = uiState.selectedMovie ?: return
        react(selection.movie, if (liked) Reaction.LIKE else Reaction.DISLIKE)
        advanceCategory(selection.category, showDetails = true)
    }

    fun rateCategoryMovie(category: MovieCategory, liked: Boolean) {
        val movies = uiState.categoryMovies[category].orEmpty()
        if (movies.isEmpty()) return
        val index = uiState.categoryIndexes[category] ?: 0
        react(movies[index % movies.size], if (liked) Reaction.LIKE else Reaction.DISLIKE)
        advanceCategory(category, showDetails = false)
    }

    /** [score] 1–10, or null to remove the rating of the movie shown in details. */
    fun setScore(score: Int?) {
        val movieId = uiState.selectedMovie?.movie?.id ?: return
        val before = uiState.selectedRating ?: return
        statsJob?.cancel() // a late load must not overwrite this change
        _uiState.update { it.copy(selectedRating = before.copy(myScore = score)) }
        viewModelScope.launch {
            try {
                val stats = saveMutex.withLock { ratings.setRating(movieId, score) }
                _uiState.update { it.withRatingFor(movieId, stats, ifMyScoreIs = score) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.withRatingFor(movieId, before, ifMyScoreIs = score)
                        .copy(message = "Couldn't save your rating. ${exception.toUserMessage()}")
                }
            }
        }
    }

    private fun loadRating(movieId: Int) {
        statsJob?.cancel()
        _uiState.update { it.copy(selectedRating = null) }
        statsJob =
            viewModelScope.launch {
                try {
                    val stats = ratings.loadStats(movieId)
                    _uiState.update {
                        if (it.selectedMovie?.movie?.id == movieId) it.copy(selectedRating = stats) else it
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // The stars stay hidden; liking and browsing still work.
                }
            }
    }

    /** Applies [stats] only if [movieId] is still on screen and the user hasn't re-rated it since. */
    private fun HomeUiState.withRatingFor(movieId: Int, stats: RatingStats, ifMyScoreIs: Int?) =
        if (selectedMovie?.movie?.id == movieId && selectedRating?.myScore == ifMyScoreIs) {
            copy(selectedRating = stats)
        } else {
            this
        }

    /**
     * Shows the new reaction at once, then saves it. If saving fails, the movie goes back to
     * its previous reaction — unless the user has already changed it again.
     */
    private fun react(movie: Movie, reaction: Reaction) {
        val previous = uiState.reactionTo(movie.id)
        if (previous == reaction) return
        _uiState.update { it.withReaction(movie, reaction) }
        viewModelScope.launch {
            try {
                saveMutex.withLock { reactions.setReaction(movie.id, reaction) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { state ->
                    if (state.reactionTo(movie.id) != reaction) state
                    else
                        state
                            .withReaction(movie, previous)
                            .copy(message = "Couldn't save that. ${exception.toUserMessage()}")
                }
            }
        }
    }

    private fun HomeUiState.withReaction(movie: Movie, reaction: Reaction?): HomeUiState =
        copy(
            reactions = if (reaction == null) reactions - movie.id else reactions + (movie.id to reaction),
            likedMovies =
                likedMovies.filterNot { it.id == movie.id }.let {
                    if (reaction == Reaction.LIKE) listOf(movie) + it else it
                },
            dislikedMovies =
                dislikedMovies.filterNot { it.id == movie.id }.let {
                    if (reaction == Reaction.DISLIKE) listOf(movie) + it else it
                },
        )

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
        if (showDetails) loadRating(movies[nextIndex].id)
    }

    private fun Exception.toUserMessage(): String =
        when {
            // The session is also ended automatically, which brings back the login screen.
            this is HttpException && code() == 401 -> "Your session expired. Please log in again."
            this is HttpException && code() == 429 -> "Too many requests. Try again shortly."
            this is HttpException && code() == 502 -> "The movie database is unavailable right now."
            this is HttpException && code() >= 500 -> "The server is temporarily unavailable."
            this is IOException -> "Can't reach the server. Check your connection."
            message.isNullOrBlank() -> "Unable to load movies."
            else -> message.orEmpty()
        }
}

package com.example.movielist.ui.home

import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.model.RatingStats
import com.example.movielist.domain.model.Reaction
import com.example.movielist.domain.repository.InMemoryRatingRepository
import com.example.movielist.domain.repository.InMemoryReactionRepository
import com.example.movielist.domain.repository.MovieRepository
import com.example.movielist.domain.repository.RatingRepository
import com.example.movielist.domain.repository.ReactionRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val movies = (1..3).map { Movie(it, "Movie $it", "", null, listOf(35)) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadingFailureCanBeRetried() =
        runTest(dispatcher) {
            var attempts = 0
            val viewModel =
                HomeViewModel(
                    MovieRepository {
                        if (attempts++ == 0) throw IOException("Offline")
                        movies
                    }
                )

            assertTrue(viewModel.uiState.isLoading)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.isLoading)
            assertEquals("Can't reach the server. Check your connection.", viewModel.uiState.errorMessage)

            viewModel.loadMovies()
            advanceUntilIdle()
            assertNull(viewModel.uiState.errorMessage)
            assertEquals(movies, viewModel.uiState.movies)
        }

    @Test
    fun repeatedLoadDoesNotStartConcurrentRequests() =
        runTest(dispatcher) {
            var requests = 0
            val viewModel =
                HomeViewModel(
                    MovieRepository {
                        requests++
                        movies
                    }
                )
            viewModel.loadMovies()
            viewModel.loadMovies()
            advanceUntilIdle()
            assertEquals(1, requests)
        }

    @Test
    fun existingReactionsAreLoadedWithTheirMovies() =
        runTest(dispatcher) {
            val reactions = InMemoryReactionRepository()
            val oldFavourite = Movie(99, "Not on home today", "", null, emptyList())
            reactions.movies[99] = oldFavourite
            reactions.setReaction(99, Reaction.LIKE)
            reactions.movies[2] = movies[1]
            reactions.setReaction(2, Reaction.DISLIKE)

            val viewModel = HomeViewModel(MovieRepository { movies }, reactions)
            advanceUntilIdle()

            assertEquals(mapOf(99 to Reaction.LIKE, 2 to Reaction.DISLIKE), viewModel.uiState.reactions)
            // Liked movies come from the server, not just from today's home page.
            assertEquals(listOf(oldFavourite), viewModel.uiState.likedMovies)
            assertEquals(listOf(movies[1]), viewModel.uiState.dislikedMovies)
        }

    @Test
    fun likingShowsAtOnceAndIsSaved() =
        runTest(dispatcher) {
            val reactions = InMemoryReactionRepository()
            val viewModel = HomeViewModel(MovieRepository { movies }, reactions)
            advanceUntilIdle()

            viewModel.selectMovie(MovieCategory.POPULAR, movies.first())
            viewModel.rateSelectedMovie(liked = true)

            // Before the save finishes:
            assertEquals(Reaction.LIKE, viewModel.uiState.reactionTo(movies.first().id))
            assertEquals(listOf(movies.first()), viewModel.uiState.likedMovies)

            advanceUntilIdle()
            assertEquals(mapOf(movies.first().id to Reaction.LIKE), reactions.loadReactions())
        }

    @Test
    fun changingReactionMovesMovieBetweenListsWithoutMutatingPreviousState() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies })
            advanceUntilIdle()
            val original = viewModel.uiState
            viewModel.selectMovie(MovieCategory.LIKED, movies.first())
            viewModel.rateSelectedMovie(liked = true)
            val likedState = viewModel.uiState
            viewModel.rateSelectedMovie(liked = false)
            advanceUntilIdle()

            assertTrue(original.reactions.isEmpty())
            assertEquals(listOf(movies.first()), likedState.likedMovies)
            assertTrue(viewModel.uiState.likedMovies.isEmpty())
            assertEquals(listOf(movies.first()), viewModel.uiState.dislikedMovies)
        }

    @Test
    fun failedSaveIsUndoneWithAMessage() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies }, FailingReactions())
            advanceUntilIdle()

            viewModel.rateCategoryMovie(MovieCategory.POPULAR, liked = true)
            assertEquals(Reaction.LIKE, viewModel.uiState.reactionTo(movies.first().id))

            advanceUntilIdle()
            assertNull(viewModel.uiState.reactionTo(movies.first().id))
            assertTrue(viewModel.uiState.likedMovies.isEmpty())
            assertEquals(
                "Couldn't save that. Can't reach the server. Check your connection.",
                viewModel.uiState.message,
            )

            viewModel.messageShown()
            assertNull(viewModel.uiState.message)
        }

    @Test
    fun failedSaveDoesNotUndoANewerChoice() =
        runTest(dispatcher) {
            val reactions = FailingReactions(failFor = setOf(Reaction.LIKE))
            val viewModel = HomeViewModel(MovieRepository { movies }, reactions)
            advanceUntilIdle()

            viewModel.selectMovie(MovieCategory.LIKED, movies.first())
            viewModel.rateSelectedMovie(liked = true) // will fail
            viewModel.rateSelectedMovie(liked = false) // succeeds, and is newer
            advanceUntilIdle()

            assertEquals(Reaction.DISLIKE, viewModel.uiState.reactionTo(movies.first().id))
            assertEquals(listOf(movies.first()), viewModel.uiState.dislikedMovies)
            assertNull(viewModel.uiState.message)
        }

    @Test
    fun selectingPosterAdvancesFromThatMovieAndWrapsAround() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies })
            advanceUntilIdle()
            viewModel.selectMovie(MovieCategory.POPULAR, movies.last())
            viewModel.rateSelectedMovie(liked = true)

            assertEquals(movies.first(), viewModel.uiState.selectedMovie?.movie)
            assertEquals(mapOf(movies.last().id to Reaction.LIKE), viewModel.uiState.reactions)
        }

    @Test
    fun closingDetailsClearsSelectionAndRating() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies })
            advanceUntilIdle()
            viewModel.selectMovie(MovieCategory.POPULAR, movies.first())
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.selectedMovie)
            assertNotNull(viewModel.uiState.selectedRating)

            viewModel.closeDetails()
            assertNull(viewModel.uiState.selectedMovie)
            assertNull(viewModel.uiState.selectedRating)
        }

    @Test
    fun ratingLoadsForEachMovieShownAndCanBeChangedAndCleared() =
        runTest(dispatcher) {
            val ratings = InMemoryRatingRepository()
            ratings.setRating(movies[1].id, 9)
            val viewModel = HomeViewModel(MovieRepository { movies }, ratings = ratings)
            advanceUntilIdle()

            viewModel.selectMovie(MovieCategory.POPULAR, movies[0])
            assertNull(viewModel.uiState.selectedRating) // loading
            advanceUntilIdle()
            assertEquals(RatingStats(average = null, count = 0, myScore = null), viewModel.uiState.selectedRating)

            viewModel.setScore(7)
            assertEquals(7, viewModel.uiState.selectedRating?.myScore) // before the save
            advanceUntilIdle()
            assertEquals(RatingStats(average = 7.0, count = 1, myScore = 7), viewModel.uiState.selectedRating)

            viewModel.setScore(null)
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.selectedRating?.count)

            // Liking in details moves on to the next movie and loads its rating.
            viewModel.rateSelectedMovie(liked = true)
            advanceUntilIdle()
            assertEquals(movies[1], viewModel.uiState.selectedMovie?.movie)
            assertEquals(9, viewModel.uiState.selectedRating?.myScore)
        }

    @Test
    fun failedRatingIsUndoneWithAMessage() =
        runTest(dispatcher) {
            val failing =
                object : RatingRepository {
                    override suspend fun loadStats(movieId: Int) = RatingStats(6.0, 2, null)

                    override suspend fun setRating(movieId: Int, score: Int?): RatingStats =
                        throw IOException("Offline")
                }
            val viewModel = HomeViewModel(MovieRepository { movies }, ratings = failing)
            advanceUntilIdle()
            viewModel.selectMovie(MovieCategory.POPULAR, movies.first())
            advanceUntilIdle()

            viewModel.setScore(10)
            advanceUntilIdle()

            assertEquals(RatingStats(6.0, 2, null), viewModel.uiState.selectedRating)
            assertTrue(viewModel.uiState.message.orEmpty().startsWith("Couldn't save your rating."))
        }

    @Test
    fun emptyCatalogCompletesLoadingAndIgnoresRating() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { emptyList() })
            advanceUntilIdle()
            viewModel.rateCategoryMovie(MovieCategory.RECOMMENDED, liked = true)
            assertFalse(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.errorMessage)
            assertTrue(viewModel.uiState.likedMovies.isEmpty())
        }

    /** Loads fine; saving [failFor] reactions throws like a dropped connection. */
    private class FailingReactions(
        private val failFor: Set<Reaction?> = setOf(Reaction.LIKE, Reaction.DISLIKE, null)
    ) : ReactionRepository {
        private val delegate = InMemoryReactionRepository()

        override suspend fun loadReactions() = delegate.loadReactions()

        override suspend fun loadMovies(reaction: Reaction) = delegate.loadMovies(reaction)

        override suspend fun setReaction(movieId: Int, reaction: Reaction?) {
            if (reaction in failFor) throw IOException("Offline")
            delegate.setReaction(movieId, reaction)
        }
    }
}

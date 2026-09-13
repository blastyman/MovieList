package com.example.movielist.ui.home

import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.MovieCategory
import com.example.movielist.domain.repository.MovieRepository
import com.example.movielist.ui.navigation.AppSection
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
            assertEquals("Offline", viewModel.uiState.errorMessage)

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
    fun changingRatingUpdatesListsWithoutMutatingPreviousState() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies })
            advanceUntilIdle()
            val original = viewModel.uiState
            viewModel.selectMovie(MovieCategory.LIKED, movies.first())
            viewModel.rateSelectedMovie(liked = true)
            val likedState = viewModel.uiState
            viewModel.rateSelectedMovie(liked = false)

            assertTrue(original.currentUser.likedMovieIds.isEmpty())
            assertEquals(listOf(movies.first()), likedState.likedMovies)
            assertTrue(viewModel.uiState.likedMovies.isEmpty())
            assertEquals(listOf(movies.first()), viewModel.uiState.dislikedMovies)
        }

    @Test
    fun selectingPosterAdvancesFromThatMovieAndWrapsAround() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies })
            advanceUntilIdle()
            viewModel.selectMovie(MovieCategory.POPULAR, movies.last())
            viewModel.rateSelectedMovie(liked = true)

            assertEquals(movies.first(), viewModel.uiState.selectedMovie?.movie)
            assertEquals(setOf(movies.last().id), viewModel.uiState.currentUser.likedMovieIds)
        }

    @Test
    fun switchingUsersKeepsRatingsSeparateAndCreatesMutualMatch() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies })
            advanceUntilIdle()
            viewModel.selectMovie(MovieCategory.LIKED, movies.first())
            viewModel.rateSelectedMovie(liked = true)
            assertNull(viewModel.uiState.matchPopup)

            viewModel.selectUser(1)
            assertTrue(viewModel.uiState.likedMovies.isEmpty())
            viewModel.selectMovie(MovieCategory.LIKED, movies.first())
            viewModel.rateSelectedMovie(liked = true)
            assertEquals(movies.first().id, viewModel.uiState.matchPopup?.movieId)
            assertEquals(1, viewModel.uiState.matches.size)

            viewModel.dismissMatch()
            viewModel.rateSelectedMovie(liked = true)
            assertNull(viewModel.uiState.matchPopup)

            viewModel.rateSelectedMovie(liked = false)
            assertTrue(viewModel.uiState.matches.isEmpty())
            viewModel.selectUser(0)
            assertEquals(listOf(movies.first()), viewModel.uiState.likedMovies)
        }

    @Test
    fun changingSectionClosesDetails() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(MovieRepository { movies })
            advanceUntilIdle()
            viewModel.selectMovie(MovieCategory.POPULAR, movies.first())
            assertNotNull(viewModel.uiState.selectedMovie)
            viewModel.selectSection(AppSection.LISTS)
            assertNull(viewModel.uiState.selectedMovie)
            assertEquals(AppSection.LISTS, viewModel.uiState.currentSection)
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
}

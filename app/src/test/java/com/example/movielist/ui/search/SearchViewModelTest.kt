package com.example.movielist.ui.search

import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.MovieSearchRepository
import com.example.movielist.domain.repository.UserRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val movieQueries = mutableListOf<Pair<String, Int>>()
    private val peopleQueries = mutableListOf<Pair<String, Int>>()

    /** Two pages of movies ("<query> 1..3"), one page of people. */
    private val movieSearch = MovieSearchRepository { query, page ->
        movieQueries += query to page
        if (page == 1) SearchPage(List(2) { Movie(it, "$query ${it + 1}", "", null, emptyList()) }, hasMore = true)
        else SearchPage(listOf(Movie(99, "$query 3", "", null, emptyList())), hasMore = false)
    }

    private val users = object : UserRepository {
        override suspend fun search(query: String, page: Int): SearchPage<UserSummary> {
            peopleQueries += query to page
            return SearchPage(listOf(UserSummary("1", "${query}_fan", null, FriendshipState.NONE)), hasMore = false)
        }

        override suspend fun profile(userId: String): UserProfile = error("not used")
    }

    @Test
    fun searchesBothTabsOnlyAfterTypingStops() =
        runTest(dispatcher) {
            val viewModel = SearchViewModel(movieSearch, users)

            viewModel.onQueryChange("in")
            advanceTimeBy(100)
            viewModel.onQueryChange("inc")
            advanceTimeBy(100)
            viewModel.onQueryChange("ince")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS + 1)
            runCurrent()

            assertEquals(listOf("ince" to 1), movieQueries) // the intermediate queries never ran
            assertEquals(listOf("ince" to 1), peopleQueries)
            assertEquals(listOf("ince 1", "ince 2"), viewModel.uiState.movies.items.map { it.title })
            assertEquals("ince_fan", viewModel.uiState.people.items.single().username)
        }

    @Test
    fun oneCharacterSearchesMoviesButNotPeople() =
        runTest(dispatcher) {
            val viewModel = SearchViewModel(movieSearch, users)
            viewModel.onQueryChange("x")
            advanceUntilIdle()

            assertEquals(listOf("x" to 1), movieQueries)
            assertTrue(peopleQueries.isEmpty())
        }

    @Test
    fun loadMoreAppendsTheNextPageOfTheCurrentTab() =
        runTest(dispatcher) {
            val viewModel = SearchViewModel(movieSearch, users)
            viewModel.onQueryChange("star")
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()
            viewModel.loadMore() // nothing more to load
            advanceUntilIdle()

            assertEquals(listOf("star 1", "star 2", "star 3"), viewModel.uiState.movies.items.map { it.title })
            assertEquals(listOf("star" to 1, "star" to 2), movieQueries)
        }

    @Test
    fun clearingTheQueryClearsResults() =
        runTest(dispatcher) {
            val viewModel = SearchViewModel(movieSearch, users)
            viewModel.onQueryChange("star")
            advanceUntilIdle()

            viewModel.onQueryChange("")
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.searchedQuery)
            assertTrue(viewModel.uiState.movies.items.isEmpty())
        }

    @Test
    fun failedSearchShowsAnErrorAndCanBeRetried() =
        runTest(dispatcher) {
            var fail = true
            val flaky = MovieSearchRepository { query, page ->
                if (fail) throw IOException("Offline")
                movieSearch.search(query, page)
            }
            val viewModel = SearchViewModel(flaky, users)
            viewModel.onQueryChange("star")
            advanceUntilIdle()
            assertEquals("Can't reach the server. Check your connection.", viewModel.uiState.movies.error)

            fail = false
            viewModel.retry()
            advanceUntilIdle()
            assertNull(viewModel.uiState.movies.error)
            assertEquals(2, viewModel.uiState.movies.items.size)
        }
}

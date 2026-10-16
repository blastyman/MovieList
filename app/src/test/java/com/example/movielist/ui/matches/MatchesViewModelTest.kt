package com.example.movielist.ui.matches

import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.model.MatchPerson
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.MatchRepository
import java.io.IOException
import java.time.Instant
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val match =
        Match(
            Movie(1, "Inception", "", null, emptyList()),
            listOf(MatchPerson(UserSummary("a", "anna", null, FriendshipState.NONE), sharedLikes = 1)),
            peopleCount = 1,
            Instant.EPOCH,
        )

    @Test
    fun loadsMatchesAndSaysWhenItHasLoaded() =
        runTest(dispatcher) {
            val viewModel = MatchesViewModel { listOf(match) }
            assertFalse(viewModel.uiState.loaded)

            viewModel.load()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.loaded)
            assertEquals(listOf(match), viewModel.uiState.matches)
        }

    @Test
    fun failureCanBeRetried() =
        runTest(dispatcher) {
            var fail = true
            val viewModel = MatchesViewModel(MatchRepository { if (fail) throw IOException("Offline") else listOf(match) })

            viewModel.load()
            advanceUntilIdle()
            assertEquals("Can't reach the server. Check your connection.", viewModel.uiState.error)

            fail = false
            viewModel.load()
            advanceUntilIdle()
            assertNull(viewModel.uiState.error)
            assertEquals(1, viewModel.uiState.matches.size)
        }
}

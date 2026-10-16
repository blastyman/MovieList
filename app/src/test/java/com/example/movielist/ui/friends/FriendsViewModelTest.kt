package com.example.movielist.ui.friends

import com.example.movielist.domain.model.FriendshipState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsFriendsAndBothKindsOfRequests() =
        runTest(dispatcher) {
            val repo = FakeFriends()
            repo.add("1", FriendshipState.FRIENDS)
            repo.add("2", FriendshipState.REQUEST_RECEIVED)
            repo.add("3", FriendshipState.REQUEST_SENT)
            val viewModel = FriendsViewModel(repo)

            viewModel.load()
            advanceUntilIdle()

            assertEquals(listOf("1"), viewModel.uiState.friends.map { it.id })
            assertEquals(listOf("2"), viewModel.uiState.incoming.map { it.user.id })
            assertEquals(listOf("3"), viewModel.uiState.outgoing.map { it.user.id })
        }

    @Test
    fun acceptingMovesTheRequestIntoFriends() =
        runTest(dispatcher) {
            val repo = FakeFriends()
            val asker = repo.add("2", FriendshipState.REQUEST_RECEIVED)
            val viewModel = FriendsViewModel(repo)
            viewModel.load()
            advanceUntilIdle()

            viewModel.accept(asker)
            assertTrue("2" in viewModel.uiState.busy) // spinner while saving
            advanceUntilIdle()

            assertTrue(viewModel.uiState.incoming.isEmpty())
            assertEquals(listOf("2"), viewModel.uiState.friends.map { it.id })
            assertTrue(viewModel.uiState.busy.isEmpty())
        }

    @Test
    fun declineCancelAndRemoveTakePeopleOffTheLists() =
        runTest(dispatcher) {
            val repo = FakeFriends()
            val asker = repo.add("2", FriendshipState.REQUEST_RECEIVED)
            val asked = repo.add("3", FriendshipState.REQUEST_SENT)
            val friend = repo.add("1", FriendshipState.FRIENDS)
            val viewModel = FriendsViewModel(repo)
            viewModel.load()
            advanceUntilIdle()

            viewModel.decline(asker)
            viewModel.cancel(asked)
            viewModel.askRemove(friend)
            assertEquals(friend, viewModel.uiState.confirmRemove)
            viewModel.confirmRemove()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.incoming.isEmpty())
            assertTrue(viewModel.uiState.outgoing.isEmpty())
            assertTrue(viewModel.uiState.friends.isEmpty())
            assertNull(viewModel.uiState.confirmRemove)
            assertEquals(FriendshipState.NONE, repo.states["1"])
        }

    @Test
    fun failedActionShowsAMessageAndReloads() =
        runTest(dispatcher) {
            val repo = FakeFriends()
            val asker = repo.add("2", FriendshipState.REQUEST_RECEIVED)
            val viewModel = FriendsViewModel(repo)
            viewModel.load()
            advanceUntilIdle()

            repo.failWith = IOException("Offline")
            viewModel.accept(asker)
            advanceUntilIdle()

            assertEquals(listOf("2"), viewModel.uiState.incoming.map { it.user.id }) // still there
            assertTrue(viewModel.uiState.message.orEmpty().startsWith("Couldn't accept the request."))
        }
}

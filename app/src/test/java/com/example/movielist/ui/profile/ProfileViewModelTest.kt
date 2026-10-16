package com.example.movielist.ui.profile

import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.ProfileStats
import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.UserRepository
import com.example.movielist.ui.friends.FakeFriends
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun profile(isMe: Boolean, displayName: String? = null) =
        UserProfile("u1", "stelios", displayName, Instant.EPOCH, isMe, FriendshipState.NONE, ProfileStats(1, 2, 3, 4), emptyList())

    private class FakeUsers(var result: () -> UserProfile) : UserRepository {
        var loads = 0

        override suspend fun search(query: String, page: Int): SearchPage<UserSummary> = error("not used")

        override suspend fun profile(userId: String): UserProfile {
            loads++
            return result()
        }
    }

    @Test
    fun loadsOnceAndRetriesAfterAFailure() =
        runTest(dispatcher) {
            var fail = true
            val users = FakeUsers { if (fail) throw IOException("Offline") else profile(isMe = false) }
            val viewModel = ProfileViewModel(users, FakeFriends())

            viewModel.load("u1")
            advanceUntilIdle()
            assertEquals("Can't reach the server. Check your connection.", viewModel.uiState.error)

            fail = false
            viewModel.retry()
            advanceUntilIdle()
            assertEquals("stelios", viewModel.uiState.profile?.username)

            viewModel.load("u1") // already shown: no reload
            advanceUntilIdle()
            assertEquals(2, users.loads)
        }

    @Test
    fun friendshipButtonActionsUpdateTheStateAndFriendCount() =
        runTest(dispatcher) {
            val friends = FakeFriends()
            val viewModel = ProfileViewModel(FakeUsers { profile(isMe = false) }, friends)
            viewModel.load("u1")
            advanceUntilIdle()
            assertEquals(4, viewModel.uiState.profile?.stats?.friends)

            viewModel.addFriend()
            advanceUntilIdle()
            assertEquals(FriendshipState.REQUEST_SENT, viewModel.uiState.profile?.friendship)
            assertEquals(4, viewModel.uiState.profile?.stats?.friends) // not friends yet

            viewModel.cancelFriendRequest()
            advanceUntilIdle()
            assertEquals(FriendshipState.NONE, viewModel.uiState.profile?.friendship)

            friends.states["u1"] = FriendshipState.REQUEST_RECEIVED // they ask us
            viewModel.acceptFriend()
            advanceUntilIdle()
            assertEquals(FriendshipState.FRIENDS, viewModel.uiState.profile?.friendship)
            assertEquals(5, viewModel.uiState.profile?.stats?.friends)

            viewModel.askUnfriend()
            assertEquals(true, viewModel.uiState.confirmUnfriend)
            viewModel.confirmUnfriend()
            advanceUntilIdle()
            assertEquals(FriendshipState.NONE, viewModel.uiState.profile?.friendship)
            assertEquals(4, viewModel.uiState.profile?.stats?.friends)
        }
}

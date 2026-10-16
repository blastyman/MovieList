package com.example.movielist.ui.profile

import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.ProfileStats
import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.model.SessionState
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.AuthRepository
import com.example.movielist.domain.repository.UserRepository
import com.example.movielist.ui.friends.FakeFriends
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private class FakeAuth(var result: Result<Account>) : AuthRepository {
        var savedName: String? = null
        override val session: StateFlow<SessionState> = MutableStateFlow(SessionState.LoggedIn)
        override val account: StateFlow<Account?> = MutableStateFlow(null)

        override suspend fun register(username: String, email: String, password: String) = result

        override suspend fun login(login: String, password: String) = result

        override suspend fun refreshAccount() = result

        override suspend fun updateDisplayName(displayName: String): Result<Account> {
            savedName = displayName
            return result
        }

        override suspend fun logout() = Unit
    }

    @Test
    fun loadsOnceAndRetriesAfterAFailure() =
        runTest(dispatcher) {
            var fail = true
            val users = FakeUsers { if (fail) throw IOException("Offline") else profile(isMe = false) }
            val viewModel = ProfileViewModel(users, FakeAuth(Result.failure(IllegalStateException())), FakeFriends())

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
    fun editingTheNameUpdatesTheProfile() =
        runTest(dispatcher) {
            val auth = FakeAuth(Result.success(Account("u1", "stelios", "s@example.com", "Stelios A.", null)))
            val viewModel = ProfileViewModel(FakeUsers { profile(isMe = true) }, auth, FakeFriends())
            viewModel.load("u1")
            advanceUntilIdle()

            viewModel.startEditName()
            assertEquals("", viewModel.uiState.nameDraft)
            viewModel.onNameDraftChange("Stelios A.")
            viewModel.saveName()
            advanceUntilIdle()

            assertEquals("Stelios A.", auth.savedName)
            assertEquals("Stelios A.", viewModel.uiState.profile?.displayName)
            assertNull(viewModel.uiState.nameDraft) // dialog closed
        }

    @Test
    fun failedNameSaveKeepsTheDialogOpenWithTheReason() =
        runTest(dispatcher) {
            val auth = FakeAuth(Result.failure(AuthException(AuthError.Server("Display name must be at most 50 characters"))))
            val viewModel = ProfileViewModel(FakeUsers { profile(isMe = true, displayName = "Old") }, auth, FakeFriends())
            viewModel.load("u1")
            advanceUntilIdle()

            viewModel.startEditName()
            viewModel.onNameDraftChange("New")
            viewModel.saveName()
            advanceUntilIdle()

            assertEquals("New", viewModel.uiState.nameDraft)
            assertEquals("Couldn't save your name. Display name must be at most 50 characters", viewModel.uiState.message)
            assertEquals("Old", viewModel.uiState.profile?.displayName)
        }

    @Test
    fun someoneElsesNameCannotBeEdited() =
        runTest(dispatcher) {
            val viewModel = ProfileViewModel(FakeUsers { profile(isMe = false) }, FakeAuth(Result.failure(IllegalStateException())), FakeFriends())
            viewModel.load("u1")
            advanceUntilIdle()

            viewModel.startEditName()
            assertNull(viewModel.uiState.nameDraft)
        }

    @Test
    fun friendshipButtonActionsUpdateTheStateAndFriendCount() =
        runTest(dispatcher) {
            val friends = FakeFriends()
            val viewModel = ProfileViewModel(FakeUsers { profile(isMe = false) }, FakeAuth(Result.failure(IllegalStateException())), friends)
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

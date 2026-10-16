package com.example.movielist.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.repository.FriendRepository
import com.example.movielist.domain.repository.UserRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userId: String? = null,
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** A friend action (add, accept, …) is in flight. */
    val isFriendBusy: Boolean = false,
    /** Waiting for the user to confirm removing this friend. */
    val confirmUnfriend: Boolean = false,
    /** Why a friend action failed. */
    val message: String? = null,
)

/** One person's profile. Lives as long as its screen (a navigation entry). */
class ProfileViewModel(
    private val users: UserRepository,
    private val friends: FriendRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiStateFlow: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    val uiState: ProfileUiState
        get() = _uiState.value

    private var loadJob: Job? = null

    /** Loads [userId]'s profile; does nothing if it is already shown, unless [refresh]. */
    fun load(userId: String, refresh: Boolean = false) {
        if (!refresh && uiState.userId == userId && (uiState.profile != null || uiState.isLoading)) return
        loadJob?.cancel()
        _uiState.update { it.copy(userId = userId, isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val profile = users.profile(userId)
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, error = exception.toUserMessage()) }
            }
        }
    }

    fun retry() {
        uiState.userId?.let { load(it, refresh = true) }
    }

    // ───────────── Friendship ─────────────

    fun addFriend() = friendAction { friends.sendRequest(it) }

    fun acceptFriend() = friendAction { friends.accept(it) }

    fun declineFriend() = friendAction { friends.decline(it) }

    fun cancelFriendRequest() = friendAction { friends.cancel(it) }

    fun askUnfriend() {
        _uiState.update { it.copy(confirmUnfriend = true) }
    }

    fun cancelUnfriend() {
        _uiState.update { it.copy(confirmUnfriend = false) }
    }

    fun confirmUnfriend() {
        _uiState.update { it.copy(confirmUnfriend = false) }
        friendAction { friends.remove(it) }
    }

    /** Runs [call] for the profile's user and shows the new state; the friend count follows. */
    private fun friendAction(call: suspend (userId: String) -> FriendshipState) {
        val profile = uiState.profile?.takeIf { !it.isMe } ?: return
        if (uiState.isFriendBusy) return
        _uiState.update { it.copy(isFriendBusy = true) }
        viewModelScope.launch {
            try {
                val state = call(profile.id)
                _uiState.update { current ->
                    val shown = current.profile ?: return@update current.copy(isFriendBusy = false)
                    val change = when {
                        shown.friendship != FriendshipState.FRIENDS && state == FriendshipState.FRIENDS -> 1
                        shown.friendship == FriendshipState.FRIENDS && state != FriendshipState.FRIENDS -> -1
                        else -> 0
                    }
                    current.copy(
                        profile = shown.copy(friendship = state, stats = shown.stats.copy(friends = shown.stats.friends + change)),
                        isFriendBusy = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isFriendBusy = false, message = "That didn't work. ${exception.toUserMessage()}") }
                retry()
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

}

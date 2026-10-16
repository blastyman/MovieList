package com.example.movielist.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.FriendRequest
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.FriendRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val friends: List<UserSummary> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: String? = null,
    /** People with an action in flight; their buttons show a spinner. */
    val busy: Set<String> = emptySet(),
    /** Waiting for the user to confirm removing this friend. */
    val confirmRemove: UserSummary? = null,
    val message: String? = null,
)

class FriendsViewModel(private val friends: FriendRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiStateFlow: StateFlow<FriendsUiState> = _uiState.asStateFlow()
    val uiState: FriendsUiState
        get() = _uiState.value

    /** Called each time the screen opens, so requests sent meanwhile show up. */
    fun load() {
        if (uiState.isLoading) return
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val list = async { friends.friends() }
                    val requests = async { friends.requests() }
                    val loaded = requests.await()
                    _uiState.update {
                        it.copy(
                            friends = list.await(),
                            incoming = loaded.incoming,
                            outgoing = loaded.outgoing,
                            isLoading = false,
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = exception.toUserMessage()) }
            }
        }
    }

    fun accept(user: UserSummary) =
        act(user, "Couldn't accept the request.", { friends.accept(user.id) }) { state, result ->
            state.copy(
                incoming = state.incoming.withoutUser(user.id),
                friends =
                    if (result == FriendshipState.FRIENDS) {
                        (state.friends + user.copy(friendship = FriendshipState.FRIENDS)).sortedBy { it.name.lowercase() }
                    } else {
                        state.friends
                    },
            )
        }

    fun decline(user: UserSummary) =
        act(user, "Couldn't decline the request.", { friends.decline(user.id) }) { state, _ ->
            state.copy(incoming = state.incoming.withoutUser(user.id))
        }

    fun cancel(user: UserSummary) =
        act(user, "Couldn't cancel the request.", { friends.cancel(user.id) }) { state, _ ->
            state.copy(outgoing = state.outgoing.withoutUser(user.id))
        }

    fun askRemove(user: UserSummary) {
        _uiState.update { it.copy(confirmRemove = user) }
    }

    fun cancelRemove() {
        _uiState.update { it.copy(confirmRemove = null) }
    }

    fun confirmRemove() {
        val user = uiState.confirmRemove ?: return
        _uiState.update { it.copy(confirmRemove = null) }
        act(user, "Couldn't remove your friend.", { friends.remove(user.id) }) { state, _ ->
            state.copy(friends = state.friends.filterNot { it.id == user.id })
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Runs one action for [user], showing a spinner on their row. On success [apply] updates
     * the lists; on failure (or if the other side already changed it) the lists are reloaded.
     */
    private fun act(
        user: UserSummary,
        failure: String,
        call: suspend () -> FriendshipState,
        apply: (FriendsUiState, FriendshipState) -> FriendsUiState,
    ) {
        if (user.id in uiState.busy) return
        _uiState.update { it.copy(busy = it.busy + user.id) }
        viewModelScope.launch {
            try {
                val result = call()
                _uiState.update { apply(it, result).copy(busy = it.busy - user.id) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(busy = it.busy - user.id, message = "$failure ${exception.toUserMessage()}") }
                load()
            }
        }
    }

    private fun List<FriendRequest>.withoutUser(userId: String) = filterNot { it.user.id == userId }
}

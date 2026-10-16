package com.example.movielist.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.FriendRepository
import com.example.movielist.domain.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateGroupUiState(
    val name: String = "",
    val friends: List<UserSummary> = emptyList(),
    val loadingFriends: Boolean = true,
    val friendsError: String? = null,
    /** Ids of the friends picked so far. */
    val selected: Set<String> = emptySet(),
    val isCreating: Boolean = false,
    /** Set once the group exists; the screen then opens it. */
    val createdGroupId: String? = null,
    val message: String? = null,
) {
    /** People in the group if created now: the picked friends plus you. */
    val memberCount: Int
        get() = selected.size + 1

    val isFull: Boolean
        get() = memberCount >= MAX_GROUP_MEMBERS

    val canCreate: Boolean
        get() = name.isNotBlank() && name.trim().length <= MAX_GROUP_NAME && !isCreating
}

/** Name a group and pick friends for it, with a live "n/10" counter. */
class CreateGroupViewModel(
    private val friends: FriendRepository,
    private val groups: GroupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiStateFlow: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()
    val uiState: CreateGroupUiState
        get() = _uiState.value

    init {
        loadFriends()
    }

    fun loadFriends() {
        _uiState.update { it.copy(loadingFriends = true, friendsError = null) }
        viewModelScope.launch {
            try {
                val list = friends.friends()
                _uiState.update { it.copy(friends = list, loadingFriends = false) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(loadingFriends = false, friendsError = exception.toGroupMessage()) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name.take(MAX_GROUP_NAME)) }
    }

    /** Picks or unpicks a friend; picking stops once the group would be full. */
    fun toggle(friend: UserSummary) {
        _uiState.update { state ->
            when {
                friend.id in state.selected -> state.copy(selected = state.selected - friend.id)
                state.isFull -> state.copy(message = fullMessage())
                else -> state.copy(selected = state.selected + friend.id)
            }
        }
    }

    fun create() {
        val state = uiState
        if (!state.canCreate) return
        _uiState.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                // Keep the order the friends are listed in, so the group lists them the same way.
                val memberIds = state.friends.map { it.id }.filter { it in state.selected }
                val group = groups.create(state.name.trim(), memberIds)
                _uiState.update { it.copy(isCreating = false, createdGroupId = group.id) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isCreating = false, message = exception.toGroupMessage()) }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }
}

package com.example.movielist.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Group
import com.example.movielist.domain.model.GroupMatch
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.FriendRepository
import com.example.movielist.domain.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupUiState(
    val group: Group? = null,
    val matches: List<GroupMatch> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Members with a removal in flight. */
    val busy: Set<String> = emptySet(),
    /** Friends that could be added, while the "add friends" picker is open; null when closed. */
    val addable: List<UserSummary>? = null,
    val loadingAddable: Boolean = false,
    /** Waiting for the owner to confirm removing this member. */
    val confirmRemove: UserSummary? = null,
    val confirmLeave: Boolean = false,
    /** The new name being typed, while renaming. */
    val nameDraft: String? = null,
    /** Set once the user has left; the screen then goes back. */
    val left: Boolean = false,
    val message: String? = null,
)

/** One group: its members, the movies everyone likes, and adding, removing and leaving. */
class GroupViewModel(
    private val groups: GroupRepository,
    private val friends: FriendRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupUiState())
    val uiStateFlow: StateFlow<GroupUiState> = _uiState.asStateFlow()
    val uiState: GroupUiState
        get() = _uiState.value

    private var groupId: String? = null

    /** Called each time the screen opens, since members and their likes change elsewhere. */
    fun load(groupId: String) {
        this.groupId = groupId
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val group = async { groups.group(groupId) }
                    val matches = async { groups.matches(groupId) }
                    _uiState.update { it.copy(group = group.await(), matches = matches.await(), isLoading = false) }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, error = exception.toGroupMessage()) }
            }
        }
    }

    fun retry() {
        groupId?.let(::load)
    }

    /** Opens the picker with the friends who aren't in the group yet. */
    fun startAdding() {
        val group = uiState.group ?: return
        if (group.isFull) {
            _uiState.update { it.copy(message = fullMessage()) }
            return
        }
        _uiState.update { it.copy(addable = emptyList(), loadingAddable = true) }
        viewModelScope.launch {
            try {
                val inGroup = group.members.map { it.id }.toSet()
                val list = friends.friends().filterNot { it.id in inGroup }
                _uiState.update { if (it.addable == null) it else it.copy(addable = list, loadingAddable = false) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(addable = null, loadingAddable = false, message = exception.toGroupMessage()) }
            }
        }
    }

    fun stopAdding() {
        _uiState.update { it.copy(addable = null, loadingAddable = false) }
    }

    fun add(friend: UserSummary) {
        val group = uiState.group ?: return
        if (friend.id in uiState.busy) return
        _uiState.update { it.copy(busy = it.busy + friend.id) }
        viewModelScope.launch {
            try {
                val updated = groups.addMember(group.id, friend.id)
                _uiState.update { state ->
                    val remaining = state.addable?.filterNot { it.id == friend.id }
                    state.copy(
                        group = updated,
                        busy = state.busy - friend.id,
                        // Nothing more to add once it's full or everyone's in.
                        addable = remaining?.takeUnless { updated.isFull || it.isEmpty() },
                    )
                }
                // A new member can break a match, so the shared movies are fetched again.
                refreshMatches(group.id)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(busy = it.busy - friend.id, message = exception.toGroupMessage()) }
            }
        }
    }

    fun askRemove(member: UserSummary) {
        _uiState.update { it.copy(confirmRemove = member) }
    }

    fun cancelRemove() {
        _uiState.update { it.copy(confirmRemove = null) }
    }

    fun confirmRemove() {
        val group = uiState.group ?: return
        val member = uiState.confirmRemove ?: return
        _uiState.update { it.copy(confirmRemove = null, busy = it.busy + member.id) }
        viewModelScope.launch {
            try {
                val updated = groups.removeMember(group.id, member.id)
                _uiState.update { it.copy(group = updated, busy = it.busy - member.id) }
                refreshMatches(group.id)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(busy = it.busy - member.id, message = exception.toGroupMessage()) }
            }
        }
    }

    fun askLeave() {
        _uiState.update { it.copy(confirmLeave = true) }
    }

    fun cancelLeave() {
        _uiState.update { it.copy(confirmLeave = false) }
    }

    fun confirmLeave(myId: String) = exit(myId) { groups.leave(it, myId) }

    /** Owner only: deletes the group for everyone, then goes back like leaving does. */
    fun confirmDisband(myId: String) = exit(myId) { groups.disband(it) }

    private fun exit(myId: String, call: suspend (groupId: String) -> Unit) {
        val group = uiState.group ?: return
        _uiState.update { it.copy(confirmLeave = false, busy = it.busy + myId) }
        viewModelScope.launch {
            try {
                call(group.id)
                _uiState.update { it.copy(busy = it.busy - myId, left = true) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(busy = it.busy - myId, message = exception.toGroupMessage()) }
            }
        }
    }

    fun startRename() {
        val group = uiState.group ?: return
        _uiState.update { it.copy(nameDraft = group.name) }
    }

    fun onNameDraftChange(name: String) {
        _uiState.update { it.copy(nameDraft = name.take(MAX_GROUP_NAME)) }
    }

    fun cancelRename() {
        _uiState.update { it.copy(nameDraft = null) }
    }

    fun saveRename() {
        val group = uiState.group ?: return
        val name = uiState.nameDraft?.trim().orEmpty()
        if (name.isEmpty()) return
        if (name == group.name) {
            cancelRename()
            return
        }
        _uiState.update { it.copy(nameDraft = null, group = group.copy(name = name)) }
        viewModelScope.launch {
            try {
                val updated = groups.rename(group.id, name)
                _uiState.update { it.copy(group = updated) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Put the old name back, unless something newer replaced the group meanwhile.
                _uiState.update { state ->
                    val current = state.group
                    state.copy(
                        group = if (current?.name == name) current.copy(name = group.name) else current,
                        message = exception.toGroupMessage(),
                    )
                }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun refreshMatches(groupId: String) {
        try {
            val matches = groups.matches(groupId)
            _uiState.update { it.copy(matches = matches) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The old list stays; the next visit loads it again.
        }
    }
}

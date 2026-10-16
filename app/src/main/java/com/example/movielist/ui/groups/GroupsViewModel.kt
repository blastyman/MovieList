package com.example.movielist.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Group
import com.example.movielist.domain.repository.GroupRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** False until the first load finishes, so "no groups yet" doesn't flash. */
    val loaded: Boolean = false,
)

/** The list of groups the signed-in user is in. */
class GroupsViewModel(private val repository: GroupRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiStateFlow: StateFlow<GroupsUiState> = _uiState.asStateFlow()
    val uiState: GroupsUiState
        get() = _uiState.value

    /** Called each time the screen opens: groups change from other screens and other people. */
    fun load() {
        if (uiState.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val groups = repository.groups()
                _uiState.update { it.copy(groups = groups, isLoading = false, loaded = true) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, error = exception.toUserMessage()) }
            }
        }
    }
}

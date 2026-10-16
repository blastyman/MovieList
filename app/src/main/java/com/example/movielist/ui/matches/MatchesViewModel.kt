package com.example.movielist.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.repository.MatchRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MatchesUiState(
    val matches: List<Match> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** False until the first load finishes, so "no matches yet" doesn't flash. */
    val loaded: Boolean = false,
)

class MatchesViewModel(private val repository: MatchRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchesUiState())
    val uiStateFlow: StateFlow<MatchesUiState> = _uiState.asStateFlow()
    val uiState: MatchesUiState
        get() = _uiState.value

    /** Called each time the screen opens: matches change whenever you or a friend likes something. */
    fun load() {
        if (uiState.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val matches = repository.matches()
                _uiState.update { it.copy(matches = matches, isLoading = false, loaded = true) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, error = exception.toUserMessage()) }
            }
        }
    }
}

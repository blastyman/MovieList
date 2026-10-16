package com.example.movielist.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.ChatEvent
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.repository.ChatConnection
import com.example.movielist.domain.repository.ChatRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatsUiState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** False until the first load finishes, so "no chats yet" doesn't flash. */
    val loaded: Boolean = false,
)

/** Your chats, most recently active first; refreshed when a message arrives live. */
class ChatsViewModel(
    private val chats: ChatRepository,
    connection: ChatConnection,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiStateFlow: StateFlow<ChatsUiState> = _uiState.asStateFlow()
    val uiState: ChatsUiState
        get() = _uiState.value

    /** A message came in while a load was running; load once more afterwards. */
    private var reloadAfter = false

    init {
        viewModelScope.launch {
            connection.events.collect { event ->
                // The server has the right order and unread counts; ask it again.
                if (event is ChatEvent.MessageReceived) load()
            }
        }
    }

    fun load() {
        if (uiState.isLoading) {
            reloadAfter = true
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = chats.conversations()
                _uiState.update { it.copy(conversations = list, isLoading = false, loaded = true) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, error = exception.toUserMessage()) }
            }
            if (reloadAfter) {
                reloadAfter = false
                load()
            }
        }
    }
}

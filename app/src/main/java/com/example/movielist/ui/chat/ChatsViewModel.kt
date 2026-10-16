package com.example.movielist.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.ChatEvent
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.Message
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

/**
 * Your chats, most recently active first. A message arriving live updates its chat in place
 * (last message, unread count, moved to the top) instead of fetching the whole list again;
 * only a chat the list doesn't have yet makes it reload.
 */
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

    /** Who "you" are, so your own messages (from another device) don't count as unread. */
    private var myId: String? = null

    init {
        viewModelScope.launch {
            connection.events.collect { event ->
                if (event is ChatEvent.MessageReceived) onMessage(event.message)
            }
        }
    }

    /** Opens the list for [myId]; called each time the screen opens. */
    fun start(myId: String?) {
        this.myId = myId
        load()
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

    private fun onMessage(message: Message) {
        val current = uiState.conversations
        val chat = current.firstOrNull { it.id == message.conversationId }
        if (chat == null) {
            if (uiState.loaded) load() // a new chat, or one we haven't seen
            return
        }
        if (chat.lastMessage?.id == message.id) return // already shown
        val updated = chat.copy(
            lastMessage = message,
            updatedAt = message.createdAt,
            unreadCount = chat.unreadCount + if (message.senderId == myId) 0 else 1,
        )
        _uiState.update { it.copy(conversations = listOf(updated) + current.filterNot { c -> c.id == chat.id }) }
    }
}

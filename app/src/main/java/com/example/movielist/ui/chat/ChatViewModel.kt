package com.example.movielist.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.ChatEvent
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.Message
import com.example.movielist.domain.repository.ChatConnection
import com.example.movielist.domain.repository.ChatRepository
import com.example.movielist.ui.utils.toUserMessage
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One bubble: a saved message, or one of yours still on its way (or that failed to go). */
sealed interface ChatItem {
    val key: String
    val body: String
    val createdAt: Instant

    data class Sent(val message: Message) : ChatItem {
        override val key get() = message.id
        override val body get() = message.body
        override val createdAt get() = message.createdAt
    }

    data class Pending(val clientId: String, override val body: String, override val createdAt: Instant, val failed: Boolean = false) :
        ChatItem {
        override val key get() = clientId
    }
}

data class ChatUiState(
    val conversation: Conversation? = null,
    /** Newest first, the order the list draws them in (bottom up). */
    val items: List<ChatItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Where older messages continue; null once the start of the chat is loaded. */
    val olderCursor: String? = null,
    val loadingOlder: Boolean = false,
    val draft: String = "",
    /** Ids of the others typing right now. */
    val typing: Set<String> = emptySet(),
    val message: String? = null,
)

/**
 * One chat. Your messages show at once as pending bubbles with a temporary client id; the
 * server's copy replaces them when it comes back (over the live connection, or from the REST
 * fallback when that's down). Messages from others arrive live and mark the chat read.
 */
class ChatViewModel(
    private val chats: ChatRepository,
    private val connection: ChatConnection,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiStateFlow: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val uiState: ChatUiState
        get() = _uiState.value

    /** Whether the live connection is up; while it isn't, messages go over plain HTTP. */
    val isConnected: StateFlow<Boolean> = connection.isConnected

    private var myId: String? = null
    private var lastTypingSent = Instant.EPOCH
    private val typingTimeouts = mutableMapOf<String, Job>()
    private val sendTimeouts = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch { connection.events.collect(::onEvent) }
    }

    /** Opens a chat by id: a group's, or one from the chats list. */
    fun open(conversationId: String, myId: String) = start(myId) { chats.conversation(conversationId) }

    /** Opens the one-to-one chat with a friend, starting it if needed. */
    fun openDirect(userId: String, myId: String) = start(myId) { chats.startDirect(userId) }

    private fun start(myId: String, find: suspend () -> Conversation) {
        if (this.myId != null) return // already open; this ViewModel belongs to one chat
        this.myId = myId
        load(find)
    }

    fun retry() {
        val id = uiState.conversation?.id
        if (id != null) load { chats.conversation(id) }
    }

    private fun load(find: suspend () -> Conversation) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val conversation = find()
                val page = chats.messages(conversation.id)
                _uiState.update { state ->
                    // Keep anything that arrived live while loading.
                    val loaded = page.items.map { ChatItem.Sent(it) }
                    val newer = state.items.filter { item -> loaded.none { it.key == item.key } }
                    state.copy(
                        conversation = conversation,
                        items = newer + loaded,
                        olderCursor = page.nextCursor,
                        isLoading = false,
                    )
                }
                markRead()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, error = exception.toUserMessage()) }
            }
        }
    }

    fun loadOlder() {
        val state = uiState
        val conversation = state.conversation ?: return
        val cursor = state.olderCursor ?: return
        if (state.loadingOlder) return
        _uiState.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            try {
                val page = chats.messages(conversation.id, cursor)
                _uiState.update { it.copy(items = it.items + page.items.map { m -> ChatItem.Sent(m) }, olderCursor = page.nextCursor, loadingOlder = false) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(loadingOlder = false, message = exception.toUserMessage()) }
            }
        }
    }

    fun onDraftChange(text: String) {
        _uiState.update { it.copy(draft = text.take(MAX_LENGTH)) }
        val conversation = uiState.conversation ?: return
        val now = clock.instant()
        // At most one "typing" every few seconds, not one per key press.
        if (text.isNotBlank() && now.isAfter(lastTypingSent.plusMillis(TYPING_EVERY_MS))) {
            lastTypingSent = now
            connection.typing(conversation.id)
        }
    }

    fun send() {
        val body = uiState.draft.trim()
        if (body.isEmpty() || uiState.conversation == null) return
        _uiState.update { it.copy(draft = "") }
        lastTypingSent = Instant.EPOCH
        deliver(body)
    }

    /** Sends a failed message again. */
    fun retrySend(clientId: String) {
        val pending = uiState.items.firstOrNull { it is ChatItem.Pending && it.clientId == clientId } ?: return
        _uiState.update { state -> state.copy(items = state.items.filterNot { it.key == clientId }) }
        deliver(pending.body)
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun isMine(item: ChatItem): Boolean = item is ChatItem.Pending || (item as ChatItem.Sent).message.senderId == myId

    private fun deliver(body: String) {
        val conversationId = uiState.conversation?.id ?: return
        val clientId = "c-" + UUID.randomUUID()
        _uiState.update { it.copy(items = listOf(ChatItem.Pending(clientId, body, clock.instant())) + it.items) }

        if (connection.send(conversationId, body, clientId)) {
            // The echo normally comes back in well under a second; if it never does, say so.
            sendTimeouts[clientId] = viewModelScope.launch {
                delay(SEND_TIMEOUT_MS)
                markFailed(clientId)
            }
        } else {
            viewModelScope.launch {
                try {
                    confirm(clientId, chats.send(conversationId, body))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    markFailed(clientId)
                }
            }
        }
    }

    private fun onEvent(event: ChatEvent) {
        val conversationId = uiState.conversation?.id
        when (event) {
            is ChatEvent.MessageReceived -> {
                if (event.message.conversationId != conversationId) return
                val clientId = event.clientId
                if (clientId != null && uiState.items.any { it.key == clientId }) {
                    confirm(clientId, event.message)
                } else {
                    add(event.message)
                }
                if (event.message.senderId != myId) {
                    stopTyping(event.message.senderId) // they sent it, so they're done typing
                    markRead()
                }
            }
            is ChatEvent.Typing -> {
                if (event.conversationId != conversationId || event.userId == myId) return
                _uiState.update { it.copy(typing = it.typing + event.userId) }
                typingTimeouts.remove(event.userId)?.cancel()
                typingTimeouts[event.userId] = viewModelScope.launch {
                    delay(TYPING_SHOWN_MS)
                    stopTyping(event.userId)
                }
            }
            is ChatEvent.SendFailed -> event.clientId?.let(::markFailed)
        }
    }

    /** Replaces the pending bubble with the saved message (or drops it if that's already shown). */
    private fun confirm(clientId: String, message: Message) {
        sendTimeouts.remove(clientId)?.cancel()
        _uiState.update { state ->
            val alreadyShown = state.items.any { it.key == message.id }
            state.copy(
                items =
                    if (alreadyShown) state.items.filterNot { it.key == clientId }
                    else state.items.map { if (it.key == clientId) ChatItem.Sent(message) else it },
            )
        }
    }

    private fun add(message: Message) {
        _uiState.update { state ->
            if (state.items.any { it.key == message.id }) state
            else state.copy(items = listOf(ChatItem.Sent(message)) + state.items)
        }
    }

    private fun markFailed(clientId: String) {
        sendTimeouts.remove(clientId)?.cancel()
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it is ChatItem.Pending && it.clientId == clientId) it.copy(failed = true) else it })
        }
    }

    private fun stopTyping(userId: String) {
        typingTimeouts.remove(userId)?.cancel()
        _uiState.update { it.copy(typing = it.typing - userId) }
    }

    private fun markRead() {
        val conversationId = uiState.conversation?.id ?: return
        if (connection.isConnected.value) {
            connection.sendRead(conversationId)
        } else {
            viewModelScope.launch {
                try {
                    chats.markRead(conversationId)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    // Only the unread badge is affected; the next open marks it read again.
                }
            }
        }
    }

    companion object {
        const val MAX_LENGTH = 2000
        private const val TYPING_EVERY_MS = 3_000L
        private const val TYPING_SHOWN_MS = 4_000L
        private const val SEND_TIMEOUT_MS = 10_000L
    }
}

package com.example.movielist.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movielist.domain.model.ChatEvent
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.ConversationType
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.Message
import com.example.movielist.domain.model.MessagePage
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.ChatConnection
import com.example.movielist.domain.repository.ChatRepository
import com.example.movielist.ui.theme.MovieListTheme
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** One empty chat with Ben; the live connection records what was sent. */
private class FakeChat : ChatRepository, ChatConnection {
    private val ben = UserSummary("ben", "ben", "Ben", FriendshipState.FRIENDS)
    private val conversation =
        Conversation("c1", ConversationType.DIRECT, "Ben", null, listOf(ben), null, 0, Instant.EPOCH)
    val sent = mutableListOf<String>()

    override val events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    override val isConnected = MutableStateFlow(true)

    override suspend fun conversations() = listOf(conversation)

    override suspend fun conversation(conversationId: String) = conversation

    override suspend fun startDirect(userId: String) = conversation

    override suspend fun messages(conversationId: String, before: String?) = MessagePage(emptyList(), null)

    override suspend fun send(conversationId: String, body: String) =
        Message("m1", conversationId, "me", body, Instant.now())

    override suspend fun markRead(conversationId: String) = Unit

    override fun send(conversationId: String, body: String, clientId: String): Boolean {
        sent += body
        return true
    }

    override fun typing(conversationId: String) = Unit

    override fun sendRead(conversationId: String) = Unit
}

class ChatScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun sendingAMessageShowsItAndClearsTheBox() {
        val chat = FakeChat()
        val viewModel = ChatViewModel(chat, chat).apply { open("c1", "me") }
        composeRule.setContent {
            MovieListTheme {
                val state by viewModel.uiStateFlow.collectAsStateWithLifecycle()
                ChatScreen(
                    state = state,
                    isConnected = true,
                    isMine = viewModel::isMine,
                    onBack = {},
                    onTitleClick = {},
                    onRetry = {},
                    onLoadOlder = {},
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send,
                    onRetrySend = viewModel::retrySend,
                )
            }
        }

        composeRule.onNodeWithText("No messages yet. Say hi!").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send").assertIsNotEnabled() // nothing to send yet

        composeRule.onNodeWithText("Message…").performTextInput("Popcorn at 8?")
        composeRule.onNodeWithContentDescription("Send").performClick()

        composeRule.onNodeWithText("Popcorn at 8?").assertIsDisplayed()
        assertEquals(listOf("Popcorn at 8?"), chat.sent)
        composeRule.onNodeWithText("Message…").assertIsDisplayed() // the box is empty again
    }
}

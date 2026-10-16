package com.example.movielist.ui.chat

import com.example.movielist.domain.model.ChatEvent
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.ConversationType
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.Message
import com.example.movielist.domain.model.MessagePage
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.ChatConnection
import com.example.movielist.domain.repository.ChatRepository
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A chat server in memory: REST calls and the live connection both. */
private class FakeChat : ChatRepository, ChatConnection {
    val ben = UserSummary("ben", "ben", null, FriendshipState.FRIENDS)
    val conversation =
        Conversation("c1", ConversationType.DIRECT, "ben", null, listOf(ben), null, 0, Instant.EPOCH)
    val history = mutableListOf<Message>() // newest first
    val sentLive = mutableListOf<Pair<String, String>>() // body to clientId
    val restSent = mutableListOf<String>()
    var markedRead = 0
    var restFails = false
    private var nextId = 1

    override val events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 16)
    override val isConnected = MutableStateFlow(true)

    fun message(body: String, from: String = "ben", conversationId: String = "c1") =
        Message("m${nextId++}", conversationId, from, body, Instant.ofEpochSecond(nextId.toLong()))

    override suspend fun conversations() = listOf(conversation)

    override suspend fun conversation(conversationId: String) = conversation

    override suspend fun startDirect(userId: String) = conversation

    override suspend fun messages(conversationId: String, before: String?): MessagePage =
        if (before == null) MessagePage(history.take(2), if (history.size > 2) "older" else null)
        else MessagePage(history.drop(2), null)

    override suspend fun send(conversationId: String, body: String): Message {
        if (restFails) throw IOException("Offline")
        restSent += body
        return message(body, from = "me")
    }

    override suspend fun markRead(conversationId: String) {
        markedRead++
    }

    override fun send(conversationId: String, body: String, clientId: String): Boolean {
        if (!isConnected.value) return false
        sentLive += body to clientId
        return true
    }

    override fun typing(conversationId: String) = Unit

    override fun sendRead(conversationId: String) {
        markedRead++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val chat = FakeChat()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun bodies(viewModel: ChatViewModel) = viewModel.uiState.items.map { it.body }

    @Test
    fun opensWithTheNewestMessagesAndMarksThemRead() =
        runTest(dispatcher) {
            chat.history += listOf(chat.message("3"), chat.message("2"), chat.message("1"))
            val viewModel = ChatViewModel(chat, chat)

            viewModel.open("c1", "me")
            advanceUntilIdle()

            assertEquals(listOf("3", "2"), bodies(viewModel))
            assertEquals(1, chat.markedRead)

            viewModel.loadOlder()
            advanceUntilIdle()
            assertEquals(listOf("3", "2", "1"), bodies(viewModel))
            assertEquals(null, viewModel.uiState.olderCursor)
        }

    @Test
    fun aSentMessageShowsAtOnceAndIsReplacedByTheServersCopy() =
        runTest(dispatcher) {
            val viewModel = ChatViewModel(chat, chat)
            viewModel.open("c1", "me")
            advanceUntilIdle()

            viewModel.onDraftChange("  hello ")
            viewModel.send()

            assertEquals("", viewModel.uiState.draft)
            val pending = viewModel.uiState.items.single()
            assertTrue(pending is ChatItem.Pending)
            assertTrue(viewModel.isMine(pending))
            val (body, clientId) = chat.sentLive.single()
            assertEquals("hello", body)

            chat.events.emit(ChatEvent.MessageReceived(chat.message("hello", from = "me"), clientId))
            runCurrent()

            val saved = viewModel.uiState.items.single()
            assertTrue(saved is ChatItem.Sent)
            assertTrue(viewModel.isMine(saved))
        }

    @Test
    fun withoutALiveConnectionItSendsOverHttp() =
        runTest(dispatcher) {
            chat.isConnected.value = false
            val viewModel = ChatViewModel(chat, chat)
            viewModel.open("c1", "me")
            advanceUntilIdle()

            viewModel.onDraftChange("hi")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(listOf("hi"), chat.restSent)
            assertTrue(viewModel.uiState.items.single() is ChatItem.Sent)
        }

    @Test
    fun aFailedSendCanBeRetried() =
        runTest(dispatcher) {
            chat.isConnected.value = false
            chat.restFails = true
            val viewModel = ChatViewModel(chat, chat)
            viewModel.open("c1", "me")
            advanceUntilIdle()

            viewModel.onDraftChange("hi")
            viewModel.send()
            advanceUntilIdle()
            val failed = viewModel.uiState.items.single() as ChatItem.Pending
            assertTrue(failed.failed)

            chat.restFails = false
            viewModel.retrySend(failed.clientId)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.items.single() is ChatItem.Sent)
        }

    @Test
    fun aLiveSendWithNoAnswerTurnsFailedAfterAWhile() =
        runTest(dispatcher) {
            val viewModel = ChatViewModel(chat, chat)
            viewModel.open("c1", "me")
            advanceUntilIdle()

            viewModel.onDraftChange("anyone?")
            viewModel.send()
            advanceTimeBy(9_000)
            assertEquals(false, (viewModel.uiState.items.single() as ChatItem.Pending).failed)
            advanceTimeBy(2_000)
            assertEquals(true, (viewModel.uiState.items.single() as ChatItem.Pending).failed)
        }

    @Test
    fun theirMessagesAndTypingShowUpLive() =
        runTest(dispatcher) {
            val viewModel = ChatViewModel(chat, chat)
            viewModel.open("c1", "me")
            advanceUntilIdle()
            val readBefore = chat.markedRead

            chat.events.emit(ChatEvent.Typing("c1", "ben"))
            runCurrent()
            assertEquals(setOf("ben"), viewModel.uiState.typing)

            chat.events.emit(ChatEvent.MessageReceived(chat.message("hey"), null))
            chat.events.emit(ChatEvent.MessageReceived(chat.message("elsewhere", conversationId = "c2"), null))
            runCurrent()

            assertEquals(listOf("hey"), bodies(viewModel)) // the other chat's message isn't shown
            assertTrue(viewModel.uiState.typing.isEmpty()) // sending ends typing
            assertEquals(readBefore + 1, chat.markedRead)
        }

    @Test
    fun typingFadesOnItsOwn() =
        runTest(dispatcher) {
            val viewModel = ChatViewModel(chat, chat)
            viewModel.open("c1", "me")
            advanceUntilIdle()

            chat.events.emit(ChatEvent.Typing("c1", "ben"))
            runCurrent()
            advanceTimeBy(5_000)
            assertTrue(viewModel.uiState.typing.isEmpty())
        }

    @Test
    fun theChatsListReloadsWhenAMessageArrives() =
        runTest(dispatcher) {
            val viewModel = ChatsViewModel(chat, chat)
            viewModel.load()
            advanceUntilIdle()
            assertEquals(listOf("c1"), viewModel.uiState.conversations.map { it.id })

            chat.history.clear()
            chat.events.emit(ChatEvent.MessageReceived(chat.message("new"), null))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.loaded)
            assertEquals(false, viewModel.uiState.isLoading)
        }
}

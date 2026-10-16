package com.example.movielist.backend

import com.example.movielist.backend.features.chat.ClientFrame
import com.example.movielist.backend.features.chat.ConversationDto
import com.example.movielist.backend.features.chat.MessageDto
import com.example.movielist.backend.features.chat.MessagePageDto
import com.example.movielist.backend.features.chat.SendMessageRequest
import com.example.movielist.backend.features.chat.ServerFrame
import com.example.movielist.backend.features.chat.StartDirectRequest
import com.example.movielist.backend.features.groups.CreateGroupRequest
import com.example.movielist.backend.features.groups.GroupDto
import com.example.movielist.backend.plugins.AppJson
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatTest {

    private suspend fun HttpClient.startDirect(me: TestUser, other: TestUser): HttpResponse =
        post("/conversations/direct") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(StartDirectRequest(other.id))
        }

    private suspend fun HttpClient.sendRest(me: TestUser, conversationId: String, body: String): HttpResponse =
        post("/conversations/$conversationId/messages") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(body))
        }

    private suspend fun HttpClient.conversations(me: TestUser): List<ConversationDto> =
        get("/conversations") { bearerAuth(me.token) }.body()

    private suspend fun HttpClient.history(me: TestUser, conversationId: String, before: String? = null, limit: Int? = null) =
        get("/conversations/$conversationId/messages") {
            bearerAuth(me.token)
            before?.let { url.parameters.append("before", it) }
            limit?.let { url.parameters.append("limit", it.toString()) }
        }

    private suspend fun HttpClient.friends(): Pair<TestUser, TestUser> {
        val a = registerUser("anna")
        val b = registerUser("ben")
        befriend(a, b)
        return a to b
    }

    private fun ApplicationTestBuilder.socketClient() = createClient { install(WebSockets) }

    private suspend fun DefaultClientWebSocketSession.sendFrame(frame: ClientFrame) =
        send(Frame.Text(AppJson.encodeToString(ClientFrame.serializer(), frame)))

    private suspend fun DefaultClientWebSocketSession.nextFrame(): ServerFrame = withTimeout(5_000) {
        val frame = incoming.receive() as Frame.Text
        AppJson.decodeFromString(ServerFrame.serializer(), frame.readText())
    }

    @Test
    fun `friends get one direct chat, whoever starts it`() = apiTest { client ->
        val (anna, ben) = client.friends()

        val first = client.startDirect(anna, ben).body<ConversationDto>()
        val second = client.startDirect(ben, anna).body<ConversationDto>()

        assertEquals(first.id, second.id)
        assertEquals("DIRECT", first.type)
        assertEquals(ben.username, first.title) // the other person
        assertEquals(anna.username, second.title)
        assertEquals(listOf(ben.id), first.members.map { it.id })
        assertNull(first.lastMessage)
    }

    @Test
    fun `strangers and yourself can't be messaged directly`() = apiTest { client ->
        val anna = client.registerUser("anna")
        val stranger = client.registerUser("stranger")

        val response = client.startDirect(anna, stranger)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("NOT_FRIENDS", response.body<ErrorResponse>().error)
        assertEquals(HttpStatusCode.BadRequest, client.startDirect(anna, anna).status)
    }

    @Test
    fun `history pages newest first and unread counts follow reading`() = apiTest { client ->
        val (anna, ben) = client.friends()
        val chat = client.startDirect(anna, ben).body<ConversationDto>()
        repeat(5) { client.sendRest(anna, chat.id, "message $it") }

        val benSees = client.conversations(ben).single()
        assertEquals(5, benSees.unreadCount)
        assertEquals("message 4", benSees.lastMessage?.body)
        assertEquals(0, client.conversations(anna).single().unreadCount) // your own messages are read

        val page = client.history(ben, chat.id, limit = 3).body<MessagePageDto>()
        assertEquals(listOf("message 4", "message 3", "message 2"), page.items.map { it.body })
        val older = client.history(ben, chat.id, before = page.nextCursor, limit = 3).body<MessagePageDto>()
        assertEquals(listOf("message 1", "message 0"), older.items.map { it.body })
        assertNull(older.nextCursor)

        client.post("/conversations/${chat.id}/read") { bearerAuth(ben.token) }
        assertEquals(0, client.conversations(ben).single().unreadCount)
        client.sendRest(anna, chat.id, "one more")
        assertEquals(1, client.conversations(ben).single().unreadCount)
    }

    @Test
    fun `messages are trimmed and limited`() = apiTest { client ->
        val (anna, ben) = client.friends()
        val chat = client.startDirect(anna, ben).body<ConversationDto>()

        assertEquals("hi", client.sendRest(anna, chat.id, "  hi \n").body<MessageDto>().body)
        assertEquals(HttpStatusCode.BadRequest, client.sendRest(anna, chat.id, "   ").status)
        assertEquals(HttpStatusCode.BadRequest, client.sendRest(anna, chat.id, "x".repeat(2001)).status)
    }

    @Test
    fun `outsiders can't read or write, and unfriending stops new direct messages`() = apiTest { client ->
        val (anna, ben) = client.friends()
        val outsider = client.registerUser("outsider")
        val chat = client.startDirect(anna, ben).body<ConversationDto>()
        client.sendRest(anna, chat.id, "before")

        assertEquals(HttpStatusCode.NotFound, client.history(outsider, chat.id).status)
        assertEquals(HttpStatusCode.NotFound, client.sendRest(outsider, chat.id, "hi").status)

        client.delete("/friends/${ben.id}") { bearerAuth(anna.token) }
        assertEquals(HttpStatusCode.Forbidden, client.sendRest(ben, chat.id, "after").status)
        assertEquals(listOf("before"), client.history(ben, chat.id).body<MessagePageDto>().items.map { it.body })
    }

    @Test
    fun `a group's chat follows its members`() = apiTest { client ->
        val (anna, ben) = client.friends()
        val group = client.post("/groups") {
            bearerAuth(anna.token)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest("Movie night", listOf(ben.id)))
        }.body<GroupDto>()

        val chat = client.conversations(ben).single()
        assertEquals("GROUP", chat.type)
        assertEquals("Movie night", chat.title)
        assertEquals(group.id, chat.groupId)
        assertEquals(group.conversationId, chat.id)
        assertEquals(HttpStatusCode.Created, client.sendRest(ben, chat.id, "popcorn?").status)

        client.delete("/groups/${group.id}/members/${ben.id}") { bearerAuth(ben.token) } // ben leaves
        assertTrue(client.conversations(ben).isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.sendRest(ben, chat.id, "still here?").status)
    }

    @Test
    fun `messages and typing reach the other person live`() = apiTest { client ->
        val (anna, ben) = client.friends()
        val chat = client.startDirect(anna, ben).body<ConversationDto>()
        val sockets = socketClient()
        val benConnected = CompletableDeferred<Unit>()

        coroutineScope {
            val benReceived = async {
                var frames = emptyList<ServerFrame>()
                sockets.webSocket("/ws", request = { bearerAuth(ben.token) }) {
                    // The server answers frames only once it has registered this socket, so an
                    // answered (bad) frame proves ben will get what anna sends next.
                    send(Frame.Text("{}"))
                    assertIs<ServerFrame.Error>(nextFrame())
                    benConnected.complete(Unit)
                    frames = listOf(nextFrame(), nextFrame())
                }
                frames
            }
            benConnected.await()

            sockets.webSocket("/ws", request = { bearerAuth(anna.token) }) {
                sendFrame(ClientFrame.Typing(chat.id))
                sendFrame(ClientFrame.Send(chat.id, "hello", clientId = "tmp-1"))

                // Anna gets her own message back with her clientId, so her app can swap the bubble.
                val echo = assertIs<ServerFrame.Message>(nextFrame())
                assertEquals("hello", echo.message.body)
                assertEquals("tmp-1", echo.clientId)
            }

            val (typing, message) = benReceived.await()
            assertEquals(anna.id, assertIs<ServerFrame.Typing>(typing).userId)
            assertEquals("hello", assertIs<ServerFrame.Message>(message).message.body)
        }
        // Saved before it was pushed.
        assertEquals(listOf("hello"), client.history(ben, chat.id).body<MessagePageDto>().items.map { it.body })
    }

    @Test
    fun `bad frames and forbidden sends get an error frame, not a dropped connection`() = apiTest { client ->
        val (anna, ben) = client.friends()
        val chat = client.startDirect(anna, ben).body<ConversationDto>()
        val strangersChat = client.friends().let { (c, d) -> client.startDirect(c, d).body<ConversationDto>() }

        socketClient().webSocket("/ws", request = { bearerAuth(anna.token) }) {
            send(Frame.Text("""{"type":"dance"}"""))
            assertEquals("BAD_FRAME", assertIs<ServerFrame.Error>(nextFrame()).error)

            sendFrame(ClientFrame.Send(strangersChat.id, "let me in", clientId = "tmp-9"))
            val denied = assertIs<ServerFrame.Error>(nextFrame())
            assertEquals("NOT_FOUND", denied.error)
            assertEquals("tmp-9", denied.clientId)

            // Still connected and working.
            sendFrame(ClientFrame.Send(chat.id, "still here", clientId = "tmp-10"))
            assertEquals("tmp-10", assertIs<ServerFrame.Message>(nextFrame()).clientId)
        }
    }

    @Test
    fun `the socket and chat endpoints need a login`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/conversations").status)
        // A real WebSocket handshake, just without a token, is turned away (the same call with
        // a token connects in the tests above).
        assertFails { socketClient().webSocket("/ws") {} }
    }
}

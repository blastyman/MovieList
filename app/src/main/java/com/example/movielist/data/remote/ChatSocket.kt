package com.example.movielist.data.remote

import com.example.movielist.domain.model.ChatEvent
import com.example.movielist.domain.model.Message
import com.example.movielist.domain.repository.ChatConnection
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The chat's live connection: one OkHttp WebSocket to `/ws`, opened while the app is in the
 * foreground and logged in ([connect] / [disconnect]). It goes through the same OkHttp client
 * as every other call, so the login token rides along in the handshake header. When the
 * connection drops it reconnects after 1 s, 2 s, 4 s … up to 30 s, with a little jitter so many
 * phones don't all retry at once.
 */
class ChatSocket(
    httpClient: OkHttpClient,
    baseUrl: String,
    private val scope: CoroutineScope,
) : ChatConnection {
    // No read timeout: the socket sits idle between messages; pings notice a dead connection.
    private val client = httpClient.newBuilder().readTimeout(0, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    private val request = Request.Builder().url(baseUrl.trimEnd('/') + "/ws").build()
    private val gson = Gson()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: WebSocket? = null
    private var wanted = false
    private var attempt = 0
    private var reconnect: Job? = null

    @Synchronized
    fun connect() {
        if (wanted) return
        wanted = true
        attempt = 0
        open()
    }

    @Synchronized
    fun disconnect() {
        wanted = false
        reconnect?.cancel()
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        _isConnected.value = false
    }

    override fun send(conversationId: String, body: String, clientId: String): Boolean =
        isConnected.value &&
            sendFrame(mapOf("type" to "send", "conversationId" to conversationId, "body" to body, "clientId" to clientId))

    override fun typing(conversationId: String) {
        if (isConnected.value) sendFrame(mapOf("type" to "typing", "conversationId" to conversationId))
    }

    override fun sendRead(conversationId: String) {
        if (isConnected.value) sendFrame(mapOf("type" to "read", "conversationId" to conversationId))
    }

    private fun sendFrame(frame: Map<String, String>): Boolean = socket?.send(gson.toJson(frame)) == true

    private fun open() {
        socket = client.newWebSocket(request, Listener())
    }

    @Synchronized
    private fun dropped(webSocket: WebSocket, response: Response?) {
        if (webSocket !== socket) return // an old socket, already replaced or closed on purpose
        socket = null
        _isConnected.value = false
        // A rejected token ends the session (the HTTP client reports it); retrying won't help.
        if (response?.code == 401) wanted = false
        if (!wanted) return
        val wait = minOf(MAX_BACKOFF_MS, FIRST_BACKOFF_MS shl minOf(attempt, 5)) + Random.nextLong(JITTER_MS)
        attempt++
        reconnect = scope.launch {
            delay(wait)
            synchronized(this@ChatSocket) { if (wanted && socket == null) open() }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(this@ChatSocket) {
                if (webSocket !== socket) return
                attempt = 0
                _isConnected.value = true
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            parse(text)?.let { _events.tryEmit(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            dropped(webSocket, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            dropped(webSocket, response)
        }
    }

    /** A server frame as an event; unknown or broken frames are ignored. */
    private fun parse(text: String): ChatEvent? =
        runCatching {
            val frame = JsonParser.parseString(text).asJsonObject
            when (frame.string("type")) {
                "message" -> {
                    val message = frame.getAsJsonObject("message")
                    ChatEvent.MessageReceived(
                        Message(
                            id = message.string("id")!!,
                            conversationId = message.string("conversationId")!!,
                            senderId = message.string("senderId")!!,
                            body = message.string("body")!!,
                            createdAt = Instant.parse(message.string("createdAt")),
                        ),
                        clientId = frame.string("clientId"),
                    )
                }
                "typing" -> ChatEvent.Typing(frame.string("conversationId")!!, frame.string("userId")!!)
                "error" -> ChatEvent.SendFailed(frame.string("clientId"), frame.string("message") ?: "Couldn't send")
                else -> null
            }
        }.getOrNull()

    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val FIRST_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val JITTER_MS = 500L
    }
}

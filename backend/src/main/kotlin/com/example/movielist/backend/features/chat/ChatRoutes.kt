package com.example.movielist.backend.features.chat

import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.AppJson
import com.example.movielist.backend.plugins.ValidationException
import com.example.movielist.backend.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebSockets() {
    install(WebSockets) {
        // Pings keep idle connections open through proxies and notice dead phones.
        pingPeriod = 25.seconds
        timeout = 60.seconds
        maxFrameSize = 64 * 1024L
    }
}

fun Route.chatRoutes(chatService: ChatService, hub: ChatHub) {
    authenticate(AUTH_JWT) {
        route("/conversations") {
            get { call.respond(chatService.conversations(call.userId)) }
            post("/direct") {
                val body = call.receive<StartDirectRequest>()
                call.respond(chatService.startDirect(call.userId, parseUuid(body.userId, "User id")))
            }
            route("/{conversationId}") {
                get { call.respond(chatService.conversation(call.userId, call.conversationId())) }
                get("/messages") {
                    val params = call.request.queryParameters
                    val limit = params["limit"]?.let { it.toIntOrNull() ?: throw ValidationException("limit must be a number") }
                    call.respond(chatService.messages(call.userId, call.conversationId(), params["before"], limit))
                }
                // For when the WebSocket isn't connected; the message is pushed to others all the same.
                post("/messages") {
                    val body = call.receive<SendMessageRequest>()
                    call.respond(HttpStatusCode.Created, chatService.send(call.userId, call.conversationId(), body.body))
                }
                post("/read") {
                    chatService.read(call.userId, call.conversationId())
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        // The JWT comes in the handshake's Authorization header, like every other call, so it
        // never ends up in a URL (and in logs).
        webSocket("/ws") {
            val me = call.userId
            hub.join(me, this)
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) handle(me, frame.readText(), chatService)
                }
            } finally {
                hub.leave(me, this)
            }
        }
    }
}

/** One frame from the app. Errors go back as an error frame; the connection stays open. */
private suspend fun DefaultWebSocketServerSession.handle(me: UUID, text: String, chatService: ChatService) {
    val frame = try {
        AppJson.decodeFromString(ClientFrame.serializer(), text)
    } catch (e: SerializationException) {
        reply(ServerFrame.Error("BAD_FRAME", "Couldn't read that frame"))
        return
    } catch (e: IllegalArgumentException) {
        reply(ServerFrame.Error("BAD_FRAME", "Couldn't read that frame"))
        return
    }
    val clientId = (frame as? ClientFrame.Send)?.clientId
    try {
        when (frame) {
            is ClientFrame.Send -> chatService.send(me, parseUuid(frame.conversationId, "Conversation id"), frame.body, frame.clientId)
            is ClientFrame.Typing -> chatService.typing(me, parseUuid(frame.conversationId, "Conversation id"))
            is ClientFrame.Read -> chatService.read(me, parseUuid(frame.conversationId, "Conversation id"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        reply(ServerFrame.Error(e.code, e.message, clientId))
    } catch (e: Exception) {
        call.application.log.error("Chat frame failed", e)
        reply(ServerFrame.Error("INTERNAL_ERROR", "Something went wrong", clientId))
    }
}

private suspend fun DefaultWebSocketServerSession.reply(frame: ServerFrame) {
    send(Frame.Text(AppJson.encodeToString(ServerFrame.serializer(), frame)))
}

private fun RoutingCall.conversationId(): UUID = parseUuid(parameters["conversationId"], "Conversation id")

private fun parseUuid(raw: String?, what: String): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ValidationException("$what must be a UUID")

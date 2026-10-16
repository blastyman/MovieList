package com.example.movielist.backend.features.chat

import com.example.movielist.backend.plugins.AppJson
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who is online right now: each user's open WebSockets (a user can have several, e.g. two
 * phones). In memory only, so this works for one server process; several would need a shared
 * channel such as Postgres LISTEN/NOTIFY. Offline users just read the history later.
 */
class ChatHub {
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    fun join(userId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun leave(userId: UUID, session: WebSocketSession) {
        sessions.computeIfPresent(userId) { _, set -> set.apply { remove(session) }.ifEmpty { null } }
    }

    /** Pushes [frame] to every open WebSocket of [userIds]; a dead socket is skipped. */
    suspend fun send(userIds: Collection<UUID>, frame: ServerFrame) {
        val text = AppJson.encodeToString(ServerFrame.serializer(), frame)
        for (userId in userIds) {
            for (session in sessions[userId].orEmpty()) {
                try {
                    session.send(Frame.Text(text))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Closed meanwhile; leave() will drop it.
                }
            }
        }
    }
}

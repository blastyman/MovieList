package com.example.movielist.backend.features.chat

import com.example.movielist.backend.plugins.AppJson
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who is online right now: each user's open WebSockets (a user can have several, e.g. two
 * phones). In memory only, so this works for one server process; several would need a shared
 * channel such as Postgres LISTEN/NOTIFY. Offline users just read the history later.
 *
 * Every socket has its own queue, emptied in order by its own coroutine, so pushing a frame
 * never waits: a slow or stalled phone only delays itself, not the others or the sender. One
 * that stops answering is closed by the ping timeout, which also ends its queue.
 */
class ChatHub {
    private class Client(val session: WebSocketSession) {
        val queue = Channel<String>(Channel.UNLIMITED)
    }

    private val clients = ConcurrentHashMap<UUID, MutableSet<Client>>()

    fun join(userId: UUID, session: WebSocketSession) {
        val client = Client(session)
        session.launch {
            for (text in client.queue) {
                try {
                    session.send(Frame.Text(text))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    break // closed meanwhile; leave() drops it
                }
            }
        }
        // Added inside the map's atomic update, so a leave() at the same moment can't lose it.
        clients.compute(userId) { _, set -> (set ?: ConcurrentHashMap.newKeySet()).apply { add(client) } }
    }

    fun leave(userId: UUID, session: WebSocketSession) {
        clients.computeIfPresent(userId) { _, set ->
            set.filter { it.session === session }.forEach {
                it.queue.close()
                set.remove(it)
            }
            set.ifEmpty { null }
        }
    }

    /** Queues [frame] for every open WebSocket of [userIds]; returns at once. */
    fun send(userIds: Collection<UUID>, frame: ServerFrame) {
        val text = AppJson.encodeToString(ServerFrame.serializer(), frame)
        for (userId in userIds) {
            for (client in clients[userId].orEmpty()) client.queue.trySend(text)
        }
    }
}

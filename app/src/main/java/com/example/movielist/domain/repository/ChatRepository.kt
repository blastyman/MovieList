package com.example.movielist.domain.repository

import com.example.movielist.domain.model.ChatEvent
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.Message
import com.example.movielist.domain.model.MessagePage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Conversations and their history, over plain HTTP. */
interface ChatRepository {
    /** The most recently active first. */
    suspend fun conversations(): List<Conversation>

    suspend fun conversation(conversationId: String): Conversation

    /** The one-to-one chat with a friend, started if needed. */
    suspend fun startDirect(userId: String): Conversation

    suspend fun messages(conversationId: String, before: String? = null): MessagePage

    /** For when the live connection is down. */
    suspend fun send(conversationId: String, body: String): Message

    suspend fun markRead(conversationId: String)
}

/** The live connection: messages and typing as they happen. */
interface ChatConnection {
    val events: SharedFlow<ChatEvent>
    val isConnected: StateFlow<Boolean>

    /** False if not connected right now; send over [ChatRepository.send] instead. */
    fun send(conversationId: String, body: String, clientId: String): Boolean

    fun typing(conversationId: String)

    /** Tells the server you've read the chat up to now. */
    fun sendRead(conversationId: String)
}

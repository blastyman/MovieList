package com.example.movielist.data.repository

import com.example.movielist.data.model.SendMessageRequest
import com.example.movielist.data.model.StartDirectRequest
import com.example.movielist.data.remote.ChatApi
import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.Message
import com.example.movielist.domain.model.MessagePage
import com.example.movielist.domain.repository.ChatRepository
import retrofit2.HttpException

class BackendChatRepository(private val api: ChatApi) : ChatRepository {
    override suspend fun conversations(): List<Conversation> = api.conversations().map { it.toConversation() }

    override suspend fun conversation(conversationId: String): Conversation =
        api.conversation(conversationId).toConversation()

    override suspend fun startDirect(userId: String): Conversation =
        api.startDirect(StartDirectRequest(userId)).toConversation()

    override suspend fun messages(conversationId: String, before: String?): MessagePage =
        api.messages(conversationId, before).toMessagePage()

    override suspend fun send(conversationId: String, body: String): Message =
        api.send(conversationId, SendMessageRequest(body)).toMessage()

    override suspend fun markRead(conversationId: String) {
        val response = api.markRead(conversationId)
        if (!response.isSuccessful) throw HttpException(response)
    }
}

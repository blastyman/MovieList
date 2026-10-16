package com.example.movielist.data.model

import com.example.movielist.domain.model.Conversation
import com.example.movielist.domain.model.ConversationType
import com.example.movielist.domain.model.Message
import com.example.movielist.domain.model.MessagePage
import java.time.Instant

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/chat/ChatDtos.kt

data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: String,
) {
    fun toMessage() = Message(id, conversationId, senderId, body, Instant.parse(createdAt))
}

data class MessagePageDto(val items: List<MessageDto>?, val nextCursor: String?) {
    fun toMessagePage() = MessagePage(items.orEmpty().map { it.toMessage() }, nextCursor)
}

data class ConversationDto(
    val id: String,
    val type: String,
    val title: String,
    val groupId: String?,
    val members: List<UserSummaryDto>?,
    val lastMessage: MessageDto?,
    val unreadCount: Int,
    val updatedAt: String,
) {
    fun toConversation() =
        Conversation(
            id = id,
            type = if (type == "GROUP") ConversationType.GROUP else ConversationType.DIRECT,
            title = title,
            groupId = groupId,
            members = members.orEmpty().map { it.toUserSummary() },
            lastMessage = lastMessage?.toMessage(),
            unreadCount = unreadCount,
            updatedAt = Instant.parse(updatedAt),
        )
}

data class StartDirectRequest(val userId: String)

data class SendMessageRequest(val body: String)

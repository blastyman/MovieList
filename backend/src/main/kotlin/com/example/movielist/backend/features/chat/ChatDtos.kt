package com.example.movielist.backend.features.chat

import com.example.movielist.backend.features.users.UserSummaryDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: String,
)

/** One page of messages, newest first. Pass [nextCursor] as `before` for older ones. */
@Serializable
data class MessagePageDto(val items: List<MessageDto>, val nextCursor: String?)

@Serializable
data class ConversationDto(
    val id: String,
    /** DIRECT or GROUP. */
    val type: String,
    /** The group's name, or the other person's name in a direct chat. */
    val title: String,
    val groupId: String?,
    /** Everyone in it except you. */
    val members: List<UserSummaryDto>,
    val lastMessage: MessageDto?,
    /** Messages from others since you last read it. */
    val unreadCount: Int,
    /** The last message's time, or when the conversation started; the list is sorted by it. */
    val updatedAt: String,
)

@Serializable
data class StartDirectRequest(val userId: String)

@Serializable
data class SendMessageRequest(val body: String)

/** What the app sends over the WebSocket; `type` picks the kind. */
@Serializable
sealed interface ClientFrame {
    /** [clientId] is the app's temporary id, echoed back so it can swap its pending bubble. */
    @Serializable
    @SerialName("send")
    data class Send(val conversationId: String, val body: String, val clientId: String? = null) : ClientFrame

    @Serializable
    @SerialName("typing")
    data class Typing(val conversationId: String) : ClientFrame

    @Serializable
    @SerialName("read")
    data class Read(val conversationId: String) : ClientFrame
}

/** What the server pushes over the WebSocket. */
@Serializable
sealed interface ServerFrame {
    @Serializable
    @SerialName("message")
    data class Message(val message: MessageDto, val clientId: String? = null) : ServerFrame

    @Serializable
    @SerialName("typing")
    data class Typing(val conversationId: String, val userId: String) : ServerFrame

    /** A frame that failed; [clientId] says which pending message, if it was a send. */
    @Serializable
    @SerialName("error")
    data class Error(val error: String, val message: String, val clientId: String? = null) : ServerFrame
}

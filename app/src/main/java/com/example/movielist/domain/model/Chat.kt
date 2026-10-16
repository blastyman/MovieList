package com.example.movielist.domain.model

import java.time.Instant

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: Instant,
)

enum class ConversationType { DIRECT, GROUP }

/** A chat: one-to-one with a friend, or a group's chat. */
data class Conversation(
    val id: String,
    val type: ConversationType,
    /** The group's name, or the other person's name. */
    val title: String,
    val groupId: String?,
    /** Everyone in it except you. */
    val members: List<UserSummary>,
    val lastMessage: Message?,
    val unreadCount: Int,
    val updatedAt: Instant,
)

/** Messages newest first; pass [nextCursor] back for older ones, null when there are none. */
data class MessagePage(val items: List<Message>, val nextCursor: String?)

/** Something the server pushed over the live connection. */
sealed interface ChatEvent {
    /** [clientId] is set on your own message, so the pending bubble with that id can be replaced. */
    data class MessageReceived(val message: Message, val clientId: String?) : ChatEvent

    data class Typing(val conversationId: String, val userId: String) : ChatEvent

    /** A send over the live connection failed; [clientId] says which. */
    data class SendFailed(val clientId: String?, val reason: String) : ChatEvent
}

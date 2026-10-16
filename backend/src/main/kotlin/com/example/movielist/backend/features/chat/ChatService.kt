package com.example.movielist.backend.features.chat

import com.example.movielist.backend.db.ConversationMembers
import com.example.movielist.backend.db.Conversations
import com.example.movielist.backend.db.FriendGroups
import com.example.movielist.backend.db.Messages
import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import com.example.movielist.backend.db.violatedUniqueConstraint
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.MessagesFrom
import com.example.movielist.backend.features.users.UserSummaryDto
import com.example.movielist.backend.features.users.friendshipStates
import com.example.movielist.backend.features.users.toUserSummary
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

class MessagesOffException(message: String) : ApiException(HttpStatusCode.Forbidden, "MESSAGES_OFF", message)

class NotFriendsChatException :
    ApiException(HttpStatusCode.Forbidden, "NOT_FRIENDS", "You can only chat one-to-one with your friends")

/**
 * Conversations and their messages. A DIRECT conversation is between two friends, one per
 * pair; a GROUP conversation belongs to a friend group and has the same members (kept in step
 * by the group service). Every new message is saved first, then pushed through [hub] to the
 * members who are online; the others see it in the history next time.
 *
 * Unread counts come from `conversation_members.last_read_at`: messages from others after it.
 */
class ChatService(
    private val db: Database,
    private val hub: ChatHub,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Every conversation of [me], the most recently active first. */
    suspend fun conversations(me: UUID): List<ConversationDto> = db.query {
        val ids = ConversationMembers.select(ConversationMembers.conversationId)
            .where { ConversationMembers.userId eq me }
            .map { it[ConversationMembers.conversationId] }
        if (ids.isEmpty()) return@query emptyList()
        val rows = Conversations.join(FriendGroups, JoinType.LEFT, Conversations.groupId, FriendGroups.id)
            .selectAll()
            .where { Conversations.id inList ids }
            .toList()
        rows.map { toDto(me, it) }.sortedByDescending { it.updatedAt }
    }

    suspend fun conversation(me: UUID, conversationId: UUID): ConversationDto = db.query {
        requireMember(me, conversationId)
        toDto(me, conversationRow(conversationId))
    }

    /** The one-to-one conversation with a friend, started if it doesn't exist yet. */
    suspend fun startDirect(me: UUID, otherId: UUID): ConversationDto {
        if (me == otherId) throw ValidationException("You can't start a chat with yourself")
        return try {
            db.query { findOrCreateDirect(me, otherId) }
        } catch (e: ExposedSQLException) {
            // Both started it at the same moment: the unique key let one row in; read that one.
            if (e.violatedUniqueConstraint() == null) throw e
            db.query { findOrCreateDirect(me, otherId) }
        }
    }

    suspend fun messages(me: UUID, conversationId: UUID, before: String?, limit: Int?): MessagePageDto {
        val pageSize = limit ?: DEFAULT_LIMIT
        if (pageSize !in 1..MAX_LIMIT) throw ValidationException("limit must be between 1 and $MAX_LIMIT")
        val cursor = before?.let(::parseCursor)
        return db.query {
            requireMember(me, conversationId)
            val rows = Messages.selectAll()
                .where {
                    val inConversation = Messages.conversationId eq conversationId
                    if (cursor == null) inConversation
                    else inConversation and (
                        (Messages.createdAt less cursor.createdAt) or
                            ((Messages.createdAt eq cursor.createdAt) and (Messages.id less cursor.id))
                        )
                }
                .orderBy(Messages.createdAt to SortOrder.DESC, Messages.id to SortOrder.DESC)
                .limit(pageSize + 1)
                .toList()
            val items = rows.take(pageSize).map(::toMessage)
            MessagePageDto(items, if (rows.size > pageSize) items.last().let { "${it.createdAt}_${it.id}" } else null)
        }
    }

    /** Saves the message, then pushes it to every online member, the sender's other devices included. */
    suspend fun send(me: UUID, conversationId: UUID, rawBody: String, clientId: String? = null): MessageDto {
        val body = rawBody.trim()
        if (body.isEmpty()) throw ValidationException("A message can't be empty")
        if (body.length > MAX_BODY) throw ValidationException("A message can be at most $MAX_BODY characters")
        val (message, members) = db.query {
            requireMember(me, conversationId)
            val conversation = conversationRow(conversationId)
            val members = memberIds(conversationId)
            if (conversation[Conversations.type] == DIRECT) {
                // Unfriending ends the one-to-one chat: the history stays, new messages don't.
                val other = members.single { it != me }
                if (friendshipStates(me, listOf(other))[other] != FriendshipState.FRIENDS) throw NotFriendsChatException()
                requireMessagesOpen(me, other)
            }
            val now = OffsetDateTime.now(clock)
            val id = Messages.insert {
                it[Messages.conversationId] = conversationId
                it[senderId] = me
                it[Messages.body] = body
                it[createdAt] = now
            }[Messages.id]
            // Your own message is read by you.
            markRead(me, conversationId, now)
            MessageDto(id.toString(), conversationId.toString(), me.toString(), body, now.toInstant().toString()) to members
        }
        hub.send(members, ServerFrame.Message(message, clientId))
        return message
    }

    suspend fun read(me: UUID, conversationId: UUID) {
        db.query {
            requireMember(me, conversationId)
            markRead(me, conversationId, OffsetDateTime.now(clock))
        }
    }

    /** Tells the other online members that [me] is typing. Not saved anywhere. */
    suspend fun typing(me: UUID, conversationId: UUID) {
        val others = db.query {
            requireMember(me, conversationId)
            memberIds(conversationId) - me
        }
        hub.send(others, ServerFrame.Typing(conversationId.toString(), me.toString()))
    }

    private fun JdbcTransaction.findOrCreateDirect(me: UUID, otherId: UUID): ConversationDto {
        if (Users.selectAll().where { Users.id eq otherId }.empty()) throw NotFoundException("No user with id $otherId")
        if (friendshipStates(me, listOf(otherId))[otherId] != FriendshipState.FRIENDS) throw NotFriendsChatException()
        requireMessagesOpen(me, otherId)
        val key = listOf(me, otherId).sortedBy { it.toString() }.joinToString(":")
        val existing = Conversations.select(Conversations.id).where { Conversations.directKey eq key }.singleOrNull()
        val conversationId = existing?.get(Conversations.id) ?: run {
            val id = Conversations.insert {
                it[type] = DIRECT
                it[directKey] = key
                it[createdAt] = OffsetDateTime.now(clock)
            }[Conversations.id]
            listOf(me, otherId).forEach { userId ->
                ConversationMembers.insert {
                    it[ConversationMembers.conversationId] = id
                    it[ConversationMembers.userId] = userId
                }
            }
            id
        }
        return toDto(me, conversationRow(conversationId))
    }

    /** Either of you can turn one-to-one messages off ("who can message me": nobody). */
    private fun JdbcTransaction.requireMessagesOpen(me: UUID, other: UUID) {
        val closed = Users.select(Users.id, Users.messagesFrom)
            .where { Users.id inList listOf(me, other) }
            .associate { it[Users.id] to (it[Users.messagesFrom] == MessagesFrom.NOBODY.name) }
        if (closed[me] == true) throw MessagesOffException("You've turned one-to-one messages off in your privacy settings")
        if (closed[other] == true) throw MessagesOffException("They aren't taking messages")
    }

    private fun JdbcTransaction.toDto(me: UUID, row: ResultRow): ConversationDto {
        val conversationId = row[Conversations.id]
        val others = ConversationMembers.join(Users, JoinType.INNER, ConversationMembers.userId, Users.id)
            .selectAll()
            .where { (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId neq me) }
            .toList()
        val states = friendshipStates(me, others.map { it[Users.id] })
        val members = others.map { it.toUserSummary(states[it[Users.id]] ?: FriendshipState.NONE) }.sortedBy { (it.displayName ?: it.username).lowercase() }

        val last = Messages.selectAll()
            .where { Messages.conversationId eq conversationId }
            .orderBy(Messages.createdAt to SortOrder.DESC, Messages.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toMessage)

        val lastRead = ConversationMembers.select(ConversationMembers.lastReadAt)
            .where { (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq me) }
            .single()[ConversationMembers.lastReadAt]
        val unread = Count(Messages.id)
        val unreadCount = Messages.select(unread)
            .where {
                val fromOthers = (Messages.conversationId eq conversationId) and (Messages.senderId neq me)
                if (lastRead == null) fromOthers else fromOthers and (Messages.createdAt greater lastRead)
            }
            .single()[unread].toInt()

        val isGroup = row[Conversations.type] == GROUP
        val title = if (isGroup) row[FriendGroups.name] else members.firstOrNull()?.let { it.displayName ?: it.username }.orEmpty()
        return ConversationDto(
            id = conversationId.toString(),
            type = row[Conversations.type],
            title = title,
            groupId = row[Conversations.groupId]?.toString(),
            members = members,
            lastMessage = last,
            unreadCount = unreadCount,
            updatedAt = last?.createdAt ?: row[Conversations.createdAt].toInstant().toString(),
        )
    }

    private fun JdbcTransaction.conversationRow(conversationId: UUID): ResultRow =
        Conversations.join(FriendGroups, JoinType.LEFT, Conversations.groupId, FriendGroups.id)
            .selectAll()
            .where { Conversations.id eq conversationId }
            .single()

    private fun JdbcTransaction.markRead(me: UUID, conversationId: UUID, at: OffsetDateTime) {
        ConversationMembers.update({
            (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq me)
        }) { it[lastReadAt] = at }
    }

    /** Non-members get a 404, so conversation ids don't reveal that a conversation exists. */
    private fun JdbcTransaction.requireMember(me: UUID, conversationId: UUID) {
        val member = ConversationMembers.selectAll()
            .where { (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq me) }
            .empty()
            .not()
        if (!member) throw NotFoundException("No such conversation")
    }

    private fun JdbcTransaction.memberIds(conversationId: UUID): List<UUID> =
        ConversationMembers.select(ConversationMembers.userId)
            .where { ConversationMembers.conversationId eq conversationId }
            .map { it[ConversationMembers.userId] }

    private fun toMessage(row: ResultRow) = MessageDto(
        id = row[Messages.id].toString(),
        conversationId = row[Messages.conversationId].toString(),
        senderId = row[Messages.senderId].toString(),
        body = row[Messages.body],
        createdAt = row[Messages.createdAt].toInstant().toString(),
    )

    private data class Cursor(val createdAt: OffsetDateTime, val id: UUID)

    /** Cursor format: `<ISO instant>_<message id>`, the oldest message already seen. */
    private fun parseCursor(raw: String): Cursor =
        try {
            Cursor(
                createdAt = Instant.parse(raw.substringBeforeLast('_')).atOffset(ZoneOffset.UTC),
                id = UUID.fromString(raw.substringAfterLast('_')),
            )
        } catch (e: DateTimeParseException) {
            throw ValidationException("Invalid cursor")
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Invalid cursor")
        }

    companion object {
        const val DIRECT = "DIRECT"
        const val GROUP = "GROUP"
        const val MAX_BODY = 2000
        private const val DEFAULT_LIMIT = 30
        private const val MAX_LIMIT = 100
    }
}

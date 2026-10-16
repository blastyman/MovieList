package com.example.movielist.backend.features.groups

import com.example.movielist.backend.db.ConversationMembers
import com.example.movielist.backend.db.Conversations
import com.example.movielist.backend.db.FriendGroups
import com.example.movielist.backend.db.GroupMembers
import com.example.movielist.backend.db.MovieReactions
import com.example.movielist.backend.db.MoviesCache
import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import com.example.movielist.backend.features.movies.toSummary
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.UserSummaryDto
import com.example.movielist.backend.features.users.friendshipStates
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.ForbiddenException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Max
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

class GroupFullException :
    ApiException(HttpStatusCode.Conflict, "GROUP_FULL", "A group can have at most ${GroupService.MAX_MEMBERS} members")

class NotYourFriendException :
    ApiException(HttpStatusCode.Forbidden, "NOT_FRIENDS", "You can only add your friends to a group")

/**
 * Friend groups of at most [MAX_MEMBERS] people, owner included. Each group has its own GROUP
 * conversation for chat, with the same members. You can only add your own friends; removing a
 * friend later does not remove them from groups you share (the group is its own thing by then).
 *
 * The size limit is checked inside the transaction that adds the member, after locking the
 * group row (`SELECT … FOR UPDATE`): two invites at once queue up on that lock, and the second
 * one counts the first one's member, so a group can never reach 11.
 */
class GroupService(private val db: Database, private val clock: Clock = Clock.systemUTC()) {

    /** Groups the caller is in, newest first. */
    suspend fun groups(me: UUID): List<GroupDto> = db.query {
        val ids = GroupMembers.select(GroupMembers.groupId)
            .where { GroupMembers.userId eq me }
            .map { it[GroupMembers.groupId] }
        if (ids.isEmpty()) return@query emptyList()
        FriendGroups.selectAll()
            .where { FriendGroups.id inList ids }
            .orderBy(FriendGroups.createdAt to SortOrder.DESC)
            .map { toDto(me, it[FriendGroups.id], it[FriendGroups.name], it[FriendGroups.ownerId], it[FriendGroups.createdAt]) }
    }

    suspend fun create(me: UUID, request: CreateGroupRequest): GroupDto {
        val name = validName(request.name)
        val memberIds = request.memberIds.map(::parseId).distinct() - me
        if (memberIds.size + 1 > MAX_MEMBERS) throw GroupFullException()
        return db.query {
            requireFriends(me, memberIds)
            val now = OffsetDateTime.now(clock)
            val groupId = FriendGroups.insert {
                it[FriendGroups.name] = name
                it[ownerId] = me
                it[createdAt] = now
            }[FriendGroups.id]
            val conversationId = Conversations.insert {
                it[type] = GROUP_CONVERSATION
                it[Conversations.groupId] = groupId
                it[createdAt] = now
            }[Conversations.id]
            // The owner joins first, so they lead the member list; the others keep the given order.
            (listOf(me) + memberIds).forEachIndexed { index, userId ->
                addMemberRows(groupId, conversationId, userId, now.plusNanos(index * 1000L))
            }
            group(me, groupId)
        }
    }

    suspend fun group(me: UUID, groupId: UUID): GroupDto = db.query {
        requireMember(me, groupId)
        group(me, groupId)
    }

    suspend fun rename(me: UUID, groupId: UUID, request: RenameGroupRequest): GroupDto {
        val name = validName(request.name)
        return db.query {
            requireOwner(me, groupId)
            FriendGroups.update({ FriendGroups.id eq groupId }) { it[FriendGroups.name] = name }
            group(me, groupId)
        }
    }

    /** Any member may add one of their friends; adding someone who's already in changes nothing. */
    suspend fun addMember(me: UUID, groupId: UUID, userId: UUID): GroupDto = db.query {
        // Lock the group row first: concurrent adds to this group wait here, one at a time.
        lockGroup(groupId)
        requireMember(me, groupId)
        if (isMember(userId, groupId)) return@query group(me, groupId)
        requireFriends(me, listOf(userId))
        if (memberIds(groupId).size >= MAX_MEMBERS) throw GroupFullException()
        addMemberRows(groupId, conversationOf(groupId), userId, OffsetDateTime.now(clock))
        group(me, groupId)
    }

    /**
     * The owner removes someone, or anyone removes themselves (leaves). When the owner leaves,
     * the member who joined earliest takes over; when the last member leaves, the group is
     * deleted. Returns the group as the caller now sees it, or null if they're no longer in it.
     */
    suspend fun removeMember(me: UUID, groupId: UUID, userId: UUID): GroupDto? = db.query {
        val ownerId = lockGroup(groupId)
        requireMember(me, groupId)
        if (userId != me && ownerId != me) throw ForbiddenException("Only the group owner can remove members")
        if (!isMember(userId, groupId)) throw NotFoundException("They are not in this group")

        val conversationId = conversationOf(groupId)
        GroupMembers.deleteWhere { (GroupMembers.groupId eq groupId) and (GroupMembers.userId eq userId) }
        ConversationMembers.deleteWhere {
            (ConversationMembers.conversationId eq conversationId) and (ConversationMembers.userId eq userId)
        }
        if (userId == ownerId) {
            val next = GroupMembers.selectAll()
                .where { GroupMembers.groupId eq groupId }
                .orderBy(GroupMembers.joinedAt to SortOrder.ASC)
                .limit(1)
                .singleOrNull()
            if (next == null) {
                FriendGroups.deleteWhere { FriendGroups.id eq groupId } // cascades to its conversation
            } else {
                FriendGroups.update({ FriendGroups.id eq groupId }) { it[FriendGroups.ownerId] = next[GroupMembers.userId] }
            }
        }
        if (userId == me) null else group(me, groupId)
    }

    /** The owner deletes the group for everyone, its chat included. */
    suspend fun disband(me: UUID, groupId: UUID) {
        db.query {
            lockGroup(groupId)
            requireOwner(me, groupId)
            FriendGroups.deleteWhere { FriendGroups.id eq groupId } // cascades to members and its conversation
        }
    }

    /** Movies every member likes, the newest match first. Needs at least two members. */
    suspend fun matches(me: UUID, groupId: UUID): List<GroupMatchDto> = db.query {
        requireMember(me, groupId)
        val members = memberIds(groupId)
        if (members.size < 2) return@query emptyList()
        val likes = Count(MovieReactions.userId)
        val lastLike = Max(MovieReactions.createdAt, MovieReactions.createdAt.columnType)
        MovieReactions
            .join(MoviesCache, JoinType.INNER, MovieReactions.movieId, MoviesCache.movieId)
            .select(MovieReactions.movieId, MoviesCache.json, likes, lastLike)
            .where { (MovieReactions.userId inList members) and (MovieReactions.reaction eq Reaction.LIKE.name) }
            .groupBy(MovieReactions.movieId, MoviesCache.json)
            .having { likes eq members.size.toLong() }
            .orderBy(lastLike to SortOrder.DESC)
            .map { GroupMatchDto(it[MoviesCache.json].toSummary(), it[lastLike]!!.toInstant().toString()) }
    }

    /**
     * Groups of [me] in which, now that [me] likes [movieId], every member does: what the match
     * popup announces. Groups of one don't count.
     */
    suspend fun groupsCompletedBy(me: UUID, movieId: Int): List<GroupBriefDto> = db.query {
        val myGroups = GroupMembers.select(GroupMembers.groupId)
            .where { GroupMembers.userId eq me }
            .map { it[GroupMembers.groupId] }
        if (myGroups.isEmpty()) return@query emptyList()
        val likers = MovieReactions.select(MovieReactions.userId)
            .where { (MovieReactions.movieId eq movieId) and (MovieReactions.reaction eq Reaction.LIKE.name) }
            .map { it[MovieReactions.userId] }
            .toSet()
        val completed = GroupMembers.selectAll()
            .where { GroupMembers.groupId inList myGroups }
            .groupBy({ it[GroupMembers.groupId] }, { it[GroupMembers.userId] })
            .filterValues { it.size >= 2 && likers.containsAll(it) }
            .keys
        if (completed.isEmpty()) return@query emptyList()
        FriendGroups.selectAll()
            .where { FriendGroups.id inList completed.toList() }
            .orderBy(FriendGroups.name to SortOrder.ASC)
            .map { GroupBriefDto(it[FriendGroups.id].toString(), it[FriendGroups.name]) }
    }

    /** Locks the group row until the transaction ends and returns its owner. */
    private fun JdbcTransaction.lockGroup(groupId: UUID): UUID =
        FriendGroups.selectAll().where { FriendGroups.id eq groupId }.forUpdate().singleOrNull()
            ?.get(FriendGroups.ownerId)
            ?: throw NotFoundException("No such group")

    private fun JdbcTransaction.group(me: UUID, groupId: UUID): GroupDto {
        val row = FriendGroups.selectAll().where { FriendGroups.id eq groupId }.single()
        return toDto(me, groupId, row[FriendGroups.name], row[FriendGroups.ownerId], row[FriendGroups.createdAt])
    }

    private fun JdbcTransaction.toDto(
        me: UUID,
        groupId: UUID,
        name: String,
        ownerId: UUID,
        createdAt: OffsetDateTime,
    ): GroupDto {
        val rows = GroupMembers.join(Users, JoinType.INNER, GroupMembers.userId, Users.id)
            .selectAll()
            .where { GroupMembers.groupId eq groupId }
            .orderBy(GroupMembers.joinedAt to SortOrder.ASC)
            .toList()
        val states = friendshipStates(me, rows.map { it[Users.id] })
        val members = rows
            .sortedByDescending { it[Users.id] == ownerId } // stable, so the rest stay in join order
            .map {
                UserSummaryDto(
                    id = it[Users.id].toString(),
                    username = it[Users.username],
                    displayName = it[Users.displayName],
                    avatarUrl = it[Users.avatarUrl],
                    friendship = states[it[Users.id]] ?: FriendshipState.NONE,
                )
            }
        return GroupDto(
            id = groupId.toString(),
            name = name,
            ownerId = ownerId.toString(),
            conversationId = conversationOf(groupId).toString(),
            members = members,
            maxMembers = MAX_MEMBERS,
            createdAt = createdAt.toInstant().toString(),
        )
    }

    private fun JdbcTransaction.addMemberRows(groupId: UUID, conversationId: UUID, userId: UUID, joinedAt: OffsetDateTime) {
        GroupMembers.insert {
            it[GroupMembers.groupId] = groupId
            it[GroupMembers.userId] = userId
            it[GroupMembers.joinedAt] = joinedAt
        }
        ConversationMembers.insert {
            it[ConversationMembers.conversationId] = conversationId
            it[ConversationMembers.userId] = userId
        }
    }

    private fun JdbcTransaction.requireFriends(me: UUID, userIds: List<UUID>) {
        if (userIds.isEmpty()) return
        val states = friendshipStates(me, userIds)
        if (userIds.any { states[it] != FriendshipState.FRIENDS }) throw NotYourFriendException()
    }

    /** Non-members get a 404, so group ids don't reveal that a group exists. */
    private fun JdbcTransaction.requireMember(me: UUID, groupId: UUID) {
        if (!isMember(me, groupId)) throw NotFoundException("No such group")
    }

    private fun JdbcTransaction.requireOwner(me: UUID, groupId: UUID) {
        requireMember(me, groupId)
        val owner = FriendGroups.select(FriendGroups.ownerId).where { FriendGroups.id eq groupId }.single()
        if (owner[FriendGroups.ownerId] != me) throw ForbiddenException("Only the group owner can do this")
    }

    private fun JdbcTransaction.isMember(userId: UUID, groupId: UUID): Boolean =
        !GroupMembers.selectAll().where { (GroupMembers.groupId eq groupId) and (GroupMembers.userId eq userId) }.empty()

    private fun JdbcTransaction.memberIds(groupId: UUID): List<UUID> =
        GroupMembers.select(GroupMembers.userId).where { GroupMembers.groupId eq groupId }.map { it[GroupMembers.userId] }

    private fun JdbcTransaction.conversationOf(groupId: UUID): UUID =
        Conversations.select(Conversations.id).where { Conversations.groupId eq groupId }.single()[Conversations.id]

    private fun validName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) throw ValidationException("Give the group a name")
        if (name.length > MAX_NAME) throw ValidationException("Group names can be at most $MAX_NAME characters")
        return name
    }

    private fun parseId(raw: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ValidationException("User id must be a UUID")

    companion object {
        /** Members per group, owner included. */
        const val MAX_MEMBERS = 10
        const val MAX_NAME = 50
        private const val GROUP_CONVERSATION = "GROUP"
    }
}

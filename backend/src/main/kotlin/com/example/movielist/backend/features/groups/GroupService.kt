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
import com.example.movielist.backend.features.users.AvatarStore
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.UserSummaryDto
import com.example.movielist.backend.features.users.friendshipStates
import com.example.movielist.backend.features.users.toUserSummary
import com.example.movielist.backend.features.users.validAvatarParts
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.ForbiddenException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Max
import org.jetbrains.exposed.v1.core.ResultRow
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
class GroupService(
    private val db: Database,
    private val avatars: AvatarStore,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Groups the caller is in, newest first. */
    suspend fun groups(me: UUID): List<GroupDto> = db.query {
        val ids = GroupMembers.select(GroupMembers.groupId)
            .where { GroupMembers.userId eq me }
            .map { it[GroupMembers.groupId] }
        if (ids.isEmpty()) return@query emptyList()
        FriendGroups.selectAll()
            .where { FriendGroups.id inList ids }
            .orderBy(FriendGroups.createdAt to SortOrder.DESC)
            .toList()
            .let { toDtos(me, it) }
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

    /** Renames the group or changes its emoji icon. Owner only. */
    suspend fun update(me: UUID, groupId: UUID, request: UpdateGroupRequest): GroupDto {
        val name = request.name?.let(::validName)
        val (emoji, color) = validAvatarParts(request.avatarEmoji, request.avatarColor)
        return db.query {
            requireOwner(me, groupId)
            FriendGroups.update({ FriendGroups.id eq groupId }) {
                if (name != null) it[FriendGroups.name] = name
                if (emoji != null) it[FriendGroups.avatarEmoji] = emoji.ifEmpty { null }
                if (color != null) it[FriendGroups.avatarColor] = color.ifEmpty { null }
            }
            group(me, groupId)
        }
    }

    /**
     * A new photo for the group's icon, in place of the old one. Owner only. The file is written
     * first; the ownership check and the switch happen together under the group's row lock, so
     * a group disbanded or handed over meanwhile is refused (and the new file removed again).
     */
    suspend fun setAvatar(me: UUID, groupId: UUID, bytes: ByteArray): GroupDto {
        db.query { requireOwner(me, groupId) } // fail fast before storing anything
        val path = avatars.save(groupId, bytes)
        val (old, group) = try {
            db.query { replaceAvatar(me, groupId, path) }
        } catch (e: Exception) {
            avatars.delete(path)
            throw e
        }
        avatars.delete(old)
        return group
    }

    suspend fun removeAvatar(me: UUID, groupId: UUID): GroupDto {
        val (old, group) = db.query { replaceAvatar(me, groupId, null) }
        avatars.delete(old)
        return group
    }

    /** Sets the photo to [path] (null removes it) and returns the old one with the group. */
    private fun JdbcTransaction.replaceAvatar(me: UUID, groupId: UUID, path: String?): Pair<String?, GroupDto> {
        lockGroup(groupId)
        requireOwner(me, groupId)
        val old = avatarOf(groupId)
        FriendGroups.update({ FriendGroups.id eq groupId }) { it[FriendGroups.avatarUrl] = path }
        return old to group(me, groupId)
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
    suspend fun removeMember(me: UUID, groupId: UUID, userId: UUID): GroupDto? {
        val (result, deletedAvatar) = db.query {
            val deletedAvatar = removeMemberRows(me, groupId, userId)
            (if (userId == me) null else group(me, groupId)) to deletedAvatar
        }
        // A group deleted because its last member left takes its photo with it, once committed.
        avatars.delete(deletedAvatar)
        return result
    }

    /**
     * Leaves every group inside [transaction], handing over the ones [me] owns; for deleting an
     * account in one go. Returns the photos of groups that ended up deleted, to remove once the
     * transaction has committed.
     */
    fun leaveAllIn(transaction: JdbcTransaction, me: UUID): List<String> =
        with(transaction) {
            GroupMembers.select(GroupMembers.groupId)
                .where { GroupMembers.userId eq me }
                .map { it[GroupMembers.groupId] }
                .mapNotNull { removeMemberRows(me, it, me) }
        }

    /** Removes [userId] from the group; returns the group's photo if that deleted the group. */
    private fun JdbcTransaction.removeMemberRows(me: UUID, groupId: UUID, userId: UUID): String? {
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
                val photo = avatarOf(groupId)
                FriendGroups.deleteWhere { FriendGroups.id eq groupId } // cascades to its conversation
                return photo
            } else {
                FriendGroups.update({ FriendGroups.id eq groupId }) { it[FriendGroups.ownerId] = next[GroupMembers.userId] }
            }
        }
        return null
    }

    /** The owner deletes the group for everyone, its chat included. */
    suspend fun disband(me: UUID, groupId: UUID) {
        val avatar = db.query {
            lockGroup(groupId)
            requireOwner(me, groupId)
            val avatar = avatarOf(groupId)
            FriendGroups.deleteWhere { FriendGroups.id eq groupId } // cascades to members and its conversation
            avatar
        }
        avatars.delete(avatar)
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
        val membersByGroup = GroupMembers.selectAll()
            .where { GroupMembers.groupId inList myGroups }
            .groupBy({ it[GroupMembers.groupId] }, { it[GroupMembers.userId] })
        // Only the likes of people in these groups matter, not everyone who ever liked the movie.
        val likers = MovieReactions.select(MovieReactions.userId)
            .where {
                (MovieReactions.movieId eq movieId) and
                    (MovieReactions.reaction eq Reaction.LIKE.name) and
                    (MovieReactions.userId inList membersByGroup.values.flatten().distinct())
            }
            .map { it[MovieReactions.userId] }
            .toSet()
        val completed = membersByGroup
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

    private fun JdbcTransaction.group(me: UUID, groupId: UUID): GroupDto =
        toDtos(me, listOf(FriendGroups.selectAll().where { FriendGroups.id eq groupId }.single())).single()

    private fun JdbcTransaction.avatarOf(groupId: UUID): String? =
        FriendGroups.select(FriendGroups.avatarUrl).where { FriendGroups.id eq groupId }.single()[FriendGroups.avatarUrl]

    /**
     * [groups] as DTOs, members included. Three queries in all however many groups there are:
     * every member, the caller's friendship with each, and every group's chat.
     */
    private fun JdbcTransaction.toDtos(me: UUID, groups: List<ResultRow>): List<GroupDto> {
        if (groups.isEmpty()) return emptyList()
        val groupIds = groups.map { it[FriendGroups.id] }
        val memberRows = GroupMembers.join(Users, JoinType.INNER, GroupMembers.userId, Users.id)
            .selectAll()
            .where { GroupMembers.groupId inList groupIds }
            .orderBy(GroupMembers.joinedAt to SortOrder.ASC)
            .toList()
        val states = friendshipStates(me, memberRows.map { it[Users.id] }.distinct())
        val membersByGroup = memberRows.groupBy { it[GroupMembers.groupId] }
        val chats = Conversations.select(Conversations.groupId, Conversations.id)
            .where { Conversations.groupId inList groupIds }
            .associate { it[Conversations.groupId] to it[Conversations.id] }
        return groups.map { toDto(it, membersByGroup[it[FriendGroups.id]].orEmpty(), states, chats.getValue(it[FriendGroups.id])) }
    }

    private fun toDto(group: ResultRow, memberRows: List<ResultRow>, states: Map<UUID, FriendshipState>, conversationId: UUID): GroupDto {
        val groupId = group[FriendGroups.id]
        val ownerId = group[FriendGroups.ownerId]
        val members = memberRows
            .sortedByDescending { it[Users.id] == ownerId } // stable, so the rest stay in join order
            .map { it.toUserSummary(states[it[Users.id]] ?: FriendshipState.NONE) }
        return GroupDto(
            id = groupId.toString(),
            name = group[FriendGroups.name],
            avatarUrl = group[FriendGroups.avatarUrl],
            avatarEmoji = group[FriendGroups.avatarEmoji],
            avatarColor = group[FriendGroups.avatarColor],
            ownerId = ownerId.toString(),
            conversationId = conversationId.toString(),
            members = members,
            maxMembers = MAX_MEMBERS,
            createdAt = group[FriendGroups.createdAt].toInstant().toString(),
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

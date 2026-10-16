package com.example.movielist.backend.features.friends

import com.example.movielist.backend.db.FriendshipStatus
import com.example.movielist.backend.db.Friendships
import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import com.example.movielist.backend.db.violatedUniqueConstraint
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.UserSummaryDto
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

class NoFriendRequestException :
    ApiException(HttpStatusCode.NotFound, "NO_FRIEND_REQUEST", "There is no such friend request")

class NotFriendsException : ApiException(HttpStatusCode.NotFound, "NOT_FRIENDS", "You are not friends")

/**
 * Friend requests and friendships. One `friendships` row per pair of users, whichever side
 * asked (a unique index enforces it): PENDING while unanswered, ACCEPTED once friends.
 * Declining, cancelling and unfriending delete the row, so the pair can start over.
 */
class FriendService(private val db: Database, private val clock: Clock = Clock.systemUTC()) {

    /**
     * Asks [otherId] to be friends. If they already asked the caller, this accepts instead.
     * Asking again, or asking a friend, changes nothing and returns the current state.
     */
    suspend fun sendRequest(me: UUID, otherId: UUID): FriendshipDto {
        if (me == otherId) throw ValidationException("You can't befriend yourself")
        return try {
            db.query { requestOrAccept(me, otherId) }
        } catch (e: ExposedSQLException) {
            // Both users asked each other at the same instant: the unique index let only one
            // row in. Now that it exists, the same call accepts it.
            if (e.violatedUniqueConstraint() == null) throw e
            db.query { requestOrAccept(me, otherId) }
        }
    }

    suspend fun accept(me: UUID, otherId: UUID): FriendshipDto = db.query {
        val row = pairRow(me, otherId)?.takeIf { it.isPending && it.requester == otherId }
            ?: throw NoFriendRequestException()
        markAccepted(row)
        FriendshipDto(otherId.toString(), FriendshipState.FRIENDS)
    }

    suspend fun decline(me: UUID, otherId: UUID): FriendshipDto = db.query {
        val row = pairRow(me, otherId)?.takeIf { it.isPending && it.requester == otherId }
            ?: throw NoFriendRequestException()
        delete(row)
        FriendshipDto(otherId.toString(), FriendshipState.NONE)
    }

    suspend fun cancel(me: UUID, otherId: UUID): FriendshipDto = db.query {
        val row = pairRow(me, otherId)?.takeIf { it.isPending && it.requester == me }
            ?: throw NoFriendRequestException()
        delete(row)
        FriendshipDto(otherId.toString(), FriendshipState.NONE)
    }

    suspend fun remove(me: UUID, otherId: UUID): FriendshipDto = db.query {
        val row = pairRow(me, otherId)?.takeIf { !it.isPending } ?: throw NotFriendsException()
        delete(row)
        FriendshipDto(otherId.toString(), FriendshipState.NONE)
    }

    /** The caller's friends, by name. */
    suspend fun friends(me: UUID): List<UserSummaryDto> = db.query {
        rowsWithOtherUser(me) { Friendships.status eq FriendshipStatus.ACCEPTED.name }
            .map { (_, user) -> user.toSummary(FriendshipState.FRIENDS) }
            .sortedBy { (it.displayName ?: it.username).lowercase() }
    }

    suspend fun requests(me: UUID): FriendRequestsDto = db.query {
        val pending = rowsWithOtherUser(me) { Friendships.status eq FriendshipStatus.PENDING.name }
            .sortedByDescending { (row, _) -> row[Friendships.createdAt] }
        val (incoming, outgoing) = pending.partition { (row, _) -> row[Friendships.addresseeId] == me }
        FriendRequestsDto(
            incoming = incoming.map { (row, user) ->
                FriendRequestDto(user.toSummary(FriendshipState.REQUEST_RECEIVED), row[Friendships.createdAt].toInstant().toString())
            },
            outgoing = outgoing.map { (row, user) ->
                FriendRequestDto(user.toSummary(FriendshipState.REQUEST_SENT), row[Friendships.createdAt].toInstant().toString())
            },
        )
    }

    private data class PairRow(val requester: UUID, val addressee: UUID, val isPending: Boolean)

    private fun JdbcTransaction.requestOrAccept(me: UUID, otherId: UUID): FriendshipDto {
        if (Users.selectAll().where { Users.id eq otherId }.empty()) throw NotFoundException("No user with id $otherId")
        val row = pairRow(me, otherId)
        val state = when {
            row == null -> {
                Friendships.insert {
                    it[requesterId] = me
                    it[addresseeId] = otherId
                    it[status] = FriendshipStatus.PENDING.name
                    it[createdAt] = OffsetDateTime.now(clock)
                }
                FriendshipState.REQUEST_SENT
            }
            !row.isPending -> FriendshipState.FRIENDS
            row.requester == me -> FriendshipState.REQUEST_SENT
            else -> {
                markAccepted(row)
                FriendshipState.FRIENDS
            }
        }
        return FriendshipDto(otherId.toString(), state)
    }

    private fun JdbcTransaction.pairRow(a: UUID, b: UUID): PairRow? =
        Friendships.selectAll()
            .where {
                ((Friendships.requesterId eq a) and (Friendships.addresseeId eq b)) or
                    ((Friendships.requesterId eq b) and (Friendships.addresseeId eq a))
            }
            .forUpdate()
            .singleOrNull()
            ?.let {
                PairRow(
                    requester = it[Friendships.requesterId],
                    addressee = it[Friendships.addresseeId],
                    isPending = it[Friendships.status] == FriendshipStatus.PENDING.name,
                )
            }

    private fun JdbcTransaction.markAccepted(row: PairRow) {
        Friendships.update({ (Friendships.requesterId eq row.requester) and (Friendships.addresseeId eq row.addressee) }) {
            it[status] = FriendshipStatus.ACCEPTED.name
            it[respondedAt] = OffsetDateTime.now(clock)
        }
    }

    private fun JdbcTransaction.delete(row: PairRow) {
        Friendships.deleteWhere { (requesterId eq row.requester) and (addresseeId eq row.addressee) }
    }

    /** Friendship rows involving [me] that match [condition], each with the other user's row. */
    private fun JdbcTransaction.rowsWithOtherUser(
        me: UUID,
        condition: () -> Op<Boolean>,
    ): List<Pair<ResultRow, ResultRow>> {
        val rows = Friendships.selectAll()
            .where { ((Friendships.requesterId eq me) or (Friendships.addresseeId eq me)) and condition() }
            .toList()
        val otherIds = rows.map { if (it[Friendships.requesterId] == me) it[Friendships.addresseeId] else it[Friendships.requesterId] }
        if (otherIds.isEmpty()) return emptyList()
        val users = Users.selectAll().where { Users.id inList otherIds }.associateBy { it[Users.id] }
        return rows.zip(otherIds).mapNotNull { (row, otherId) -> users[otherId]?.let { row to it } }
    }

    private fun ResultRow.toSummary(state: FriendshipState) = UserSummaryDto(
        id = this[Users.id].toString(),
        username = this[Users.username],
        displayName = this[Users.displayName],
        avatarUrl = this[Users.avatarUrl],
        friendship = state,
    )
}

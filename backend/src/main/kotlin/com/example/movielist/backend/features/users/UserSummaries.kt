package com.example.movielist.backend.features.users

import com.example.movielist.backend.db.FriendshipStatus
import com.example.movielist.backend.db.Friendships
import com.example.movielist.backend.db.MovieReactions
import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.db.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

/** Who may send you a friend request. */
@Serializable
enum class FriendRequestsFrom {
    EVERYONE,
    /** Only people who like at least one movie you like. */
    MATCHES,
    NOBODY,
}

/** Who may chat with you one-to-one. Group chats aren't affected. */
@Serializable
enum class MessagesFrom { FRIENDS, NOBODY }

/** A `users` row as it appears in lists, with the viewer's [friendship] to them. */
internal fun ResultRow.toUserSummary(friendship: FriendshipState) = UserSummaryDto(
    id = this[Users.id].toString(),
    username = this[Users.username],
    displayName = this[Users.displayName],
    avatarUrl = this[Users.avatarUrl],
    avatarEmoji = this[Users.avatarEmoji],
    avatarColor = this[Users.avatarColor],
    friendship = friendship,
)

/** Everyone [me] is friends with. */
internal fun JdbcTransaction.friendIds(me: UUID): Set<UUID> =
    Friendships.selectAll()
        .where {
            ((Friendships.requesterId eq me) or (Friendships.addresseeId eq me)) and
                (Friendships.status eq FriendshipStatus.ACCEPTED.name)
        }
        .map { if (it[Friendships.requesterId] == me) it[Friendships.addresseeId] else it[Friendships.requesterId] }
        .toSet()

/** Whether [a] and [b] like at least one of the same movies. */
internal fun JdbcTransaction.shareALike(a: UUID, b: UUID): Boolean {
    val aLikes = MovieReactions.select(MovieReactions.movieId)
        .where { (MovieReactions.userId eq a) and (MovieReactions.reaction eq Reaction.LIKE.name) }
        .map { it[MovieReactions.movieId] }
    if (aLikes.isEmpty()) return false
    return !MovieReactions.selectAll()
        .where {
            (MovieReactions.userId eq b) and
                (MovieReactions.reaction eq Reaction.LIKE.name) and
                (MovieReactions.movieId inList aLikes)
        }
        .limit(1)
        .empty()
}

/**
 * Whether [viewer] may send [target] a friend request under the target's privacy setting.
 * Answering a request they sent you is always allowed, and checked elsewhere.
 */
internal fun JdbcTransaction.mayRequest(viewer: UUID, target: UUID, setting: FriendRequestsFrom): Boolean =
    when (setting) {
        FriendRequestsFrom.EVERYONE -> true
        FriendRequestsFrom.MATCHES -> shareALike(viewer, target)
        FriendRequestsFrom.NOBODY -> false
    }

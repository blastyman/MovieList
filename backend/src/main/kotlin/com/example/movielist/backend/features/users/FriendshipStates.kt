package com.example.movielist.backend.features.users

import com.example.movielist.backend.db.FriendshipStatus
import com.example.movielist.backend.db.Friendships
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

/** The viewer's friendship state with each of [userIds]; users with no row are left out. */
internal fun JdbcTransaction.friendshipStates(viewerId: UUID, userIds: List<UUID>): Map<UUID, FriendshipState> {
    if (userIds.isEmpty()) return emptyMap()
    return Friendships.selectAll()
        .where {
            ((Friendships.requesterId eq viewerId) and (Friendships.addresseeId inList userIds)) or
                ((Friendships.addresseeId eq viewerId) and (Friendships.requesterId inList userIds))
        }
        .associate { row ->
            val sentByViewer = row[Friendships.requesterId] == viewerId
            val other = if (sentByViewer) row[Friendships.addresseeId] else row[Friendships.requesterId]
            other to when {
                row[Friendships.status] == FriendshipStatus.ACCEPTED.name -> FriendshipState.FRIENDS
                sentByViewer -> FriendshipState.REQUEST_SENT
                else -> FriendshipState.REQUEST_RECEIVED
            }
        }
}

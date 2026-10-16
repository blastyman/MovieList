package com.example.movielist.backend.features.users

import com.example.movielist.backend.db.Comments
import com.example.movielist.backend.db.FriendshipStatus
import com.example.movielist.backend.db.Friendships
import com.example.movielist.backend.db.MovieReactions
import com.example.movielist.backend.db.MoviesCache
import com.example.movielist.backend.db.Ratings
import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import com.example.movielist.backend.features.movies.toSummary
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class UserService(private val db: Database) {

    /**
     * Users whose username or display name contains [query], ignoring case. Exact matches
     * come first, then names starting with the query, then the rest; the viewer is left out.
     */
    suspend fun search(viewerId: UUID, query: String?, page: Int?): UserSearchPageDto {
        val term = query?.trim()?.lowercase().orEmpty()
        if (term.length < MIN_QUERY) throw ValidationException("q must be at least $MIN_QUERY characters")
        if (term.length > MAX_QUERY) throw ValidationException("q must be at most $MAX_QUERY characters")
        val pageNumber = page ?: 1
        if (pageNumber < 1) throw ValidationException("page must be 1 or more")

        // % and _ are wildcards in LIKE; a search for "a_b" must mean the literal "a_b".
        val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val text = VarCharColumnType()
        return db.query {
            val rows = exec(
                """
                SELECT id, username, display_name, avatar_url, avatar_emoji, avatar_color
                FROM users
                WHERE id <> ?
                  AND (lower(username) LIKE ? ESCAPE '\' OR lower(display_name) LIKE ? ESCAPE '\')
                ORDER BY
                  CASE WHEN lower(username) = ? OR lower(display_name) = ? THEN 0
                       WHEN lower(username) LIKE ? ESCAPE '\' OR lower(display_name) LIKE ? ESCAPE '\' THEN 1
                       ELSE 2 END,
                  lower(username)
                LIMIT ? OFFSET ?
                """.trimIndent(),
                listOf(
                    UUIDColumnType() to viewerId,
                    text to "%$escaped%", text to "%$escaped%",
                    text to term, text to term,
                    text to "$escaped%", text to "$escaped%",
                    IntegerColumnType() to PAGE_SIZE + 1,
                    IntegerColumnType() to (pageNumber - 1) * PAGE_SIZE,
                ),
            ) { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            FoundUser(
                                id = rs.getObject("id", UUID::class.java),
                                username = rs.getString("username"),
                                displayName = rs.getString("display_name"),
                                avatarUrl = rs.getString("avatar_url"),
                                avatarEmoji = rs.getString("avatar_emoji"),
                                avatarColor = rs.getString("avatar_color"),
                            )
                        )
                    }
                }
            }.orEmpty()

            val shown = rows.take(PAGE_SIZE)
            val states = friendshipStates(viewerId, shown.map { it.id })
            UserSearchPageDto(
                page = pageNumber,
                results = shown.map {
                    UserSummaryDto(
                        id = it.id.toString(),
                        username = it.username,
                        displayName = it.displayName,
                        avatarUrl = it.avatarUrl,
                        avatarEmoji = it.avatarEmoji,
                        avatarColor = it.avatarColor,
                        friendship = states[it.id] ?: FriendshipState.NONE,
                    )
                },
                hasMore = rows.size > PAGE_SIZE,
            )
        }
    }

    suspend fun profile(viewerId: UUID, userId: UUID): UserProfileDto = db.query {
        val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
            ?: throw NotFoundException("No user with id $userId")

        val likes = countWhere(MovieReactions) {
            (MovieReactions.userId eq userId) and (MovieReactions.reaction eq Reaction.LIKE.name)
        }
        val ratings = countWhere(Ratings) { Ratings.userId eq userId }
        val comments = countWhere(Comments) { (Comments.userId eq userId) and (Comments.deleted eq false) }
        val friends = countWhere(Friendships) {
            ((Friendships.requesterId eq userId) or (Friendships.addresseeId eq userId)) and
                (Friendships.status eq FriendshipStatus.ACCEPTED.name)
        }

        val isMe = userId == viewerId
        val friendship = if (isMe) FriendshipState.NONE
            else friendshipStates(viewerId, listOf(userId))[userId] ?: FriendshipState.NONE
        val areFriends = friendship == FriendshipState.FRIENDS
        // Private likes: only friends (and you) see them.
        val likesHidden = user[Users.privateLikes] && !isMe && !areFriends

        val recentLikes = MovieReactions
            .join(MoviesCache, JoinType.INNER, MovieReactions.movieId, MoviesCache.movieId)
            .select(MoviesCache.json)
            .where { (MovieReactions.userId eq userId) and (MovieReactions.reaction eq Reaction.LIKE.name) }
            .orderBy(MovieReactions.createdAt to SortOrder.DESC)
            .limit(RECENT_LIKES)
            .map { it[MoviesCache.json].toSummary() }

        val requestsFrom = FriendRequestsFrom.valueOf(user[Users.friendRequestsFrom])
        val viewerMessagesOff =
            Users.select(Users.messagesFrom).where { Users.id eq viewerId }.single()[Users.messagesFrom] == MessagesFrom.NOBODY.name
        UserProfileDto(
            id = userId.toString(),
            username = user[Users.username],
            displayName = user[Users.displayName],
            avatarUrl = user[Users.avatarUrl],
            avatarEmoji = user[Users.avatarEmoji],
            avatarColor = user[Users.avatarColor],
            memberSince = user[Users.createdAt].toInstant().toString(),
            isMe = isMe,
            friendship = friendship,
            stats = ProfileStatsDto(likes = if (likesHidden) null else likes, ratings = ratings, comments = comments, friends = friends),
            recentLikes = if (likesHidden) emptyList() else recentLikes,
            likesHidden = likesHidden,
            // Answering their request is always possible; a new one depends on their setting.
            canAddFriend = !isMe && (friendship == FriendshipState.REQUEST_RECEIVED ||
                (friendship == FriendshipState.NONE && mayRequest(viewerId, userId, requestsFrom))),
            canMessage = areFriends && !viewerMessagesOff && user[Users.messagesFrom] != MessagesFrom.NOBODY.name,
        )
    }

    private data class FoundUser(
        val id: UUID,
        val username: String,
        val displayName: String?,
        val avatarUrl: String?,
        val avatarEmoji: String?,
        val avatarColor: String?,
    )

    /** SELECT count(*) FROM [table] WHERE [condition]. */
    private fun JdbcTransaction.countWhere(table: Table, condition: () -> Op<Boolean>): Int {
        val count = Count(intLiteral(1))
        return table.select(count).where(condition()).single()[count].toInt()
    }

    private companion object {
        const val MIN_QUERY = 2
        const val MAX_QUERY = 50
        const val PAGE_SIZE = 20
        const val RECENT_LIKES = 10
    }
}

package com.example.movielist.backend.features.matches

import com.example.movielist.backend.db.MovieReactions
import com.example.movielist.backend.db.MoviesCache
import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import com.example.movielist.backend.features.movies.MovieSummaryDto
import com.example.movielist.backend.features.movies.toSummary
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.UserSummaryDto
import com.example.movielist.backend.features.users.friendIds
import com.example.movielist.backend.features.users.friendshipStates
import com.example.movielist.backend.features.users.toUserSummary
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

/** Someone who likes the same movie as you, and how many liked movies the two of you share. */
@Serializable
data class MatchPersonDto(
    val user: UserSummaryDto,
    val sharedLikes: Int,
)

/** The people who like a movie you like: the best [MatchService.MAX_PEOPLE] of them, and how many there are. */
@Serializable
data class MatchPeopleDto(
    val people: List<MatchPersonDto>,
    val count: Int,
)

/** A movie the caller and at least one other user both liked. */
@Serializable
data class MatchDto(
    val movie: MovieSummaryDto,
    /** Most likes in common first, so the people with the closest taste come up. */
    val people: List<MatchPersonDto>,
    /** Everyone who likes it, including those cut from [people]. */
    val peopleCount: Int,
    /** When the caller liked it. */
    val likedAt: String,
)

/**
 * Matches: movies you and someone else, friend or stranger, both liked. The point is to meet
 * people through movies, so strangers count, and each person comes with the number of likes you
 * share. Matches have no table of their own: they follow from likes, so unliking removes one.
 */
class MatchService(private val db: Database) {

    /** All matches, the movies you liked most recently first. */
    suspend fun matches(me: UUID): List<MatchDto> = db.query {
        val myLikes = MovieReactions
            .join(MoviesCache, JoinType.INNER, MovieReactions.movieId, MoviesCache.movieId)
            .select(MovieReactions.movieId, MovieReactions.createdAt, MoviesCache.json)
            .where { (MovieReactions.userId eq me) and (MovieReactions.reaction eq Reaction.LIKE.name) }
            .orderBy(MovieReactions.createdAt to SortOrder.DESC)
            .toList()
        if (myLikes.isEmpty()) return@query emptyList()

        val likers = likersByMovie(me, myLikes.map { it[MovieReactions.movieId] })
        if (likers.isEmpty()) return@query emptyList()
        val people = people(me, likers.values.flatten().distinct())

        myLikes.mapNotNull { row ->
            val ids = likers[row[MovieReactions.movieId]] ?: return@mapNotNull null
            val ranked = ids.map(people::getValue).sortedWith(BEST_MATCH_FIRST)
            MatchDto(
                movie = row[MoviesCache.json].toSummary(),
                people = ranked.take(MAX_PEOPLE),
                peopleCount = ranked.size,
                likedAt = row[MovieReactions.createdAt].toInstant().toString(),
            )
        }
    }

    /** Who else likes [movieId]; what the "It's a match!" popup shows after a like. */
    suspend fun peopleWhoLike(me: UUID, movieId: Int): MatchPeopleDto = db.query {
        val ids = likersByMovie(me, listOf(movieId))[movieId].orEmpty()
        val ranked = people(me, ids).values.sortedWith(BEST_MATCH_FIRST)
        MatchPeopleDto(ranked.take(MAX_PEOPLE), ranked.size)
    }

    /**
     * For each of [movieIds], the other users who liked it. People who turned "show me in
     * matches" off are left out, except for their friends.
     */
    private fun JdbcTransaction.likersByMovie(me: UUID, movieIds: List<Int>): Map<Int, List<UUID>> {
        val friends = friendIds(me)
        return MovieReactions.join(Users, JoinType.INNER, MovieReactions.userId, Users.id)
            .select(MovieReactions.movieId, MovieReactions.userId, Users.showInMatches)
            .where {
                (MovieReactions.movieId inList movieIds) and
                    (MovieReactions.userId neq me) and
                    (MovieReactions.reaction eq Reaction.LIKE.name)
            }
            .filter { it[Users.showInMatches] || it[MovieReactions.userId] in friends }
            .groupBy({ it[MovieReactions.movieId] }, { it[MovieReactions.userId] })
    }

    /** [userIds] as people: their profile, your friendship with them, and the likes you share. */
    private fun JdbcTransaction.people(me: UUID, userIds: List<UUID>): Map<UUID, MatchPersonDto> {
        if (userIds.isEmpty()) return emptyMap()
        val shared = sharedLikes(me, userIds)
        val states = friendshipStates(me, userIds)
        return Users.selectAll().where { Users.id inList userIds }.associate {
            val id = it[Users.id]
            id to MatchPersonDto(
                user = it.toUserSummary(states[id] ?: FriendshipState.NONE),
                sharedLikes = shared[id] ?: 0,
            )
        }
    }

    /** How many movies each of [userIds] liked that [me] liked too. */
    private fun JdbcTransaction.sharedLikes(me: UUID, userIds: List<UUID>): Map<UUID, Int> {
        val myMovieIds = MovieReactions.select(MovieReactions.movieId)
            .where { (MovieReactions.userId eq me) and (MovieReactions.reaction eq Reaction.LIKE.name) }
            .map { it[MovieReactions.movieId] }
        if (myMovieIds.isEmpty()) return emptyMap()
        val count = Count(MovieReactions.movieId)
        return MovieReactions.select(MovieReactions.userId, count)
            .where {
                (MovieReactions.userId inList userIds) and
                    (MovieReactions.movieId inList myMovieIds) and
                    (MovieReactions.reaction eq Reaction.LIKE.name)
            }
            .groupBy(MovieReactions.userId)
            .associate { it[MovieReactions.userId] to it[count].toInt() }
    }

    companion object {
        /** People shown per movie; the counts still include everyone. */
        const val MAX_PEOPLE = 10

        /** Closest taste first; friends before strangers on a tie, then by name. */
        private val BEST_MATCH_FIRST =
            compareByDescending<MatchPersonDto> { it.sharedLikes }
                .thenByDescending { it.user.friendship == FriendshipState.FRIENDS }
                .thenBy { (it.user.displayName ?: it.user.username).lowercase() }
    }
}

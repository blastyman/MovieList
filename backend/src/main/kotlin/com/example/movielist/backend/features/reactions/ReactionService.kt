package com.example.movielist.backend.features.reactions

import com.example.movielist.backend.db.MovieReactions
import com.example.movielist.backend.db.MoviesCache
import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.db.query
import com.example.movielist.backend.features.matches.MatchService
import com.example.movielist.backend.features.movies.MovieService
import com.example.movielist.backend.features.movies.toSummary
import com.example.movielist.backend.plugins.ValidationException
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

class ReactionService(
    private val db: Database,
    private val movies: MovieService,
    private val matches: MatchService,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Likes or dislikes a movie, replacing any earlier reaction. Fetching the details first
     * checks the movie exists and puts it in movies_cache, so reaction lists can show its
     * title and poster without asking TMDB again.
     */
    suspend fun setReaction(userId: UUID, movieId: Int, reaction: Reaction): ReactionDto {
        movies.details(movieId)
        db.query {
            MovieReactions.upsert {
                it[MovieReactions.userId] = userId
                it[MovieReactions.movieId] = movieId
                it[MovieReactions.reaction] = reaction.name
                // Changing your mind moves the movie to the top of the list.
                it[createdAt] = OffsetDateTime.now(clock)
            }
        }
        if (reaction != Reaction.LIKE) return ReactionDto(movieId, reaction)
        val matched = matches.peopleWhoLike(userId, movieId)
        return ReactionDto(movieId, reaction, matched.people, matched.count)
    }

    suspend fun removeReaction(userId: UUID, movieId: Int) {
        db.query {
            MovieReactions.deleteWhere {
                (MovieReactions.userId eq userId) and (MovieReactions.movieId eq movieId)
            }
        }
    }

    suspend fun myReaction(userId: UUID, movieId: Int): Reaction? = db.query {
        MovieReactions.select(MovieReactions.reaction)
            .where { (MovieReactions.userId eq userId) and (MovieReactions.movieId eq movieId) }
            .singleOrNull()
            ?.let { Reaction.valueOf(it[MovieReactions.reaction]) }
    }

    suspend fun ids(userId: UUID): ReactionIdsDto = db.query {
        val rows = MovieReactions.select(MovieReactions.movieId, MovieReactions.reaction)
            .where { MovieReactions.userId eq userId }
            .toList()
        ReactionIdsDto(
            liked = rows.filter { it[MovieReactions.reaction] == Reaction.LIKE.name }.map { it[MovieReactions.movieId] },
            disliked = rows.filter { it[MovieReactions.reaction] == Reaction.DISLIKE.name }.map { it[MovieReactions.movieId] },
        )
    }

    suspend fun page(userId: UUID, type: Reaction?, limit: Int?, before: String?): ReactionPageDto {
        val pageSize = (limit ?: DEFAULT_LIMIT).also {
            if (it !in 1..MAX_LIMIT) throw ValidationException("limit must be between 1 and $MAX_LIMIT")
        }
        val cursor = before?.let(::parseCursor)

        val rows = db.query {
            MovieReactions
                .join(MoviesCache, JoinType.INNER, MovieReactions.movieId, MoviesCache.movieId)
                .selectAll()
                .where {
                    var condition = MovieReactions.userId eq userId
                    if (type != null) condition = condition and (MovieReactions.reaction eq type.name)
                    if (cursor != null) {
                        condition = condition and (
                            (MovieReactions.createdAt less cursor.reactedAt) or
                                ((MovieReactions.createdAt eq cursor.reactedAt) and (MovieReactions.movieId less cursor.movieId))
                            )
                    }
                    condition
                }
                .orderBy(MovieReactions.createdAt to SortOrder.DESC, MovieReactions.movieId to SortOrder.DESC)
                .limit(pageSize + 1)
                .toList()
        }

        val items = rows.take(pageSize).map { row ->
            ReactedMovieDto(
                movie = row[MoviesCache.json].toSummary(),
                reaction = Reaction.valueOf(row[MovieReactions.reaction]),
                reactedAt = row[MovieReactions.createdAt].toInstant().toString(),
            )
        }
        val nextCursor = if (rows.size > pageSize) items.last().let { "${it.reactedAt}_${it.movie.id}" } else null
        return ReactionPageDto(items, nextCursor)
    }

    private data class Cursor(val reactedAt: OffsetDateTime, val movieId: Int)

    /** Cursor format: `<ISO instant>_<movieId>`, the position of the last item already seen. */
    private fun parseCursor(raw: String): Cursor {
        val instant = raw.substringBeforeLast('_', "")
        val movieId = raw.substringAfterLast('_', "").toIntOrNull()
        return try {
            Cursor(Instant.parse(instant).atOffset(ZoneOffset.UTC), movieId ?: throw ValidationException("Invalid cursor"))
        } catch (e: DateTimeParseException) {
            throw ValidationException("Invalid cursor")
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 100
    }
}

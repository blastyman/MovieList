package com.example.movielist.backend.features.comments

import com.example.movielist.backend.db.Comments
import com.example.movielist.backend.db.Ratings
import com.example.movielist.backend.db.Users
import com.example.movielist.backend.db.query
import com.example.movielist.backend.features.movies.MovieService
import com.example.movielist.backend.plugins.ForbiddenException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Comments under movies, with one level of replies. A reply always hangs off a top-level
 * comment; replying to a reply joins the same thread. Deleting is soft, and deleting a
 * top-level comment hides its whole thread.
 */
class CommentService(
    private val db: Database,
    private val movies: MovieService,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Top-level comments of a movie, newest first, each with its reply count. */
    suspend fun page(viewerId: UUID, movieId: Int, limit: Int?, before: String?): CommentPageDto {
        val pageSize = validLimit(limit)
        val cursor = before?.let(::parseCursor)

        return db.query {
            val rows = commentsWithAuthors()
                .where {
                    var condition = (Comments.movieId eq movieId) and (Comments.deleted eq false) and
                        Comments.parentId.isNull()
                    if (cursor != null) {
                        condition = condition and (
                            (Comments.createdAt less cursor.createdAt) or
                                ((Comments.createdAt eq cursor.createdAt) and (Comments.id less cursor.id))
                            )
                    }
                    condition
                }
                .orderBy(Comments.createdAt to SortOrder.DESC, Comments.id to SortOrder.DESC)
                .limit(pageSize + 1)
                .toList()

            val shown = rows.take(pageSize)
            val replyCounts = replyCounts(shown.map { it[Comments.id] })
            val items = shown.map { it.toDto(viewerId, replyCounts[it[Comments.id]] ?: 0) }
            CommentPageDto(items, nextCursor(rows.size > pageSize, items))
        }
    }

    /** Replies in a thread, oldest first, so they read like a conversation. */
    suspend fun replies(viewerId: UUID, commentId: UUID, limit: Int?, after: String?): CommentPageDto {
        val pageSize = validLimit(limit)
        val cursor = after?.let(::parseCursor)

        return db.query {
            val thread = visibleComment(commentId)
            if (thread.parentId != null) throw ValidationException("Replies belong to top-level comments")

            val rows = commentsWithAuthors()
                .where {
                    var condition = (Comments.parentId eq commentId) and (Comments.deleted eq false)
                    if (cursor != null) {
                        condition = condition and (
                            (Comments.createdAt greater cursor.createdAt) or
                                ((Comments.createdAt eq cursor.createdAt) and (Comments.id greater cursor.id))
                            )
                    }
                    condition
                }
                .orderBy(Comments.createdAt to SortOrder.ASC, Comments.id to SortOrder.ASC)
                .limit(pageSize + 1)
                .toList()

            val items = rows.take(pageSize).map { it.toDto(viewerId, replyCount = 0) }
            CommentPageDto(items, nextCursor(rows.size > pageSize, items))
        }
    }

    /** Everything a reader can see: top-level comments plus replies in visible threads. */
    suspend fun count(movieId: Int): Int = db.query {
        val parent = Comments.alias("parent")
        val count = Comments.id.count()
        Comments.join(parent, JoinType.LEFT, Comments.parentId, parent[Comments.id])
            .select(count)
            .where {
                (Comments.movieId eq movieId) and (Comments.deleted eq false) and
                    (Comments.parentId.isNull() or (parent[Comments.deleted] eq false))
            }
            .single()[count]
            .toInt()
    }

    suspend fun create(authorId: UUID, movieId: Int, request: CommentRequest): CommentDto {
        val body = validBody(request.body)
        movies.details(movieId) // 404 for movies TMDB doesn't know
        return db.query { insert(authorId, movieId, parentId = null, body).toDto(authorId, replyCount = 0) }
    }

    /** Replying to a reply adds to the same thread rather than nesting deeper. */
    suspend fun reply(authorId: UUID, targetId: UUID, request: CommentRequest): CommentDto {
        val body = validBody(request.body)
        return db.query {
            val target = visibleComment(targetId)
            val threadId = target.parentId ?: target.id
            insert(authorId, target.movieId, threadId, body).toDto(authorId, replyCount = 0)
        }
    }

    suspend fun edit(editorId: UUID, commentId: UUID, request: CommentRequest): CommentDto {
        val body = validBody(request.body)
        return db.query {
            val comment = visibleComment(commentId)
            if (comment.authorId != editorId) throw ForbiddenException("You can only change your own comments")
            Comments.update({ Comments.id eq commentId }) {
                it[Comments.body] = body
                it[editedAt] = OffsetDateTime.now(clock)
            }
            val replyCount = if (comment.parentId == null) replyCounts(listOf(commentId))[commentId] ?: 0 else 0
            loadRow(commentId).toDto(editorId, replyCount)
        }
    }

    /**
     * Soft delete: the row stays (for moderation, and so the thesis can discuss it), but the
     * comment — and, for a top-level comment, its replies — disappear from every list.
     */
    suspend fun delete(userId: UUID, commentId: UUID) {
        db.query {
            val comment = visibleComment(commentId)
            if (comment.authorId != userId) throw ForbiddenException("You can only change your own comments")
            Comments.update({ Comments.id eq commentId }) { it[deleted] = true }
        }
    }

    private data class VisibleComment(val id: UUID, val movieId: Int, val authorId: UUID, val parentId: UUID?)

    /** The comment if a reader can see it: not deleted, and not in a deleted thread. Else 404. */
    private fun JdbcTransaction.visibleComment(commentId: UUID): VisibleComment {
        val notFound = NotFoundException("No comment with id $commentId")
        val row = Comments.select(Comments.id, Comments.movieId, Comments.userId, Comments.parentId, Comments.deleted)
            .where { Comments.id eq commentId }
            .singleOrNull() ?: throw notFound
        if (row[Comments.deleted]) throw notFound

        val parentId = row[Comments.parentId]
        if (parentId != null) {
            val parentDeleted = Comments.select(Comments.deleted).where { Comments.id eq parentId }.single()[Comments.deleted]
            if (parentDeleted) throw notFound
        }
        return VisibleComment(row[Comments.id], row[Comments.movieId], row[Comments.userId], parentId)
    }

    private fun JdbcTransaction.insert(authorId: UUID, movieId: Int, parentId: UUID?, body: String): ResultRow {
        val id = Comments.insert {
            it[Comments.movieId] = movieId
            it[userId] = authorId
            it[Comments.body] = body
            it[Comments.parentId] = parentId
            it[createdAt] = OffsetDateTime.now(clock)
        }[Comments.id]
        return loadRow(id)
    }

    /** Visible replies per top-level comment id. */
    private fun JdbcTransaction.replyCounts(commentIds: List<UUID>): Map<UUID, Int> {
        if (commentIds.isEmpty()) return emptyMap()
        val count = Comments.id.count()
        return Comments.select(Comments.parentId, count)
            .where { (Comments.parentId inList commentIds) and (Comments.deleted eq false) }
            .groupBy(Comments.parentId)
            .associate { it[Comments.parentId]!! to it[count].toInt() }
    }

    private fun JdbcTransaction.loadRow(commentId: UUID): ResultRow =
        commentsWithAuthors().where { Comments.id eq commentId }.single()

    /** Comments with their author and the author's rating of the same movie (if any). */
    private fun commentsWithAuthors() =
        Comments.join(Users, JoinType.INNER, Comments.userId, Users.id)
            .join(Ratings, JoinType.LEFT, Comments.userId, Ratings.userId) { Ratings.movieId eq Comments.movieId }
            .select(
                Comments.id, Comments.movieId, Comments.parentId, Comments.body, Comments.createdAt,
                Comments.editedAt, Users.id, Users.username, Users.displayName, Ratings.score,
            )

    private fun ResultRow.toDto(viewerId: UUID, replyCount: Int) = CommentDto(
        id = this[Comments.id].toString(),
        movieId = this[Comments.movieId],
        author = CommentAuthorDto(
            id = this[Users.id].toString(),
            username = this[Users.username],
            displayName = this[Users.displayName],
        ),
        body = this[Comments.body],
        createdAt = this[Comments.createdAt].toInstant().toString(),
        editedAt = this[Comments.editedAt]?.toInstant()?.toString(),
        isMine = this[Users.id] == viewerId,
        parentId = this[Comments.parentId]?.toString(),
        replyCount = replyCount,
        // getOrNull: LEFT JOIN, so the score is missing when the author never rated the movie.
        authorRating = getOrNull(Ratings.score)?.toInt(),
    )

    private fun validBody(raw: String): String {
        val body = raw.trim()
        if (body.isEmpty()) throw ValidationException("Comment can't be empty")
        if (body.length > MAX_LENGTH) throw ValidationException("Comment must be at most $MAX_LENGTH characters")
        return body
    }

    private fun validLimit(limit: Int?): Int =
        (limit ?: DEFAULT_LIMIT).also {
            if (it !in 1..MAX_LIMIT) throw ValidationException("limit must be between 1 and $MAX_LIMIT")
        }

    private fun nextCursor(hasMore: Boolean, items: List<CommentDto>): String? =
        if (hasMore) items.last().let { "${it.createdAt}_${it.id}" } else null

    private data class Cursor(val createdAt: OffsetDateTime, val id: UUID)

    /** Cursor format: `<ISO instant>_<comment id>`, the last comment already seen. */
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

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
        const val MAX_LENGTH = 1000 // matches the CHECK constraint in V1__init.sql
    }
}

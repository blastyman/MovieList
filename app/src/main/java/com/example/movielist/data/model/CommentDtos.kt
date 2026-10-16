package com.example.movielist.data.model

import com.example.movielist.domain.model.Comment
import com.example.movielist.domain.model.CommentPage
import java.time.Instant

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/comments/CommentDtos.kt

data class CommentRequest(val body: String)

data class CommentAuthorDto(val id: String, val username: String, val displayName: String?)

data class CommentDto(
    val id: String,
    val movieId: Int,
    val author: CommentAuthorDto,
    val body: String,
    val createdAt: String,
    val editedAt: String?,
    val isMine: Boolean,
    val parentId: String?,
    val replyCount: Int,
    val authorRating: Int?,
) {
    fun toComment() =
        Comment(
            id = id,
            authorName = author.displayName?.takeIf { it.isNotBlank() } ?: author.username,
            body = body,
            createdAt = Instant.parse(createdAt),
            edited = editedAt != null,
            isMine = isMine,
            parentId = parentId,
            replyCount = replyCount,
            authorScore = authorRating,
        )
}

data class CommentPageDto(val items: List<CommentDto>?, val nextCursor: String?) {
    fun toCommentPage() = CommentPage(items.orEmpty().map { it.toComment() }, nextCursor)
}

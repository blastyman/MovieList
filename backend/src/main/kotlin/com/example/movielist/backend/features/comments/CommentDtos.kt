package com.example.movielist.backend.features.comments

import kotlinx.serialization.Serializable

@Serializable
data class CommentRequest(val body: String)

@Serializable
data class CommentAuthorDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String? = null,
    val avatarEmoji: String? = null,
    val avatarColor: String? = null,
)

@Serializable
data class CommentDto(
    val id: String,
    val movieId: Int,
    val author: CommentAuthorDto,
    val body: String,
    val createdAt: String,
    /** Null if never edited. */
    val editedAt: String?,
    /** True when the signed-in user wrote it, i.e. may edit or delete it. */
    val isMine: Boolean,
    /** The top-level comment this replies to; null for a top-level comment. */
    val parentId: String?,
    /** Visible replies; always 0 for a reply (replies are one level deep). */
    val replyCount: Int,
    /** The author's own 1–10 rating of this movie, if they rated it. */
    val authorRating: Int?,
)

/**
 * A page of comments. Top-level comments come newest first (pass [nextCursor] as `before`);
 * replies come oldest first, like a conversation (pass [nextCursor] as `after`).
 */
@Serializable
data class CommentPageDto(
    val items: List<CommentDto>,
    val nextCursor: String?,
)

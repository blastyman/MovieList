package com.example.movielist.domain.model

import java.time.Instant

data class Comment(
    val id: String,
    val authorName: String,
    val body: String,
    val createdAt: Instant,
    val edited: Boolean,
    /** The signed-in user wrote it, so may edit or delete it. */
    val isMine: Boolean,
    /** Top-level comment this replies to; null for a top-level comment. */
    val parentId: String? = null,
    /** Replies under a top-level comment; always 0 for a reply (one level deep). */
    val replyCount: Int = 0,
    /** The author's own 1–10 rating of this movie, if they rated it. */
    val authorScore: Int? = null,
    val authorAvatar: Avatar = Avatar.NONE,
) {
    val isReply: Boolean
        get() = parentId != null

    /** The thread a reply to this comment goes into: itself, or its parent for a reply. */
    val threadId: String
        get() = parentId ?: id
}

/** Top-level comments newest first, replies oldest first. [nextCursor] is null on the last page. */
data class CommentPage(val comments: List<Comment>, val nextCursor: String?)

/** Same limit as the server and its database constraint. */
const val MAX_COMMENT_LENGTH = 1000

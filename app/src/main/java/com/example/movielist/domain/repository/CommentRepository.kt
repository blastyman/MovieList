package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Comment
import com.example.movielist.domain.model.CommentPage

interface CommentRepository {
    /** Newest first; pass the previous page's cursor as [before] for older comments. */
    suspend fun load(movieId: Int, before: String? = null): CommentPage

    suspend fun post(movieId: Int, body: String): Comment

    /** Replies in [commentId]'s thread, oldest first; pass the previous page's cursor as [after]. */
    suspend fun loadReplies(commentId: String, after: String? = null): CommentPage

    /** Replying to a reply adds to the same thread (the server takes care of that). */
    suspend fun reply(commentId: String, body: String): Comment

    suspend fun edit(commentId: String, body: String): Comment

    suspend fun delete(commentId: String)
}

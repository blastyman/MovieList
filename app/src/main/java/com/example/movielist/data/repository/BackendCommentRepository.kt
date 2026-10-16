package com.example.movielist.data.repository

import com.example.movielist.data.model.CommentRequest
import com.example.movielist.data.remote.CommentApi
import com.example.movielist.domain.model.Comment
import com.example.movielist.domain.model.CommentPage
import com.example.movielist.domain.repository.CommentRepository

class BackendCommentRepository(private val api: CommentApi) : CommentRepository {
    override suspend fun load(movieId: Int, before: String?): CommentPage =
        api.page(movieId, before).toCommentPage()

    override suspend fun post(movieId: Int, body: String): Comment =
        api.post(movieId, CommentRequest(body)).toComment()

    override suspend fun loadReplies(commentId: String, after: String?): CommentPage =
        api.replies(commentId, after).toCommentPage()

    override suspend fun reply(commentId: String, body: String): Comment =
        api.reply(commentId, CommentRequest(body)).toComment()

    override suspend fun edit(commentId: String, body: String): Comment =
        api.edit(commentId, CommentRequest(body)).toComment()

    override suspend fun delete(commentId: String) = api.delete(commentId)
}

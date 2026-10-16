package com.example.movielist.ui.comments

import com.example.movielist.domain.model.Comment

/** Everything the comments UI can ask for. [CommentsViewModel] implements it; previews and tests can fake it. */
interface CommentsActions {
    fun onDraftChange(text: String)

    fun post()

    fun loadMore()

    fun retry()

    fun toggleReplies(commentId: String)

    fun loadMoreReplies(commentId: String)

    fun startReply(target: Comment)

    fun onReplyDraftChange(text: String)

    fun sendReply()

    fun cancelReply()

    fun startEdit(commentId: String)

    fun onEditDraftChange(text: String)

    fun saveEdit()

    fun cancelEdit()

    fun askDelete(commentId: String)

    fun confirmDelete()

    fun cancelDelete()
}

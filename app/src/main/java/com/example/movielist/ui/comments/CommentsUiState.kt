package com.example.movielist.ui.comments

import com.example.movielist.domain.model.Comment

data class CommentsUiState(
    val movieId: Int? = null,
    /** Top-level comments, newest first. */
    val comments: List<Comment> = emptyList(),
    /** Cursor for older comments; null when everything is loaded. */
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** The first page failed; shown with a retry button instead of the list. */
    val loadError: String? = null,
    val draft: String = "",
    val isPosting: Boolean = false,
    /** Reply threads by top-level comment id. Only threads the user opened are here. */
    val threads: Map<String, ThreadUiState> = emptyMap(),
    /** The thread the reply box is open in. */
    val replyingTo: ReplyTarget? = null,
    val replyDraft: String = "",
    val isReplying: Boolean = false,
    val editingId: String? = null,
    val editDraft: String = "",
    /** Waiting for the user to confirm deleting this comment or reply. */
    val confirmDeleteId: String? = null,
    /** One-off message (a failed post, edit or delete), cleared by [CommentsViewModel.messageShown]. */
    val message: String? = null,
) {
    val canPost: Boolean
        get() = draft.isNotBlank() && !isPosting

    val canSendReply: Boolean
        get() = replyingTo != null && replyDraft.isNotBlank() && !isReplying

    fun thread(commentId: String): ThreadUiState = threads[commentId] ?: ThreadUiState()

    /** A top-level comment or a loaded reply. */
    fun findComment(commentId: String): Comment? =
        comments.firstOrNull { it.id == commentId }
            ?: threads.values.firstNotNullOfOrNull { thread -> thread.replies.firstOrNull { it.id == commentId } }
}

data class ThreadUiState(
    val expanded: Boolean = false,
    /** Oldest first. */
    val replies: List<Comment> = emptyList(),
    val nextCursor: String? = null,
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
)

/** Where a reply goes ([threadId]) and who it answers, for the "Replying to …" label. */
data class ReplyTarget(val threadId: String, val authorName: String)

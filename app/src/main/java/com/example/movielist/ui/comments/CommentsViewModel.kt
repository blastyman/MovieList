package com.example.movielist.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.Comment
import com.example.movielist.domain.model.MAX_COMMENT_LENGTH
import com.example.movielist.domain.repository.CommentRepository
import com.example.movielist.ui.utils.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Comments of the movie shown in details, with one level of replies. One instance follows
 * whichever movie is open.
 */
class CommentsViewModel(private val repository: CommentRepository) : ViewModel(), CommentsActions {
    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiStateFlow: StateFlow<CommentsUiState> = _uiState.asStateFlow()
    val uiState: CommentsUiState
        get() = _uiState.value

    /** Loads for the current movie (pages and reply threads); all cancelled when another movie is shown. */
    private val loadJobs = mutableListOf<Job>()

    /** Loads the newest comments; called whenever details open a movie, so they are always fresh. */
    fun show(movieId: Int) {
        loadJobs.forEach { it.cancel() }
        loadJobs.clear()
        // Reopening the same movie keeps a half-written comment.
        val draft = if (uiState.movieId == movieId) uiState.draft else ""
        _uiState.value = CommentsUiState(movieId = movieId, isLoading = true, draft = draft)
        launchLoad {
            try {
                val page = repository.load(movieId)
                _uiState.update {
                    it.copy(comments = page.comments, nextCursor = page.nextCursor, isLoading = false)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = exception.toUserMessage()) }
            }
        }
    }

    override fun retry() {
        uiState.movieId?.let(::show)
    }

    override fun loadMore() {
        val state = uiState
        val movieId = state.movieId ?: return
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        launchLoad {
            try {
                val page = repository.load(movieId, before = cursor)
                _uiState.update {
                    it.copy(
                        // A comment posted meanwhile may also be on this page; keep one copy.
                        comments = (it.comments + page.comments).distinctBy { comment -> comment.id },
                        nextCursor = page.nextCursor,
                        isLoadingMore = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(isLoadingMore = false, message = "Couldn't load more. ${exception.toUserMessage()}")
                }
            }
        }
    }

    override fun onDraftChange(text: String) {
        _uiState.update { it.copy(draft = text.take(MAX_COMMENT_LENGTH)) }
    }

    /** Posting is not optimistic: the comment appears once the server has it (with its id and time). */
    override fun post() {
        val state = uiState
        val movieId = state.movieId ?: return
        if (!state.canPost) return
        val body = state.draft.trim()
        _uiState.update { it.copy(isPosting = true) }
        viewModelScope.launch {
            try {
                val comment = repository.post(movieId, body)
                _uiState.update {
                    if (it.movieId != movieId) it
                    else it.copy(comments = listOf(comment) + it.comments, draft = "", isPosting = false)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // The draft stays, so nothing the user typed is lost.
                _uiState.update {
                    it.copy(isPosting = false, message = "Couldn't post your comment. ${exception.toUserMessage()}")
                }
            }
        }
    }

    // ───────────── Replies ─────────────

    /** Shows or hides a thread's replies, loading the first page the first time. */
    override fun toggleReplies(commentId: String) {
        val thread = uiState.thread(commentId)
        if (thread.expanded) {
            updateThread(commentId) { it.copy(expanded = false) }
            if (uiState.replyingTo?.threadId == commentId) cancelReply()
        } else {
            updateThread(commentId) { it.copy(expanded = true) }
            if (!thread.loaded && !thread.isLoading) loadReplies(commentId)
        }
    }

    override fun loadMoreReplies(commentId: String) {
        val thread = uiState.thread(commentId)
        if (thread.nextCursor != null && !thread.isLoading) loadReplies(commentId, after = thread.nextCursor)
    }

    private fun loadReplies(commentId: String, after: String? = null) {
        updateThread(commentId) { it.copy(isLoading = true) }
        launchLoad {
            try {
                val page = repository.loadReplies(commentId, after)
                updateThread(commentId) {
                    it.copy(
                        // A reply sent meanwhile may also be on this page; keep one copy.
                        replies = (it.replies + page.comments).distinctBy { reply -> reply.id },
                        nextCursor = page.nextCursor,
                        loaded = true,
                        isLoading = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                updateThread(commentId) { it.copy(isLoading = false) }
                _uiState.update { it.copy(message = "Couldn't load replies. ${exception.toUserMessage()}") }
            }
        }
    }

    /**
     * Opens the reply box under [target]'s thread. Replying to a reply goes into the same
     * thread and starts with "@name ", so it's clear who is being answered.
     */
    override fun startReply(target: Comment) {
        val threadId = target.threadId
        val mention = if (target.isReply) "@${target.authorName} " else ""
        _uiState.update {
            it.copy(
                replyingTo = ReplyTarget(threadId, target.authorName),
                replyDraft = if (it.replyingTo?.threadId == threadId && it.replyDraft.isNotBlank()) it.replyDraft
                    else mention,
            )
        }
        if (!uiState.thread(threadId).expanded) toggleReplies(threadId)
    }

    override fun onReplyDraftChange(text: String) {
        _uiState.update { it.copy(replyDraft = text.take(MAX_COMMENT_LENGTH)) }
    }

    override fun cancelReply() {
        _uiState.update { it.copy(replyingTo = null, replyDraft = "") }
    }

    override fun sendReply() {
        val state = uiState
        val target = state.replyingTo ?: return
        if (!state.canSendReply) return
        val body = state.replyDraft.trim()
        _uiState.update { it.copy(isReplying = true) }
        viewModelScope.launch {
            try {
                val reply = repository.reply(target.threadId, body)
                updateThread(target.threadId) { it.copy(replies = it.replies + reply) }
                _uiState.update {
                    it.copy(
                        comments = it.comments.replace(target.threadId) { c -> c.copy(replyCount = c.replyCount + 1) },
                        replyingTo = null,
                        replyDraft = "",
                        isReplying = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // The box stays open with the text, so nothing is lost.
                _uiState.update {
                    it.copy(isReplying = false, message = "Couldn't send your reply. ${exception.toUserMessage()}")
                }
            }
        }
    }

    // ───────────── Editing and deleting (comments and replies alike) ─────────────

    override fun startEdit(commentId: String) {
        val comment = uiState.findComment(commentId)?.takeIf { it.isMine } ?: return
        _uiState.update { it.copy(editingId = comment.id, editDraft = comment.body) }
    }

    override fun onEditDraftChange(text: String) {
        _uiState.update { it.copy(editDraft = text.take(MAX_COMMENT_LENGTH)) }
    }

    override fun cancelEdit() {
        _uiState.update { it.copy(editingId = null, editDraft = "") }
    }

    override fun saveEdit() {
        val state = uiState
        val commentId = state.editingId ?: return
        val body = state.editDraft.trim()
        if (body.isEmpty()) return
        val original = state.findComment(commentId) ?: return
        if (body == original.body) return cancelEdit()

        // Shown right away; the server's copy replaces it (or the original comes back on failure).
        _uiState.update {
            it.withComment(commentId) { comment -> comment.copy(body = body, edited = true) }
                .copy(editingId = null, editDraft = "")
        }
        viewModelScope.launch {
            try {
                val saved = repository.edit(commentId, body)
                // Keep the reply count we know; the thread may have changed since.
                _uiState.update { it.withComment(commentId) { current -> saved.copy(replyCount = current.replyCount) } }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.withComment(commentId) { current -> original.copy(replyCount = current.replyCount) }
                        .copy(message = "Couldn't save your edit. ${exception.toUserMessage()}")
                }
            }
        }
    }

    override fun askDelete(commentId: String) {
        _uiState.update { it.copy(confirmDeleteId = commentId) }
    }

    override fun cancelDelete() {
        _uiState.update { it.copy(confirmDeleteId = null) }
    }

    /** Removed at once; put back in the same place if the server refuses. */
    override fun confirmDelete() {
        val state = uiState
        val commentId = state.confirmDeleteId ?: return
        val removed = state.findComment(commentId) ?: return cancelDelete()
        val before = state

        _uiState.update { it.withoutComment(removed).copy(confirmDeleteId = null) }
        viewModelScope.launch {
            try {
                repository.delete(commentId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.withRestored(removed, before)
                        .copy(message = "Couldn't delete your comment. ${exception.toUserMessage()}")
                }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    // ───────────── Helpers ─────────────

    private fun launchLoad(block: suspend () -> Unit) {
        loadJobs.removeAll { it.isCompleted }
        loadJobs += viewModelScope.launch { block() }
    }

    private fun updateThread(commentId: String, transform: (ThreadUiState) -> ThreadUiState) {
        _uiState.update { it.copy(threads = it.threads + (commentId to transform(it.thread(commentId)))) }
    }

    /** Applies [transform] wherever the comment is: the top-level list or a thread. */
    private fun CommentsUiState.withComment(commentId: String, transform: (Comment) -> Comment) =
        copy(
            comments = comments.replace(commentId, transform),
            threads = threads.mapValues { (_, thread) -> thread.copy(replies = thread.replies.replace(commentId, transform)) },
        )

    private fun CommentsUiState.withoutComment(comment: Comment): CommentsUiState {
        val parentId = comment.parentId
        return if (parentId == null) {
            // A deleted top-level comment takes its thread with it, like on the server.
            copy(
                comments = comments.filterNot { it.id == comment.id },
                threads = threads - comment.id,
                replyingTo = replyingTo?.takeIf { it.threadId != comment.id },
            )
        } else {
            copy(
                comments = comments.replace(parentId) { it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0)) },
                threads = threads + (parentId to thread(parentId).let { t -> t.copy(replies = t.replies.filterNot { it.id == comment.id }) }),
            )
        }
    }

    /** Undoes [withoutComment], putting [comment] back where it was in [before]. */
    private fun CommentsUiState.withRestored(comment: Comment, before: CommentsUiState): CommentsUiState {
        val parentId = comment.parentId
        return if (parentId == null) {
            val index = before.comments.indexOfFirst { it.id == comment.id }
            copy(
                comments = comments.toMutableList().apply { add(index.coerceIn(0, size), comment) },
                threads = before.threads[comment.id]?.let { threads + (comment.id to it) } ?: threads,
            )
        } else {
            val index = before.thread(parentId).replies.indexOfFirst { it.id == comment.id }
            copy(
                comments = comments.replace(parentId) { it.copy(replyCount = it.replyCount + 1) },
                threads = threads + (parentId to thread(parentId).let { t ->
                    t.copy(replies = t.replies.toMutableList().apply { add(index.coerceIn(0, size), comment) })
                }),
            )
        }
    }

    private fun List<Comment>.replace(id: String, transform: (Comment) -> Comment): List<Comment> =
        map { if (it.id == id) transform(it) else it }
}

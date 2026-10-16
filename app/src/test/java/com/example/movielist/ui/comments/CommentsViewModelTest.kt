package com.example.movielist.ui.comments

import com.example.movielist.domain.model.Comment
import com.example.movielist.domain.model.CommentPage
import com.example.movielist.domain.model.MAX_COMMENT_LENGTH
import com.example.movielist.domain.repository.CommentRepository
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showLoadsNewestCommentsForThatMovie() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 3)
            val viewModel = CommentsViewModel(repo)

            viewModel.show(1)
            assertTrue(viewModel.uiState.isLoading)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.isLoading)
            assertEquals(listOf("c3", "c2", "c1"), viewModel.uiState.comments.map { it.body })
        }

    @Test
    fun loadMoreAppendsOlderPagesUntilTheEnd() =
        runTest(dispatcher) {
            val viewModel = CommentsViewModel(FakeComments(movieId = 1, count = 5, pageSize = 2))
            viewModel.show(1)
            advanceUntilIdle()

            while (viewModel.uiState.nextCursor != null) {
                viewModel.loadMore()
                advanceUntilIdle()
            }

            assertEquals(listOf("c5", "c4", "c3", "c2", "c1"), viewModel.uiState.comments.map { it.body })
        }

    @Test
    fun postingAddsTheCommentOnTopAndClearsTheDraft() =
        runTest(dispatcher) {
            val viewModel = CommentsViewModel(FakeComments(movieId = 1, count = 1))
            viewModel.show(1)
            advanceUntilIdle()

            viewModel.onDraftChange("  Loved it  ")
            assertTrue(viewModel.uiState.canPost)
            viewModel.post()
            advanceUntilIdle()

            assertEquals("Loved it", viewModel.uiState.comments.first().body)
            assertEquals("", viewModel.uiState.draft)
        }

    @Test
    fun blankDraftCannotBePostedAndLongDraftIsCut() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 0)
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            advanceUntilIdle()

            viewModel.onDraftChange("   ")
            viewModel.post()
            advanceUntilIdle()
            assertEquals(0, repo.posts)

            viewModel.onDraftChange("x".repeat(MAX_COMMENT_LENGTH + 50))
            assertEquals(MAX_COMMENT_LENGTH, viewModel.uiState.draft.length)
        }

    @Test
    fun failedPostKeepsTheDraftAndShowsAMessage() =
        runTest(dispatcher) {
            val viewModel = CommentsViewModel(FakeComments(movieId = 1, count = 0, failWrites = true))
            viewModel.show(1)
            advanceUntilIdle()

            viewModel.onDraftChange("my thoughts")
            viewModel.post()
            advanceUntilIdle()

            assertEquals("my thoughts", viewModel.uiState.draft)
            assertTrue(viewModel.uiState.comments.isEmpty())
            assertTrue(viewModel.uiState.message.orEmpty().startsWith("Couldn't post your comment."))
        }

    @Test
    fun editShowsAtOnceAndFailureRestoresTheOriginal() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 1, mine = true)
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            advanceUntilIdle()
            val id = viewModel.uiState.comments.single().id

            viewModel.startEdit(id)
            assertEquals("c1", viewModel.uiState.editDraft)
            viewModel.onEditDraftChange("c1 fixed")
            viewModel.saveEdit()
            assertEquals("c1 fixed", viewModel.uiState.comments.single().body)
            assertNull(viewModel.uiState.editingId)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.comments.single().edited)

            repo.failWrites = true
            viewModel.startEdit(id)
            viewModel.onEditDraftChange("will fail")
            viewModel.saveEdit()
            advanceUntilIdle()
            assertEquals("c1 fixed", viewModel.uiState.comments.single().body)
            assertTrue(viewModel.uiState.message.orEmpty().startsWith("Couldn't save your edit."))
        }

    @Test
    fun othersCommentsCannotBeEdited() =
        runTest(dispatcher) {
            val viewModel = CommentsViewModel(FakeComments(movieId = 1, count = 1, mine = false))
            viewModel.show(1)
            advanceUntilIdle()

            viewModel.startEdit(viewModel.uiState.comments.single().id)
            assertNull(viewModel.uiState.editingId)
        }

    @Test
    fun deleteAsksFirstAndFailurePutsTheCommentBackInPlace() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 3, mine = true)
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            advanceUntilIdle()
            val middle = viewModel.uiState.comments[1].id

            viewModel.askDelete(middle)
            assertEquals(3, viewModel.uiState.comments.size) // nothing yet
            viewModel.cancelDelete()
            assertNull(viewModel.uiState.confirmDeleteId)

            repo.failWrites = true
            viewModel.askDelete(middle)
            viewModel.confirmDelete()
            assertEquals(2, viewModel.uiState.comments.size) // gone at once
            advanceUntilIdle()
            assertEquals(listOf("c3", "c2", "c1"), viewModel.uiState.comments.map { it.body })

            repo.failWrites = false
            viewModel.askDelete(middle)
            viewModel.confirmDelete()
            advanceUntilIdle()
            assertEquals(listOf("c3", "c1"), viewModel.uiState.comments.map { it.body })
        }

    @Test
    fun switchingMovieReplacesCommentsButReopeningKeepsTheDraft() =
        runTest(dispatcher) {
            val viewModel = CommentsViewModel(FakeComments(movieId = 1, count = 2))
            viewModel.show(1)
            advanceUntilIdle()
            viewModel.onDraftChange("half-written")

            viewModel.show(1)
            assertEquals("half-written", viewModel.uiState.draft)

            viewModel.show(2)
            advanceUntilIdle()
            assertEquals("", viewModel.uiState.draft)
            assertTrue(viewModel.uiState.comments.isEmpty())
        }

    @Test
    fun loadFailureCanBeRetried() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 1, failLoads = 1)
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            advanceUntilIdle()
            assertEquals("Can't reach the server. Check your connection.", viewModel.uiState.loadError)

            viewModel.retry()
            advanceUntilIdle()
            assertNull(viewModel.uiState.loadError)
            assertEquals(1, viewModel.uiState.comments.size)
        }

    // ───────────── Replies ─────────────

    @Test
    fun openingAThreadLoadsItsRepliesOnceAndHidingKeepsThem() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 1)
            repo.addReplies("id1", "r1", "r2")
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.comments.single().replyCount)

            viewModel.toggleReplies("id1")
            advanceUntilIdle()
            assertEquals(listOf("r1", "r2"), viewModel.uiState.thread("id1").replies.map { it.body })

            viewModel.toggleReplies("id1")
            assertFalse(viewModel.uiState.thread("id1").expanded)
            viewModel.toggleReplies("id1")
            advanceUntilIdle()
            assertEquals(1, repo.replyLoads) // not loaded again
        }

    @Test
    fun replyingAddsToTheThreadAndCountsIt() =
        runTest(dispatcher) {
            val viewModel = CommentsViewModel(FakeComments(movieId = 1, count = 1))
            viewModel.show(1)
            advanceUntilIdle()
            val top = viewModel.uiState.comments.single()

            viewModel.startReply(top)
            assertEquals(ReplyTarget("id1", "alice"), viewModel.uiState.replyingTo)
            assertEquals("", viewModel.uiState.replyDraft)
            assertTrue(viewModel.uiState.thread("id1").expanded) // opens the thread too

            viewModel.onReplyDraftChange("  agreed  ")
            viewModel.sendReply()
            advanceUntilIdle()

            assertEquals(listOf("agreed"), viewModel.uiState.thread("id1").replies.map { it.body })
            assertEquals(1, viewModel.uiState.comments.single().replyCount)
            assertNull(viewModel.uiState.replyingTo)
            assertEquals("", viewModel.uiState.replyDraft)
        }

    @Test
    fun replyingToAReplyMentionsItsAuthorInTheSameThread() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 1)
            repo.addReplies("id1", "r1")
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            viewModel.toggleReplies("id1")
            advanceUntilIdle()

            viewModel.startReply(viewModel.uiState.thread("id1").replies.single())

            assertEquals("id1", viewModel.uiState.replyingTo?.threadId)
            assertEquals("@bob ", viewModel.uiState.replyDraft)
        }

    @Test
    fun failedReplyKeepsTheBoxAndText() =
        runTest(dispatcher) {
            val viewModel = CommentsViewModel(FakeComments(movieId = 1, count = 1, failWrites = true))
            viewModel.show(1)
            advanceUntilIdle()

            viewModel.startReply(viewModel.uiState.comments.single())
            viewModel.onReplyDraftChange("my reply")
            viewModel.sendReply()
            advanceUntilIdle()

            assertEquals("id1", viewModel.uiState.replyingTo?.threadId)
            assertEquals("my reply", viewModel.uiState.replyDraft)
            assertEquals(0, viewModel.uiState.comments.single().replyCount)
            assertTrue(viewModel.uiState.message.orEmpty().startsWith("Couldn't send your reply."))
        }

    @Test
    fun repliesCanBeEditedAndDeletedWithRollback() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 1)
            repo.addReplies("id1", "first", "second", mine = true)
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            viewModel.toggleReplies("id1")
            advanceUntilIdle()
            val first = viewModel.uiState.thread("id1").replies.first()

            viewModel.startEdit(first.id)
            viewModel.onEditDraftChange("first, edited")
            viewModel.saveEdit()
            advanceUntilIdle()
            assertEquals("first, edited", viewModel.uiState.thread("id1").replies.first().body)
            assertEquals(2, viewModel.uiState.comments.single().replyCount) // untouched by the edit

            repo.failWrites = true
            viewModel.askDelete(first.id)
            viewModel.confirmDelete()
            assertEquals(1, viewModel.uiState.comments.single().replyCount) // gone at once
            advanceUntilIdle()
            assertEquals(listOf("first, edited", "second"), viewModel.uiState.thread("id1").replies.map { it.body })
            assertEquals(2, viewModel.uiState.comments.single().replyCount)

            repo.failWrites = false
            viewModel.askDelete(first.id)
            viewModel.confirmDelete()
            advanceUntilIdle()
            assertEquals(listOf("second"), viewModel.uiState.thread("id1").replies.map { it.body })
            assertEquals(1, viewModel.uiState.comments.single().replyCount)
        }

    @Test
    fun deletingACommentTakesItsThreadAndOpenReplyBoxWithIt() =
        runTest(dispatcher) {
            val repo = FakeComments(movieId = 1, count = 1, mine = true)
            repo.addReplies("id1", "r1")
            val viewModel = CommentsViewModel(repo)
            viewModel.show(1)
            advanceUntilIdle()
            viewModel.startReply(viewModel.uiState.comments.single())
            advanceUntilIdle()

            viewModel.askDelete("id1")
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.comments.isEmpty())
            assertTrue(viewModel.uiState.threads.isEmpty())
            assertNull(viewModel.uiState.replyingTo)
        }

    /**
     * In-memory comments for [movieId]: c1 (oldest) … c<count> (newest), by alice, plus replies
     * added with [addReplies] (by bob, unless mine).
     */
    private class FakeComments(
        private val movieId: Int,
        count: Int,
        private val pageSize: Int = 20,
        mine: Boolean = false,
        var failWrites: Boolean = false,
        private var failLoads: Int = 0,
    ) : CommentRepository {
        private val comments =
            (1..count).map { Comment("id$it", "alice", "c$it", Instant.ofEpochSecond(it.toLong()), false, mine) }.toMutableList()
        private val replies = mutableListOf<Comment>()
        var posts = 0
        var replyLoads = 0

        fun addReplies(parentId: String, vararg bodies: String, mine: Boolean = false) {
            bodies.forEach { body ->
                val n = replies.size + 1
                replies += Comment("r$n", if (mine) "me" else "bob", body, Instant.ofEpochSecond(100L + n), false, mine, parentId)
            }
        }

        private fun withCount(comment: Comment) = comment.copy(replyCount = replies.count { it.parentId == comment.id })

        override suspend fun load(movieId: Int, before: String?): CommentPage {
            if (failLoads-- > 0) throw IOException("Offline")
            if (movieId != this.movieId) return CommentPage(emptyList(), null)
            val newestFirst = comments.sortedByDescending { it.createdAt }
            val start = before?.let { cursor -> newestFirst.indexOfFirst { it.id == cursor } + 1 } ?: 0
            val page = newestFirst.drop(start).take(pageSize)
            val next = if (start + page.size < newestFirst.size) page.last().id else null
            return CommentPage(page.map(::withCount), next)
        }

        override suspend fun post(movieId: Int, body: String): Comment {
            if (failWrites) throw IOException("Offline")
            posts++
            return Comment("new$posts", "me", body, Instant.now(), false, true).also { comments += it }
        }

        override suspend fun loadReplies(commentId: String, after: String?): CommentPage {
            replyLoads++
            return CommentPage(replies.filter { it.parentId == commentId }, null)
        }

        override suspend fun reply(commentId: String, body: String): Comment {
            if (failWrites) throw IOException("Offline")
            return Comment("reply${replies.size + 1}", "me", body, Instant.now(), false, true, commentId)
                .also { replies += it }
        }

        override suspend fun edit(commentId: String, body: String): Comment {
            if (failWrites) throw IOException("Offline")
            val index = comments.indexOfFirst { it.id == commentId }
            if (index >= 0) return comments[index].copy(body = body, edited = true).also { comments[index] = it }
            val replyIndex = replies.indexOfFirst { it.id == commentId }
            return replies[replyIndex].copy(body = body, edited = true).also { replies[replyIndex] = it }
        }

        override suspend fun delete(commentId: String) {
            if (failWrites) throw IOException("Offline")
            comments.removeAll { it.id == commentId }
            replies.removeAll { it.id == commentId || it.parentId == commentId }
        }
    }
}

package com.example.movielist.backend

import com.example.movielist.backend.features.comments.CommentDto
import com.example.movielist.backend.features.comments.CommentPageDto
import com.example.movielist.backend.features.comments.CommentRequest
import com.example.movielist.backend.features.movies.MovieDetailsResponse
import com.example.movielist.backend.features.ratings.SetRatingRequest
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentTest {

    private suspend fun HttpClient.comment(user: TestUser, movieId: Int, body: String): HttpResponse =
        post("/movies/$movieId/comments") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(CommentRequest(body))
        }

    private suspend fun HttpClient.edit(user: TestUser, commentId: String, body: String): HttpResponse =
        patch("/comments/$commentId") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(CommentRequest(body))
        }

    private suspend fun HttpClient.list(user: TestUser, movieId: Int, query: String = ""): CommentPageDto =
        get("/movies/$movieId/comments?$query") { bearerAuth(user.token) }.body()

    @Test
    fun `posting returns the comment with its author, trimmed`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val movie = uniqueMovieId()

        val response = client.comment(alice, movie, "  Great movie!  ")
        assertEquals(HttpStatusCode.Created, response.status)

        val comment = response.body<CommentDto>()
        assertEquals("Great movie!", comment.body)
        assertEquals(movie, comment.movieId)
        assertEquals(alice.username, comment.author.username)
        assertEquals(alice.id, comment.author.id)
        assertTrue(comment.isMine)
        assertNull(comment.editedAt)
    }

    @Test
    fun `everyone sees comments, isMine only for the author`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")
        val movie = uniqueMovieId()
        client.comment(alice, movie, "from alice")

        val asBob = client.list(bob, movie).items.single()
        assertEquals("from alice", asBob.body)
        assertFalse(asBob.isMine)
        assertTrue(client.list(alice, movie).items.single().isMine)
    }

    @Test
    fun `list is newest first and paginates without gaps or repeats`() = apiTest { client ->
        val user = client.registerUser()
        val movie = uniqueMovieId()
        val bodies = (1..7).map { "comment $it" }
        bodies.forEach { client.comment(user, movie, it) }
        client.comment(user, uniqueMovieId(), "other movie") // must not appear

        val seen = mutableListOf<String>()
        var page = client.list(user, movie, "limit=3")
        seen += page.items.map { it.body }
        while (page.nextCursor != null) {
            page = client.list(user, movie, "limit=3&before=${page.nextCursor}")
            seen += page.items.map { it.body }
        }
        assertEquals(bodies.reversed(), seen)
    }

    @Test
    fun `author can edit, which marks the comment edited`() = apiTest { client ->
        val user = client.registerUser()
        val movie = uniqueMovieId()
        val id = client.comment(user, movie, "typo").body<CommentDto>().id

        val edited = client.edit(user, id, "fixed").body<CommentDto>()
        assertEquals("fixed", edited.body)
        assertNotNull(edited.editedAt)
        assertEquals("fixed", client.list(user, movie).items.single().body)
    }

    @Test
    fun `only the author can edit or delete`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val mallory = client.registerUser("mallory")
        val movie = uniqueMovieId()
        val id = client.comment(alice, movie, "mine").body<CommentDto>().id

        val edit = client.edit(mallory, id, "hacked")
        assertEquals(HttpStatusCode.Forbidden, edit.status)
        assertEquals("FORBIDDEN", edit.body<ErrorResponse>().error)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/comments/$id") { bearerAuth(mallory.token) }.status)

        assertEquals("mine", client.list(alice, movie).items.single().body)
    }

    @Test
    fun `deleted comments disappear and can't be edited`() = apiTest { client ->
        val user = client.registerUser()
        val movie = uniqueMovieId()
        val keep = client.comment(user, movie, "keep").body<CommentDto>().id
        val gone = client.comment(user, movie, "gone").body<CommentDto>().id

        assertEquals(HttpStatusCode.NoContent, client.delete("/comments/$gone") { bearerAuth(user.token) }.status)

        assertEquals(listOf(keep), client.list(user, movie).items.map { it.id })
        assertEquals(HttpStatusCode.NotFound, client.edit(user, gone, "back?").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/comments/$gone") { bearerAuth(user.token) }.status)
    }

    @Test
    fun `movie details include the visible comment count`() = apiTest { client ->
        val user = client.registerUser()
        val movie = uniqueMovieId()
        client.comment(user, movie, "one")
        val two = client.comment(user, movie, "two").body<CommentDto>().id
        client.delete("/comments/$two") { bearerAuth(user.token) }

        val details = client.get("/movies/$movie") { bearerAuth(user.token) }.body<MovieDetailsResponse>()
        assertEquals(1, details.commentCount)
    }

    @Test
    fun `invalid comments are rejected`() = apiTest { client ->
        val user = client.registerUser()
        val movie = uniqueMovieId()

        listOf("", "   ", "x".repeat(1001)).forEach { body ->
            val response = client.comment(user, movie, body)
            assertEquals(HttpStatusCode.BadRequest, response.status, "body length ${body.length}")
            assertEquals("VALIDATION_ERROR", response.body<ErrorResponse>().error)
        }
        assertEquals(HttpStatusCode.Created, client.comment(user, movie, "x".repeat(1000)).status)

        assertEquals(HttpStatusCode.NotFound, client.comment(user, MISSING_MOVIE, "hello").status)
        assertEquals(HttpStatusCode.BadRequest, client.edit(user, "not-a-uuid", "x").status)
        assertEquals(HttpStatusCode.NotFound, client.edit(user, UUID.randomUUID().toString(), "x").status)
        listOf("limit=0", "limit=51", "before=nonsense").forEach { query ->
            assertEquals(HttpStatusCode.BadRequest, client.get("/movies/$movie/comments?$query") { bearerAuth(user.token) }.status, query)
        }
    }

    @Test
    fun `comments require login`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/movies/1/comments").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/movies/1/comments").status)
        assertEquals(HttpStatusCode.Unauthorized, client.patch("/comments/${UUID.randomUUID()}").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/comments/${UUID.randomUUID()}/replies").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/comments/${UUID.randomUUID()}/replies").status)
    }

    private suspend fun HttpClient.rate(user: TestUser, movieId: Int, score: Int) =
        put("/movies/$movieId/rating") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(SetRatingRequest(score))
        }

    @Test
    fun `comments carry their author's rating of that movie`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")
        val movie = uniqueMovieId()
        val otherMovie = uniqueMovieId()
        client.rate(alice, movie, 8)
        client.rate(bob, otherMovie, 3) // a rating of a different movie must not show

        val top = client.comment(alice, movie, "great").body<CommentDto>()
        assertEquals(8, top.authorRating)
        val bobsReply = client.reply(bob, top.id, "not seen it").body<CommentDto>()
        assertNull(bobsReply.authorRating)

        client.rate(alice, movie, 5) // re-rating shows up in later loads
        assertEquals(5, client.list(bob, movie).items.single().authorRating)

        client.rate(bob, movie, 10)
        assertEquals(10, client.replies(alice, top.id).body<CommentPageDto>().items.single().authorRating)
    }

    // ───────────── Replies ─────────────

    private suspend fun HttpClient.reply(user: TestUser, commentId: String, body: String): HttpResponse =
        post("/comments/$commentId/replies") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(CommentRequest(body))
        }

    private suspend fun HttpClient.replies(user: TestUser, commentId: String, query: String = ""): HttpResponse =
        get("/comments/$commentId/replies?$query") { bearerAuth(user.token) }

    @Test
    fun `replies form a thread under the comment, oldest first`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")
        val movie = uniqueMovieId()
        val top = client.comment(alice, movie, "What did you think of the ending?").body<CommentDto>()

        val first = client.reply(bob, top.id, "Loved it").body<CommentDto>()
        client.reply(alice, top.id, "Same!")

        assertEquals(top.id, first.parentId)
        assertEquals(movie, first.movieId)
        assertEquals(0, first.replyCount)

        val thread = client.replies(alice, top.id).body<CommentPageDto>()
        assertEquals(listOf("Loved it", "Same!"), thread.items.map { it.body })
        assertEquals(listOf(false, true), thread.items.map { it.isMine })

        // Top-level list shows only the comment, with its reply count.
        val topLevel = client.list(bob, movie).items.single()
        assertEquals(top.id, topLevel.id)
        assertNull(topLevel.parentId)
        assertEquals(2, topLevel.replyCount)
    }

    @Test
    fun `replying to a reply joins the same thread`() = apiTest { client ->
        val user = client.registerUser()
        val top = client.comment(user, uniqueMovieId(), "top").body<CommentDto>()
        val reply = client.reply(user, top.id, "reply").body<CommentDto>()

        val replyToReply = client.reply(user, reply.id, "@someone reply to reply").body<CommentDto>()

        assertEquals(top.id, replyToReply.parentId)
        assertEquals(2, client.replies(user, top.id).body<CommentPageDto>().items.size)
        // A reply has no thread of its own.
        assertEquals(HttpStatusCode.BadRequest, client.replies(user, reply.id).status)
    }

    @Test
    fun `replies paginate forwards without gaps or repeats`() = apiTest { client ->
        val user = client.registerUser()
        val top = client.comment(user, uniqueMovieId(), "top").body<CommentDto>()
        val bodies = (1..5).map { "reply $it" }
        bodies.forEach { client.reply(user, top.id, it) }

        val seen = mutableListOf<String>()
        var page = client.replies(user, top.id, "limit=2").body<CommentPageDto>()
        seen += page.items.map { it.body }
        while (page.nextCursor != null) {
            page = client.replies(user, top.id, "limit=2&after=${page.nextCursor}").body<CommentPageDto>()
            seen += page.items.map { it.body }
        }
        assertEquals(bodies, seen)
    }

    @Test
    fun `replies can be edited and deleted by their author only`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")
        val top = client.comment(alice, uniqueMovieId(), "top").body<CommentDto>()
        val bobsReply = client.reply(bob, top.id, "bob's reply").body<CommentDto>()

        assertEquals(HttpStatusCode.Forbidden, client.edit(alice, bobsReply.id, "changed by alice").status)
        val edited = client.edit(bob, bobsReply.id, "bob's reply, edited").body<CommentDto>()
        assertEquals(top.id, edited.parentId)
        assertNotNull(edited.editedAt)

        assertEquals(HttpStatusCode.NoContent, client.delete("/comments/${bobsReply.id}") { bearerAuth(bob.token) }.status)
        assertEquals(0, client.list(alice, top.movieId).items.single().replyCount)
        assertTrue(client.replies(alice, top.id).body<CommentPageDto>().items.isEmpty())
    }

    @Test
    fun `deleting a comment hides its whole thread`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")
        val movie = uniqueMovieId()
        val top = client.comment(alice, movie, "top").body<CommentDto>()
        val reply = client.reply(bob, top.id, "reply").body<CommentDto>()
        client.comment(alice, movie, "another top")

        client.delete("/comments/${top.id}") { bearerAuth(alice.token) }

        assertEquals(listOf("another top"), client.list(bob, movie).items.map { it.body })
        assertEquals(HttpStatusCode.NotFound, client.replies(bob, top.id).status)
        assertEquals(HttpStatusCode.NotFound, client.reply(bob, top.id, "too late").status)
        assertEquals(HttpStatusCode.NotFound, client.reply(bob, reply.id, "too late").status)
        assertEquals(HttpStatusCode.NotFound, client.edit(bob, reply.id, "too late").status)

        val details = client.get("/movies/$movie") { bearerAuth(bob.token) }.body<MovieDetailsResponse>()
        assertEquals(1, details.commentCount)
    }

    @Test
    fun `comment count includes visible replies`() = apiTest { client ->
        val user = client.registerUser()
        val movie = uniqueMovieId()
        val top = client.comment(user, movie, "top").body<CommentDto>()
        client.reply(user, top.id, "one")
        client.reply(user, top.id, "two")

        val details = client.get("/movies/$movie") { bearerAuth(user.token) }.body<MovieDetailsResponse>()
        assertEquals(3, details.commentCount)
    }

    @Test
    fun `invalid replies are rejected`() = apiTest { client ->
        val user = client.registerUser()
        val top = client.comment(user, uniqueMovieId(), "top").body<CommentDto>()

        assertEquals(HttpStatusCode.BadRequest, client.reply(user, top.id, "   ").status)
        assertEquals(HttpStatusCode.NotFound, client.reply(user, UUID.randomUUID().toString(), "hi").status)
        assertEquals(HttpStatusCode.BadRequest, client.reply(user, "nope", "hi").status)
        assertEquals(HttpStatusCode.BadRequest, client.replies(user, top.id, "after=garbage").status)
    }
}

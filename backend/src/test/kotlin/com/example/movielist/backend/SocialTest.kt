package com.example.movielist.backend

import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.movies.MovieDetailsResponse
import com.example.movielist.backend.features.ratings.RatingStatsDto
import com.example.movielist.backend.features.ratings.SetRatingRequest
import com.example.movielist.backend.features.reactions.ReactionIdsDto
import com.example.movielist.backend.features.reactions.ReactionPageDto
import com.example.movielist.backend.features.reactions.SetReactionRequest
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Likes/dislikes and ratings (Phase 4). */
class SocialTest {

    private suspend fun HttpClient.react(token: String, movieId: Int, reaction: Reaction): HttpResponse =
        put("/movies/$movieId/reaction") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(SetReactionRequest(reaction))
        }

    private suspend fun HttpClient.rate(token: String, movieId: Int, score: Int): HttpResponse =
        put("/movies/$movieId/rating") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(SetRatingRequest(score))
        }

    private suspend fun HttpClient.details(token: String, movieId: Int): MovieDetailsResponse =
        get("/movies/$movieId") { bearerAuth(token) }.body()

    private suspend fun HttpClient.ids(token: String): ReactionIdsDto =
        get("/me/reactions/ids") { bearerAuth(token) }.body()

    @Test
    fun `like then dislike replaces the reaction, delete clears it`() = apiTest { client ->
        val token = client.registerUser().token
        val movie = uniqueMovieId()

        assertEquals(HttpStatusCode.OK, client.react(token, movie, Reaction.LIKE).status)
        assertEquals(ReactionIdsDto(liked = listOf(movie), disliked = emptyList()), client.ids(token))
        assertEquals(Reaction.LIKE, client.details(token, movie).myReaction)

        client.react(token, movie, Reaction.DISLIKE)
        assertEquals(ReactionIdsDto(liked = emptyList(), disliked = listOf(movie)), client.ids(token))

        assertEquals(HttpStatusCode.NoContent, client.delete("/movies/$movie/reaction") { bearerAuth(token) }.status)
        assertEquals(ReactionIdsDto(emptyList(), emptyList()), client.ids(token))
        assertNull(client.details(token, movie).myReaction)
    }

    @Test
    fun `reactions are private to each user`() = apiTest { client ->
        val alice = client.registerUser().token
        val bob = client.registerUser().token
        val movie = uniqueMovieId()

        client.react(alice, movie, Reaction.LIKE)

        assertEquals(ReactionIdsDto(emptyList(), emptyList()), client.ids(bob))
        assertNull(client.details(bob, movie).myReaction)
    }

    @Test
    fun `reacting to an unknown movie is a 404 and stores nothing`() = apiTest { client ->
        val token = client.registerUser().token

        val response = client.react(token, MISSING_MOVIE, Reaction.LIKE)

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ReactionIdsDto(emptyList(), emptyList()), client.ids(token))
    }

    @Test
    fun `invalid reaction value is a bad request`() = apiTest { client ->
        val token = client.registerUser().token
        val response = client.put("/movies/${uniqueMovieId()}/reaction") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"reaction":"LOVE"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `reaction list is newest first, filtered, paginated and includes movie info`() = apiTest { client ->
        val token = client.registerUser().token
        val liked = List(5) { uniqueMovieId() }
        val disliked = uniqueMovieId()
        liked.forEach { client.react(token, it, Reaction.LIKE) }
        client.react(token, disliked, Reaction.DISLIKE)

        val first = client.get("/me/reactions?type=like&limit=2") { bearerAuth(token) }.body<ReactionPageDto>()
        assertEquals(liked.reversed().take(2), first.items.map { it.movie.id })
        assertEquals("Movie ${liked.last()}", first.items.first().movie.title)
        assertEquals("/${liked.last()}.jpg", first.items.first().movie.posterPath)
        assertEquals(listOf(35), first.items.first().movie.genreIds)

        val seen = first.items.map { it.movie.id }.toMutableList()
        var cursor = first.nextCursor
        while (cursor != null) {
            val page = client.get("/me/reactions?type=LIKE&limit=2&before=$cursor") { bearerAuth(token) }
                .body<ReactionPageDto>()
            seen += page.items.map { it.movie.id }
            cursor = page.nextCursor
        }
        assertEquals(liked.reversed(), seen) // every like exactly once, no dislike

        val all = client.get("/me/reactions") { bearerAuth(token) }.body<ReactionPageDto>()
        assertEquals(6, all.items.size)
        assertEquals(disliked, all.items.first().movie.id)
        assertNull(all.nextCursor)
    }

    @Test
    fun `bad list parameters are rejected`() = apiTest { client ->
        val token = client.registerUser().token
        listOf("type=meh", "limit=0", "limit=101", "limit=x", "before=garbage", "before=2026-01-01T00:00:00Z_x")
            .forEach { query ->
                val response = client.get("/me/reactions?$query") { bearerAuth(token) }
                assertEquals(HttpStatusCode.BadRequest, response.status, query)
                assertEquals("VALIDATION_ERROR", response.body<ErrorResponse>().error, query)
            }
    }

    @Test
    fun `rating is validated, upserted and averaged over users`() = apiTest { client ->
        val alice = client.registerUser().token
        val bob = client.registerUser().token
        val carol = client.registerUser().token
        val movie = uniqueMovieId()

        assertEquals(RatingStatsDto(avgRating = null, ratingCount = 0, myRating = null), statsOf(client.details(alice, movie)))

        listOf(0, 11, -3).forEach { assertEquals(HttpStatusCode.BadRequest, client.rate(alice, movie, it).status) }

        client.rate(alice, movie, 4)
        client.rate(alice, movie, 8) // changes her rating, doesn't add a second one
        client.rate(bob, movie, 7)
        val afterCarol = client.rate(carol, movie, 6).body<RatingStatsDto>()

        assertEquals(RatingStatsDto(avgRating = 7.0, ratingCount = 3, myRating = 6), afterCarol)
        assertEquals(RatingStatsDto(avgRating = 7.0, ratingCount = 3, myRating = 8), statsOf(client.details(alice, movie)))

        val afterDelete = client.delete("/movies/$movie/rating") { bearerAuth(bob) }.body<RatingStatsDto>()
        assertEquals(RatingStatsDto(avgRating = 7.0, ratingCount = 2, myRating = null), afterDelete)
    }

    @Test
    fun `average is rounded to one decimal`() = apiTest { client ->
        val movie = uniqueMovieId()
        val scores = listOf(10, 9, 9) // 9.333…
        scores.forEach { client.rate(client.registerUser().token, movie, it) }

        val stats = statsOf(client.details(client.registerUser().token, movie))
        assertEquals(9.3, stats.avgRating)
        assertEquals(3, stats.ratingCount)
    }

    @Test
    fun `social endpoints require login`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.put("/movies/1/reaction").status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/movies/1/rating").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me/reactions").status)
        assertTrue(client.get("/me/reactions/ids").status == HttpStatusCode.Unauthorized)
    }

    private fun statsOf(details: MovieDetailsResponse) =
        RatingStatsDto(details.avgRating, details.ratingCount, details.myRating)
}

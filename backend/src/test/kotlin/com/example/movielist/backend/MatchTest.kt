package com.example.movielist.backend

import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.friends.FriendRequestBody
import com.example.movielist.backend.features.matches.MatchDto
import com.example.movielist.backend.features.matches.MatchService
import com.example.movielist.backend.features.reactions.ReactionDto
import com.example.movielist.backend.features.reactions.SetReactionRequest
import com.example.movielist.backend.features.users.FriendshipState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchTest {

    private suspend fun HttpClient.react(user: TestUser, movieId: Int, reaction: Reaction = Reaction.LIKE): ReactionDto =
        put("/movies/$movieId/reaction") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(SetReactionRequest(reaction))
        }.body()

    private suspend fun HttpClient.befriend(a: TestUser, b: TestUser) {
        post("/friends/requests") {
            bearerAuth(a.token)
            contentType(ContentType.Application.Json)
            setBody(FriendRequestBody(b.id))
        }
        post("/friends/requests/${a.id}/accept") { bearerAuth(b.token) }
    }

    private suspend fun HttpClient.matches(user: TestUser): List<MatchDto> =
        get("/matches") { bearerAuth(user.token) }.body()

    @Test
    fun `liking a movie someone liked is a match, even with a stranger`() = apiTest { client ->
        val me = client.registerUser("me")
        val stranger = client.registerUser("stranger")
        val movie = uniqueMovieId()

        assertTrue(client.react(stranger, movie).matchedPeople.isEmpty()) // nobody else yet
        val mine = client.react(me, movie)

        assertEquals(listOf(stranger.id), mine.matchedPeople.map { it.user.id })
        assertEquals(1, mine.matchedCount)
        assertEquals(FriendshipState.NONE, mine.matchedPeople.single().user.friendship)
        assertEquals(1, mine.matchedPeople.single().sharedLikes)

        val match = client.matches(me).single()
        assertEquals(movie, match.movie.id)
        assertEquals("Movie $movie", match.movie.title)
        assertEquals(listOf(stranger.id), match.people.map { it.user.id })
        assertEquals(listOf(me.id), client.matches(stranger).single().people.map { it.user.id }) // both see it
    }

    @Test
    fun `dislikes don't match`() = apiTest { client ->
        val me = client.registerUser("me")
        val other = client.registerUser("other")
        val movie = uniqueMovieId()

        client.react(other, movie, Reaction.DISLIKE)
        assertTrue(client.react(me, movie).matchedPeople.isEmpty())
        assertTrue(client.matches(me).isEmpty())

        // Disliking never reports a match, even when the other person likes it.
        client.react(other, movie)
        assertTrue(client.react(me, movie, Reaction.DISLIKE).matchedPeople.isEmpty())
        assertTrue(client.matches(me).isEmpty())
    }

    @Test
    fun `people with the most likes in common come first, friends first on a tie`() = apiTest { client ->
        val me = client.registerUser("me")
        val anna = client.registerUser("anna")
        val ben = client.registerUser("ben")
        val cleo = client.registerUser("cleo")
        client.befriend(me, cleo)
        val older = uniqueMovieId()
        val newer = uniqueMovieId()
        listOf(anna, ben, cleo).forEach { client.react(it, newer) }
        client.react(anna, older)

        client.react(me, older)
        val mine = client.react(me, newer)

        // anna shares both movies; cleo and ben one each, and cleo is a friend.
        assertEquals(listOf(anna.id, cleo.id, ben.id), mine.matchedPeople.map { it.user.id })
        assertEquals(listOf(2, 1, 1), mine.matchedPeople.map { it.sharedLikes })
        assertEquals(FriendshipState.FRIENDS, mine.matchedPeople[1].user.friendship)

        val matches = client.matches(me)
        assertEquals(listOf(newer, older), matches.map { it.movie.id }) // newest like first
        assertEquals(listOf(anna.id, cleo.id, ben.id), matches.first().people.map { it.user.id })
        assertEquals(listOf(anna.id), matches.last().people.map { it.user.id })
    }

    @Test
    fun `a popular movie shows the best few people and counts everyone`() = apiTest { client ->
        val me = client.registerUser("me")
        val movie = uniqueMovieId()
        repeat(MatchService.MAX_PEOPLE + 1) { client.react(client.registerUser("fan$it"), movie) }

        val mine = client.react(me, movie)
        assertEquals(MatchService.MAX_PEOPLE, mine.matchedPeople.size)
        assertEquals(MatchService.MAX_PEOPLE + 1, mine.matchedCount)
        assertEquals(MatchService.MAX_PEOPLE + 1, client.matches(me).single().peopleCount)
    }

    @Test
    fun `unliking on either side removes the match`() = apiTest { client ->
        val me = client.registerUser("me")
        val other = client.registerUser("other")
        val first = uniqueMovieId()
        val second = uniqueMovieId()
        listOf(first, second).forEach { client.react(other, it); client.react(me, it) }
        assertEquals(2, client.matches(me).size)

        client.delete("/movies/$first/reaction") { bearerAuth(me.token) }
        assertEquals(listOf(second), client.matches(me).map { it.movie.id })

        client.react(other, second, Reaction.DISLIKE)
        assertTrue(client.matches(me).isEmpty())
    }

    @Test
    fun `matches require login`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/matches").status)
    }
}

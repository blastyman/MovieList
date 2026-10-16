package com.example.movielist.backend

import com.example.movielist.backend.db.FriendshipStatus
import com.example.movielist.backend.db.Friendships
import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.auth.AuthResponse
import com.example.movielist.backend.features.auth.RegisterRequest
import com.example.movielist.backend.features.auth.UpdateMeRequest
import com.example.movielist.backend.features.comments.CommentRequest
import com.example.movielist.backend.features.ratings.SetRatingRequest
import com.example.movielist.backend.features.reactions.SetReactionRequest
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.ProfileStatsDto
import com.example.movielist.backend.features.users.UserProfileDto
import com.example.movielist.backend.features.users.UserSearchPageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserTest {

    /** Letters only, so it can't collide with other tests' random hex names. */
    private fun token() = (1..6).map { ('a'..'z').random() }.joinToString("")

    private suspend fun HttpClient.registerNamed(username: String, displayName: String? = null): TestUser {
        val auth = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, "$username@example.com", "correct-horse"))
        }.body<AuthResponse>()
        if (displayName != null) {
            patch("/me") {
                bearerAuth(auth.token)
                contentType(ContentType.Application.Json)
                setBody(UpdateMeRequest(displayName = displayName))
            }
        }
        return TestUser(auth.token, auth.user.id, username)
    }

    private suspend fun HttpClient.search(user: TestUser, query: String): UserSearchPageDto =
        get("/users/search?q=$query") { bearerAuth(user.token) }.body()

    @Test
    fun `search matches usernames and display names, ignoring case, best match first`() = apiTest { client ->
        val t = token()
        val viewer = client.registerNamed("${t}viewer")
        client.registerNamed("x${t}zz")                       // contains
        client.registerNamed("${t}zz_later")                  // starts with
        client.registerNamed("${t}zz")                        // exact
        client.registerNamed("someone_${Random.nextInt(100000)}", displayName = "Fan of ${t}ZZ") // display name

        val results = client.search(viewer, "${t.uppercase()}ZZ").results.map { it.username }

        assertEquals("${t}zz", results.first())
        assertEquals("${t}zz_later", results[1])
        assertEquals(setOf("x${t}zz"), results.drop(2).filter { it.startsWith("x") }.toSet())
        assertTrue(results.any { it.startsWith("someone_") })
        assertFalse(results.contains("${t}viewer")) // never yourself
    }

    @Test
    fun `wildcards in the query are taken literally`() = apiTest { client ->
        val t = token()
        val viewer = client.registerNamed("${t}_viewer")
        client.registerNamed("${t}a_b")
        client.registerNamed("${t}axb") // would match "a_b" if _ were a wildcard

        assertEquals(listOf("${t}a_b"), client.search(viewer, "${t}a_b").results.map { it.username })
        assertTrue(client.search(viewer, "${t}%25").results.isEmpty()) // %25 = "%"
    }

    @Test
    fun `search pages through results`() = apiTest { client ->
        val t = token()
        val viewer = client.registerNamed("${t}viewer")
        repeat(23) { client.registerNamed("${t}user$it") }

        val first = client.get("/users/search?q=${t}user") { bearerAuth(viewer.token) }.body<UserSearchPageDto>()
        val second = client.get("/users/search?q=${t}user&page=2") { bearerAuth(viewer.token) }.body<UserSearchPageDto>()

        assertEquals(20, first.results.size)
        assertTrue(first.hasMore)
        assertEquals(3, second.results.size)
        assertFalse(second.hasMore)
        assertEquals(23, (first.results + second.results).map { it.id }.toSet().size)
    }

    @Test
    fun `results and profiles show the friendship state from the viewer's side`() = apiTest { client ->
        val t = token()
        val me = client.registerNamed("${t}me")
        val friend = client.registerNamed("${t}friend")
        val asked = client.registerNamed("${t}asked")
        val asker = client.registerNamed("${t}asker")
        val stranger = client.registerNamed("${t}stranger")
        // Phase 8 adds the endpoints; here the rows are written directly.
        transaction(Database.connect(TestDatabase.dataSource)) {
            fun link(from: TestUser, to: TestUser, status: FriendshipStatus) = Friendships.insert {
                it[requesterId] = UUID.fromString(from.id)
                it[addresseeId] = UUID.fromString(to.id)
                it[Friendships.status] = status.name
            }
            link(friend, me, FriendshipStatus.ACCEPTED)
            link(me, asked, FriendshipStatus.PENDING)
            link(asker, me, FriendshipStatus.PENDING)
        }

        val states = client.search(me, t).results.associate { it.username to it.friendship }
        assertEquals(FriendshipState.FRIENDS, states["${t}friend"])
        assertEquals(FriendshipState.REQUEST_SENT, states["${t}asked"])
        assertEquals(FriendshipState.REQUEST_RECEIVED, states["${t}asker"])
        assertEquals(FriendshipState.NONE, states["${t}stranger"])

        val profile = client.get("/users/${asked.id}") { bearerAuth(me.token) }.body<UserProfileDto>()
        assertEquals(FriendshipState.REQUEST_SENT, profile.friendship)
        assertEquals(1, client.get("/users/${me.id}") { bearerAuth(stranger.token) }.body<UserProfileDto>().stats.friends)
    }

    @Test
    fun `profile shows counts and recent likes, never the email`() = apiTest { client ->
        val t = token()
        val alice = client.registerNamed("${t}alice", displayName = "Alice")
        val bob = client.registerNamed("${t}bob")
        val liked = List(3) { uniqueMovieId() }
        liked.forEach { movie ->
            client.put("/movies/$movie/reaction") {
                bearerAuth(alice.token)
                contentType(ContentType.Application.Json)
                setBody(SetReactionRequest(Reaction.LIKE))
            }
        }
        client.put("/movies/${liked[0]}/rating") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(SetRatingRequest(9))
        }
        client.post("/movies/${liked[0]}/comments") {
            bearerAuth(alice.token)
            contentType(ContentType.Application.Json)
            setBody(CommentRequest("great"))
        }

        val response = client.get("/users/${alice.id}") { bearerAuth(bob.token) }
        val profile = response.body<UserProfileDto>()

        assertEquals("Alice", profile.displayName)
        assertFalse(profile.isMe)
        assertEquals(ProfileStatsDto(likes = 3, ratings = 1, comments = 1, friends = 0), profile.stats)
        assertEquals(liked.reversed(), profile.recentLikes.map { it.id })
        assertFalse(response.bodyAsText().contains("@example.com"))

        assertTrue(client.get("/users/${alice.id}") { bearerAuth(alice.token) }.body<UserProfileDto>().isMe)
    }

    @Test
    fun `bad requests are rejected`() = apiTest { client ->
        val user = client.registerNamed("${token()}user")
        listOf("/users/search", "/users/search?q=a", "/users/search?q=${"x".repeat(51)}", "/users/search?q=ab&page=0", "/users/not-a-uuid")
            .forEach { assertEquals(HttpStatusCode.BadRequest, client.get(it) { bearerAuth(user.token) }.status, it) }
        assertEquals(HttpStatusCode.NotFound, client.get("/users/${UUID.randomUUID()}") { bearerAuth(user.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/search?q=ab").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users/${user.id}").status)
    }
}

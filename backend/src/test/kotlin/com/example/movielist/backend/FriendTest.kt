package com.example.movielist.backend

import com.example.movielist.backend.db.Friendships
import com.example.movielist.backend.features.friends.FriendRequestBody
import com.example.movielist.backend.features.friends.FriendRequestsDto
import com.example.movielist.backend.features.friends.FriendshipDto
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.UserProfileDto
import com.example.movielist.backend.features.users.UserSummaryDto
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendTest {

    private suspend fun HttpClient.ask(from: TestUser, to: TestUser): HttpResponse =
        post("/friends/requests") {
            bearerAuth(from.token)
            contentType(ContentType.Application.Json)
            setBody(FriendRequestBody(to.id))
        }

    private suspend fun HttpClient.requests(user: TestUser) =
        get("/friends/requests") { bearerAuth(user.token) }.body<FriendRequestsDto>()

    private suspend fun HttpClient.friends(user: TestUser) =
        get("/friends") { bearerAuth(user.token) }.body<List<UserSummaryDto>>()

    private suspend fun HttpClient.stateSeenBy(viewer: TestUser, other: TestUser) =
        get("/users/${other.id}") { bearerAuth(viewer.token) }.body<UserProfileDto>().friendship

    private fun rowsBetween(a: TestUser, b: TestUser): Int {
        val ua = UUID.fromString(a.id)
        val ub = UUID.fromString(b.id)
        return transaction(Database.connect(TestDatabase.dataSource)) {
            Friendships.selectAll().where {
                ((Friendships.requesterId eq ua) and (Friendships.addresseeId eq ub)) or
                    ((Friendships.requesterId eq ub) and (Friendships.addresseeId eq ua))
            }.count().toInt()
        }
    }

    @Test
    fun `request, then accept, makes both friends`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")

        assertEquals(FriendshipDto(bob.id, FriendshipState.REQUEST_SENT), client.ask(alice, bob).body())
        assertEquals(listOf(alice.id), client.requests(bob).incoming.map { it.user.id })
        assertEquals(listOf(bob.id), client.requests(alice).outgoing.map { it.user.id })
        assertEquals(FriendshipState.REQUEST_SENT, client.stateSeenBy(alice, bob))
        assertEquals(FriendshipState.REQUEST_RECEIVED, client.stateSeenBy(bob, alice))

        val accepted = client.post("/friends/requests/${alice.id}/accept") { bearerAuth(bob.token) }.body<FriendshipDto>()
        assertEquals(FriendshipState.FRIENDS, accepted.friendship)

        assertEquals(listOf(bob.id), client.friends(alice).map { it.id })
        assertEquals(listOf(alice.id), client.friends(bob).map { it.id })
        assertTrue(client.requests(bob).incoming.isEmpty())
        assertEquals(FriendshipState.FRIENDS, client.stateSeenBy(alice, bob))
    }

    @Test
    fun `asking someone who already asked you accepts`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")
        client.ask(alice, bob)

        assertEquals(FriendshipState.FRIENDS, client.ask(bob, alice).body<FriendshipDto>().friendship)
        assertEquals(1, rowsBetween(alice, bob))
    }

    @Test
    fun `asking twice or asking a friend changes nothing`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")

        client.ask(alice, bob)
        assertEquals(FriendshipState.REQUEST_SENT, client.ask(alice, bob).body<FriendshipDto>().friendship)
        assertEquals(1, rowsBetween(alice, bob))

        client.ask(bob, alice) // accepts
        assertEquals(FriendshipState.FRIENDS, client.ask(alice, bob).body<FriendshipDto>().friendship)
        assertEquals(1, rowsBetween(alice, bob))
    }

    @Test
    fun `asking each other at the same moment ends as friends with one row`() = apiTest { client ->
        repeat(5) {
            val alice = client.registerUser("alice")
            val bob = client.registerUser("bob")

            val results = coroutineScope {
                listOf(async { client.ask(alice, bob) }, async { client.ask(bob, alice) }).awaitAll()
            }

            assertTrue(results.all { it.status == HttpStatusCode.OK }, results.map { it.status }.toString())
            assertEquals(1, rowsBetween(alice, bob))
            // Whichever arrived second accepted the first.
            assertEquals(FriendshipState.FRIENDS, client.stateSeenBy(alice, bob))
        }
    }

    @Test
    fun `decline and cancel remove the request so it can be sent again`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")

        client.ask(alice, bob)
        assertEquals(FriendshipState.NONE, client.post("/friends/requests/${alice.id}/decline") { bearerAuth(bob.token) }.body<FriendshipDto>().friendship)
        assertEquals(0, rowsBetween(alice, bob))

        client.ask(alice, bob)
        assertEquals(FriendshipState.NONE, client.delete("/friends/requests/${bob.id}") { bearerAuth(alice.token) }.body<FriendshipDto>().friendship)
        assertTrue(client.requests(bob).incoming.isEmpty())

        assertEquals(FriendshipState.REQUEST_SENT, client.ask(alice, bob).body<FriendshipDto>().friendship)
    }

    @Test
    fun `unfriending works from either side`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")
        client.ask(alice, bob)
        client.ask(bob, alice)

        assertEquals(FriendshipState.NONE, client.delete("/friends/${alice.id}") { bearerAuth(bob.token) }.body<FriendshipDto>().friendship)
        assertTrue(client.friends(alice).isEmpty())
        assertEquals(FriendshipState.NONE, client.stateSeenBy(alice, bob))
    }

    @Test
    fun `wrong actions are refused`() = apiTest { client ->
        val alice = client.registerUser("alice")
        val bob = client.registerUser("bob")

        assertEquals(HttpStatusCode.BadRequest, client.ask(alice, alice).status)
        assertEquals(HttpStatusCode.NotFound, client.ask(alice, TestUser("", UUID.randomUUID().toString(), "ghost")).status)

        // Nothing to accept, decline, cancel or remove yet.
        val accept = client.post("/friends/requests/${bob.id}/accept") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.NotFound, accept.status)
        assertEquals("NO_FRIEND_REQUEST", accept.body<ErrorResponse>().error)
        val remove = client.delete("/friends/${bob.id}") { bearerAuth(alice.token) }
        assertEquals("NOT_FRIENDS", remove.body<ErrorResponse>().error)

        // You can't accept your own request, or cancel someone else's.
        client.ask(alice, bob)
        assertEquals(HttpStatusCode.NotFound, client.post("/friends/requests/${bob.id}/accept") { bearerAuth(alice.token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/friends/requests/${alice.id}") { bearerAuth(bob.token) }.status)

        assertEquals(HttpStatusCode.BadRequest, client.post("/friends/requests/not-a-uuid/accept") { bearerAuth(bob.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/friends").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/friends/requests").status)
    }
}

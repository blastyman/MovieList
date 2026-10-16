package com.example.movielist.backend

import com.example.movielist.backend.features.auth.CurrentUserDto
import com.example.movielist.backend.features.auth.UpdatePrivacyRequest
import com.example.movielist.backend.features.chat.ConversationDto
import com.example.movielist.backend.features.chat.SendMessageRequest
import com.example.movielist.backend.features.chat.StartDirectRequest
import com.example.movielist.backend.features.friends.FriendRequestBody
import com.example.movielist.backend.features.matches.MatchDto
import com.example.movielist.backend.features.users.FriendRequestsFrom
import com.example.movielist.backend.features.users.MessagesFrom
import com.example.movielist.backend.features.users.UserProfileDto
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivacyTest {

    private suspend fun HttpClient.privacy(user: TestUser, request: UpdatePrivacyRequest): CurrentUserDto =
        patch("/me/privacy") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    private suspend fun HttpClient.profile(viewer: TestUser, of: TestUser): UserProfileDto =
        get("/users/${of.id}") { bearerAuth(viewer.token) }.body()

    private suspend fun HttpClient.askFriend(from: TestUser, to: TestUser): HttpResponse =
        post("/friends/requests") {
            bearerAuth(from.token)
            contentType(ContentType.Application.Json)
            setBody(FriendRequestBody(to.id))
        }

    private suspend fun HttpClient.startDirect(from: TestUser, to: TestUser): HttpResponse =
        post("/conversations/direct") {
            bearerAuth(from.token)
            contentType(ContentType.Application.Json)
            setBody(StartDirectRequest(to.id))
        }

    @Test
    fun `settings start open and change one at a time`() = apiTest { client ->
        val me = client.registerUser("me")

        val defaults = client.get("/me") { bearerAuth(me.token) }.body<CurrentUserDto>().privacy
        assertTrue(defaults.showInMatches)
        assertFalse(defaults.privateLikes)
        assertEquals(FriendRequestsFrom.EVERYONE, defaults.friendRequestsFrom)
        assertEquals(MessagesFrom.FRIENDS, defaults.messagesFrom)

        val changed = client.privacy(me, UpdatePrivacyRequest(privateLikes = true)).privacy
        assertTrue(changed.privateLikes)
        assertTrue(changed.showInMatches) // untouched
    }

    @Test
    fun `hidden from matches means strangers don't see you, friends still do`() = apiTest { client ->
        val shy = client.registerUser("shy")
        val stranger = client.registerUser("stranger")
        val friend = client.registerUser("friend")
        client.befriend(shy, friend)
        val movie = uniqueMovieId()
        client.react(shy, movie)
        client.privacy(shy, UpdatePrivacyRequest(showInMatches = false))

        assertTrue(client.react(stranger, movie).matchedPeople.isEmpty())
        assertTrue(client.get("/matches") { bearerAuth(stranger.token) }.body<List<MatchDto>>().isEmpty())

        // The friend still sees shy (and the stranger, who liked it too).
        assertEquals(setOf(shy.id, stranger.id), client.react(friend, movie).matchedPeople.map { it.user.id }.toSet())
        // Hiding yourself doesn't hide others from you.
        assertEquals(setOf(stranger.id, friend.id), client.get("/matches") { bearerAuth(shy.token) }.body<List<MatchDto>>().single().people.map { it.user.id }.toSet())
    }

    @Test
    fun `private likes are only for friends`() = apiTest { client ->
        val me = client.registerUser("me")
        val friend = client.registerUser("friend")
        val stranger = client.registerUser("stranger")
        client.befriend(me, friend)
        client.react(me, uniqueMovieId())
        client.privacy(me, UpdatePrivacyRequest(privateLikes = true))

        val seenByStranger = client.profile(stranger, me)
        assertTrue(seenByStranger.likesHidden)
        assertNull(seenByStranger.stats.likes)
        assertTrue(seenByStranger.recentLikes.isEmpty())

        val seenByFriend = client.profile(friend, me)
        assertFalse(seenByFriend.likesHidden)
        assertEquals(1, seenByFriend.stats.likes)
        assertEquals(1, client.profile(me, me).stats.likes) // and you
    }

    @Test
    fun `friend requests can be limited to matches or turned off`() = apiTest { client ->
        val picky = client.registerUser("picky")
        val stranger = client.registerUser("stranger")
        val match = client.registerUser("match")
        val movie = uniqueMovieId()
        client.react(picky, movie)
        client.react(match, movie)

        client.privacy(picky, UpdatePrivacyRequest(friendRequestsFrom = FriendRequestsFrom.MATCHES))
        assertFalse(client.profile(stranger, picky).canAddFriend)
        val refused = client.askFriend(stranger, picky)
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("REQUESTS_CLOSED", refused.body<ErrorResponse>().error)
        assertTrue(client.profile(match, picky).canAddFriend)
        assertEquals(HttpStatusCode.OK, client.askFriend(match, picky).status)

        client.privacy(picky, UpdatePrivacyRequest(friendRequestsFrom = FriendRequestsFrom.NOBODY))
        val other = client.registerUser("other")
        assertEquals(HttpStatusCode.Forbidden, client.askFriend(other, picky).status)
        // They can still ask you themselves, and you can answer.
        assertEquals(HttpStatusCode.OK, client.askFriend(picky, other).status)
    }

    @Test
    fun `turning messages off closes one-to-one chats both ways, not group chats`() = apiTest { client ->
        val quiet = client.registerUser("quiet")
        val friend = client.registerUser("friend")
        client.befriend(quiet, friend)
        val chat = client.startDirect(friend, quiet).body<ConversationDto>()
        assertTrue(client.profile(friend, quiet).canMessage)

        client.privacy(quiet, UpdatePrivacyRequest(messagesFrom = MessagesFrom.NOBODY))

        assertFalse(client.profile(friend, quiet).canMessage)
        assertEquals("MESSAGES_OFF", client.startDirect(friend, quiet).body<ErrorResponse>().error)
        for ((from, to) in listOf(friend to quiet, quiet to friend)) {
            val sent = client.post("/conversations/${chat.id}/messages") {
                bearerAuth(from.token)
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest("hi ${to.username}"))
            }
            assertEquals(HttpStatusCode.Forbidden, sent.status)
        }
    }
}

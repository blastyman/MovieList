package com.example.movielist.backend

import com.example.movielist.backend.features.groups.AddMemberRequest
import com.example.movielist.backend.features.groups.CreateGroupRequest
import com.example.movielist.backend.features.groups.GroupDto
import com.example.movielist.backend.features.groups.GroupMatchDto
import com.example.movielist.backend.features.groups.GroupService
import com.example.movielist.backend.features.groups.RenameGroupRequest
import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupTest {

    private suspend fun HttpClient.createGroup(owner: TestUser, name: String, members: List<TestUser>): HttpResponse =
        post("/groups") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name, members.map { it.id }))
        }

    private suspend fun HttpClient.addMember(by: TestUser, groupId: String, user: TestUser): HttpResponse =
        post("/groups/$groupId/members") {
            bearerAuth(by.token)
            contentType(ContentType.Application.Json)
            setBody(AddMemberRequest(user.id))
        }

    private suspend fun HttpClient.removeMember(by: TestUser, groupId: String, user: TestUser): HttpResponse =
        delete("/groups/$groupId/members/${user.id}") { bearerAuth(by.token) }

    private suspend fun HttpClient.group(user: TestUser, groupId: String): HttpResponse =
        get("/groups/$groupId") { bearerAuth(user.token) }

    /** An owner with [count] friends, ready to be put in groups. */
    private suspend fun HttpClient.ownerWithFriends(count: Int): Pair<TestUser, List<TestUser>> {
        val owner = registerUser("owner")
        val friends = List(count) { registerUser("friend$it").also { befriend(owner, it) } }
        return owner to friends
    }

    @Test
    fun `creating a group puts the owner first and gives it a chat`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(2)

        val response = client.createGroup(owner, "  Movie night ", friends)
        assertEquals(HttpStatusCode.Created, response.status)
        val group = response.body<GroupDto>()
        assertEquals("Movie night", group.name)
        assertEquals(owner.id, group.ownerId)
        assertEquals(listOf(owner.id) + friends.map { it.id }, group.members.map { it.id })
        assertEquals(GroupService.MAX_MEMBERS, group.maxMembers)
        // Members see each other's friendship with them: the owner is friends with both.
        assertEquals(FriendshipState.FRIENDS, group.members[1].friendship)

        // Everyone in it sees it; the friends aren't friends with each other, which is fine.
        assertEquals(listOf(group.id), client.get("/groups") { bearerAuth(friends[1].token) }.body<List<GroupDto>>().map { it.id })
        assertEquals(1, TestDatabase.count("SELECT count(*) FROM conversations WHERE group_id = '${group.id}' AND type = 'GROUP'"))
        assertEquals(3, TestDatabase.count(
            "SELECT count(*) FROM conversation_members cm JOIN conversations c ON c.id = cm.conversation_id WHERE c.group_id = '${group.id}'"
        ))
    }

    @Test
    fun `only friends can be added`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(1)
        val stranger = client.registerUser("stranger")

        val create = client.createGroup(owner, "Nope", listOf(friends[0], stranger))
        assertEquals(HttpStatusCode.Forbidden, create.status)
        assertEquals("NOT_FRIENDS", create.body<ErrorResponse>().error)

        val group = client.createGroup(owner, "Yes", friends).body<GroupDto>()
        assertEquals(HttpStatusCode.Forbidden, client.addMember(owner, group.id, stranger).status)
        // A member may only add their own friends: the friend isn't friends with the owner's stranger either.
        assertEquals(HttpStatusCode.Forbidden, client.addMember(friends[0], group.id, stranger).status)
    }

    @Test
    fun `groups hold at most 10 people, owner included`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(GroupService.MAX_MEMBERS)

        val tooBig = client.createGroup(owner, "Too big", friends) // 1 + 10 = 11
        assertEquals(HttpStatusCode.Conflict, tooBig.status)
        assertEquals("GROUP_FULL", tooBig.body<ErrorResponse>().error)

        val group = client.createGroup(owner, "Full", friends.dropLast(1)).body<GroupDto>()
        assertEquals(10, group.members.size)
        val eleventh = client.addMember(owner, group.id, friends.last())
        assertEquals(HttpStatusCode.Conflict, eleventh.status)
        assertEquals("GROUP_FULL", eleventh.body<ErrorResponse>().error)
    }

    @Test
    fun `two invites racing for the last spot can't make 11`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(GroupService.MAX_MEMBERS)
        val group = client.createGroup(owner, "Race", friends.take(8)).body<GroupDto>() // 9 people
        val (a, b) = friends.drop(8)

        val statuses = coroutineScope {
            listOf(a, b).map { async { client.addMember(owner, group.id, it).status } }.awaitAll()
        }

        assertEquals(setOf(HttpStatusCode.OK, HttpStatusCode.Conflict), statuses.toSet())
        assertEquals(10, client.group(owner, group.id).body<GroupDto>().members.size)
    }

    @Test
    fun `adding someone twice changes nothing`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(1)
        val group = client.createGroup(owner, "Twice", friends).body<GroupDto>()
        val again = client.addMember(owner, group.id, friends[0])
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(2, again.body<GroupDto>().members.size)
    }

    @Test
    fun `outsiders can't see or touch a group`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(1)
        val outsider = client.registerUser("outsider")
        client.befriend(outsider, owner)
        val group = client.createGroup(owner, "Private", friends).body<GroupDto>()

        assertEquals(HttpStatusCode.NotFound, client.group(outsider, group.id).status)
        assertEquals(HttpStatusCode.NotFound, client.get("/groups/${group.id}/matches") { bearerAuth(outsider.token) }.status)
        // Even though the owner is their friend, they can't add themselves.
        assertEquals(HttpStatusCode.NotFound, client.addMember(outsider, group.id, owner).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/groups").status)
    }

    @Test
    fun `only the owner removes others or renames, anyone can leave`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(2)
        val group = client.createGroup(owner, "Club", friends).body<GroupDto>()

        assertEquals(HttpStatusCode.Forbidden, client.removeMember(friends[0], group.id, friends[1]).status)
        val rename = client.patch("/groups/${group.id}") {
            bearerAuth(friends[0].token)
            contentType(ContentType.Application.Json)
            setBody(RenameGroupRequest("Mine now"))
        }
        assertEquals(HttpStatusCode.Forbidden, rename.status)

        val afterRemove = client.removeMember(owner, group.id, friends[1])
        assertEquals(listOf(owner.id, friends[0].id), afterRemove.body<GroupDto>().members.map { it.id })
        assertEquals(HttpStatusCode.NotFound, client.group(friends[1], group.id).status)

        assertEquals(HttpStatusCode.NoContent, client.removeMember(friends[0], group.id, friends[0]).status)
        assertEquals(listOf(owner.id), client.group(owner, group.id).body<GroupDto>().members.map { it.id })
    }

    @Test
    fun `when the owner leaves the longest member takes over, and the last one out deletes it`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(2)
        val group = client.createGroup(owner, "Handover", friends).body<GroupDto>()

        assertEquals(HttpStatusCode.NoContent, client.removeMember(owner, group.id, owner).status)
        val handedOver = client.group(friends[0], group.id).body<GroupDto>()
        assertEquals(friends[0].id, handedOver.ownerId)
        assertEquals(listOf(friends[0].id, friends[1].id), handedOver.members.map { it.id })

        client.removeMember(friends[0], group.id, friends[0])
        client.removeMember(friends[1], group.id, friends[1])
        assertEquals(0, TestDatabase.count("SELECT count(*) FROM friend_groups WHERE id = '${group.id}'"))
        assertEquals(0, TestDatabase.count("SELECT count(*) FROM conversations WHERE group_id = '${group.id}'"))
    }

    @Test
    fun `group matches are movies every member likes`() = apiTest { client ->
        val (owner, friends) = client.ownerWithFriends(2)
        val group = client.createGroup(owner, "Taste", friends).body<GroupDto>()
        val all = uniqueMovieId()
        val most = uniqueMovieId()
        friends.forEach { client.react(it, all) }
        client.react(friends[0], most)
        client.react(owner, most)

        // The like that completes the group says so; the one that doesn't, doesn't.
        assertTrue(client.react(friends[1], uniqueMovieId()).completedGroups.isEmpty())
        val completing = client.react(owner, all)
        assertEquals(listOf("Taste"), completing.completedGroups.map { it.name })

        val matches = client.get("/groups/${group.id}/matches") { bearerAuth(friends[1].token) }.body<List<GroupMatchDto>>()
        assertEquals(listOf(all), matches.map { it.movie.id })

        // A newcomer who hasn't liked it breaks the match.
        val newcomer = client.registerUser("newcomer")
        client.befriend(owner, newcomer)
        client.addMember(owner, group.id, newcomer)
        assertTrue(client.get("/groups/${group.id}/matches") { bearerAuth(owner.token) }.body<List<GroupMatchDto>>().isEmpty())
    }

    @Test
    fun `bad names are rejected`() = apiTest { client ->
        val owner = client.registerUser("owner")
        assertEquals(HttpStatusCode.BadRequest, client.createGroup(owner, "   ", emptyList()).status)
        assertEquals(HttpStatusCode.BadRequest, client.createGroup(owner, "x".repeat(51), emptyList()).status)
        assertEquals(HttpStatusCode.Created, client.createGroup(owner, "Just me", emptyList()).status)
    }
}

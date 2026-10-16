package com.example.movielist.backend

import com.example.movielist.backend.features.auth.AuthResponse
import com.example.movielist.backend.features.auth.ChangeEmailRequest
import com.example.movielist.backend.features.auth.ChangePasswordRequest
import com.example.movielist.backend.features.auth.CurrentUserDto
import com.example.movielist.backend.features.auth.DeleteAccountRequest
import com.example.movielist.backend.features.auth.LoginRequest
import com.example.movielist.backend.features.auth.RegisterRequest
import com.example.movielist.backend.features.auth.UpdateMeRequest
import com.example.movielist.backend.features.groups.CreateGroupRequest
import com.example.movielist.backend.features.groups.GroupDto
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
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountTest {

    private suspend fun HttpClient.login(login: String, password: String): HttpResponse =
        post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(login, password))
        }

    private suspend fun HttpClient.uploadAvatar(user: TestUser, bytes: ByteArray, type: ContentType = ContentType.Image.PNG) =
        post("/me/avatar") {
            bearerAuth(user.token)
            contentType(type)
            setBody(bytes)
        }

    /** The first bytes of a real PNG; enough for the server to recognise the format. */
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64) { it.toByte() }

    @Test
    fun `the handle can be changed, but not to a taken one`() = apiTest { client ->
        val me = client.registerUser("me")
        val other = client.registerUser("other")
        val newName = "new_" + me.username.takeLast(10)

        val renamed = client.patch("/me") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateMeRequest(username = newName))
        }.body<CurrentUserDto>()
        assertEquals(newName, renamed.username)
        assertEquals(HttpStatusCode.OK, client.login(newName, "correct-horse").status)

        val taken = client.patch("/me") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateMeRequest(username = other.username.uppercase())) // taken, ignoring case
        }
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("USERNAME_TAKEN", taken.body<ErrorResponse>().error)
        val invalid = client.patch("/me") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(UpdateMeRequest(username = "no spaces"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
    }

    @Test
    fun `email and password changes need the current password`() = apiTest { client ->
        val me = client.registerUser("me")
        val other = client.registerUser("other")

        val wrong = client.put("/me/email") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(ChangeEmailRequest("new@example.com", "not-my-password"))
        }
        // 403, not 401: a wrong password mustn't log the app out.
        assertEquals(HttpStatusCode.Forbidden, wrong.status)
        assertEquals("WRONG_PASSWORD", wrong.body<ErrorResponse>().error)

        val taken = client.put("/me/email") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(ChangeEmailRequest("${other.username}@example.com", "correct-horse"))
        }
        assertEquals("EMAIL_TAKEN", taken.body<ErrorResponse>().error)

        val newEmail = "${me.username}@new.example.com"
        val changed = client.put("/me/email") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(ChangeEmailRequest(newEmail, "correct-horse"))
        }.body<CurrentUserDto>()
        assertEquals(newEmail, changed.email)

        val password = client.put("/me/password") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest("correct-horse", "battery-staple"))
        }
        assertEquals(HttpStatusCode.NoContent, password.status)
        assertEquals(HttpStatusCode.Unauthorized, client.login(newEmail, "correct-horse").status)
        assertEquals(HttpStatusCode.OK, client.login(newEmail, "battery-staple").status)

        val tooShort = client.put("/me/password") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest("battery-staple", "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, tooShort.status)
    }

    @Test
    fun `a profile picture is stored, served, replaced and removed`() = apiTest { client ->
        val me = client.registerUser("me")

        val first = client.uploadAvatar(me, png).body<CurrentUserDto>()
        val firstPath = assertNotNull(first.avatarUrl)
        assertTrue(firstPath.startsWith("/avatars/") && firstPath.endsWith(".png"), firstPath)
        val served = client.get(firstPath)
        assertEquals(HttpStatusCode.OK, served.status)
        assertContentEquals(png, served.readRawBytes())

        // A new picture gets a new address, and the old file goes.
        val second = client.uploadAvatar(me, png).body<CurrentUserDto>()
        assertNotEquals(firstPath, second.avatarUrl)
        assertEquals(HttpStatusCode.NotFound, client.get(firstPath).status)

        val removed = client.delete("/me/avatar") { bearerAuth(me.token) }.body<CurrentUserDto>()
        assertNull(removed.avatarUrl)
        assertEquals(HttpStatusCode.NotFound, client.get(second.avatarUrl!!).status)
    }

    @Test
    fun `only real images of a sensible size are accepted`() = apiTest { client ->
        val me = client.registerUser("me")

        assertEquals(HttpStatusCode.BadRequest, client.uploadAvatar(me, "<script>".toByteArray()).status)
        val huge = png + ByteArray(2 * 1024 * 1024)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.uploadAvatar(me, huge).status)
        // Names that aren't stored pictures are never served, so no ../ tricks.
        assertEquals(HttpStatusCode.NotFound, client.get("/avatars/..%2F..%2Fbuild.gradle.kts").status)
    }

    @Test
    fun `deleting the account needs the password and hands over owned groups`() = apiTest { client ->
        val me = client.registerUser("me")
        val friend = client.registerUser("friend")
        client.befriend(me, friend)
        val group = client.post("/groups") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest("Mine", listOf(friend.id)))
        }.body<GroupDto>()

        val wrong = client.delete("/me") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("nope"))
        }
        assertEquals(HttpStatusCode.Forbidden, wrong.status)

        val deleted = client.delete("/me") {
            bearerAuth(me.token)
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest("correct-horse"))
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.Unauthorized, client.login(me.username, "correct-horse").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/me") { bearerAuth(me.token) }.status)

        val handedOver = client.get("/groups/${group.id}") { bearerAuth(friend.token) }.body<GroupDto>()
        assertEquals(friend.id, handedOver.ownerId)
        assertEquals(listOf(friend.id), handedOver.members.map { it.id })
    }

    @Test
    fun `account changes need a login`() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.put("/me/password").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/me/avatar").status)
        assertEquals(HttpStatusCode.Unauthorized, client.patch("/me/privacy").status)
        // Registration still answers with the new privacy defaults.
        val fresh = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("fresh_" + System.nanoTime() % 100000, "f${System.nanoTime()}@example.com", "correct-horse"))
        }.body<AuthResponse>()
        assertTrue(fresh.user.privacy.showInMatches)
    }
}

package com.example.movielist.backend

import com.example.movielist.backend.features.auth.AuthResponse
import com.example.movielist.backend.features.auth.CurrentUserDto
import com.example.movielist.backend.features.auth.JwtService
import com.example.movielist.backend.features.auth.LoginRequest
import com.example.movielist.backend.features.auth.RegisterRequest
import com.example.movielist.backend.features.auth.UpdateMeRequest
import com.example.movielist.backend.plugins.AppJson
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthTest {

    private fun authTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { module(TestDatabase.config, TestDatabase.dataSource) }
        val client = createClient { install(ContentNegotiation) { json(AppJson) } }
        block(client)
    }

    private fun uniqueName() = "u_" + UUID.randomUUID().toString().replace("-", "").take(12)

    private suspend fun HttpClient.register(
        username: String = uniqueName(),
        email: String = "$username@example.com",
        password: String = "correct-horse",
    ): HttpResponse = post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(username, email, password))
    }

    private suspend fun HttpClient.login(login: String, password: String): HttpResponse = post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(login, password))
    }

    @Test
    fun `register returns token that works for me`() = authTest { client ->
        val name = uniqueName()
        val response = client.register(username = name)
        assertEquals(HttpStatusCode.Created, response.status)

        val auth = response.body<AuthResponse>()
        assertEquals(name, auth.user.username)

        val me = client.get("/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(auth.user.id, me.body<CurrentUserDto>().id)
    }

    @Test
    fun `login works with username or email, case-insensitively`() = authTest { client ->
        val name = uniqueName()
        client.register(username = name, password = "correct-horse")

        assertEquals(HttpStatusCode.OK, client.login(name.uppercase(), "correct-horse").status)
        assertEquals(HttpStatusCode.OK, client.login("$name@EXAMPLE.com", "correct-horse").status)
    }

    @Test
    fun `wrong password and unknown user give the same error`() = authTest { client ->
        val name = uniqueName()
        client.register(username = name)

        val wrongPassword = client.login(name, "wrong-password")
        val unknownUser = client.login(uniqueName(), "whatever123")

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownUser.status)
        assertEquals(wrongPassword.body<ErrorResponse>(), unknownUser.body<ErrorResponse>())
    }

    @Test
    fun `duplicate username and email are rejected`() = authTest { client ->
        val name = uniqueName()
        client.register(username = name)

        val sameName = client.register(username = name.uppercase(), email = "${uniqueName()}@example.com")
        assertEquals(HttpStatusCode.Conflict, sameName.status)
        assertEquals("USERNAME_TAKEN", sameName.body<ErrorResponse>().error)

        val sameEmail = client.register(username = uniqueName(), email = "$name@example.com")
        assertEquals(HttpStatusCode.Conflict, sameEmail.status)
        assertEquals("EMAIL_TAKEN", sameEmail.body<ErrorResponse>().error)
    }

    @Test
    fun `invalid registration input is rejected`() = authTest { client ->
        val cases = listOf(
            client.register(username = "ab"),                                   // too short
            client.register(username = "has space"),                            // bad characters
            client.register(email = "not-an-email"),
            client.register(password = "short"),
            client.register(password = "x".repeat(73)),                         // over BCrypt's limit
        )
        cases.forEach { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("VALIDATION_ERROR", response.body<ErrorResponse>().error)
        }
    }

    @Test
    fun `malformed body returns bad request`() = authTest { client ->
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"only"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("BAD_REQUEST", response.body<ErrorResponse>().error)
    }

    @Test
    fun `me requires a valid token`() = authTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth("garbage") }.status)

        val forged = JwtService("some-other-secret").issueToken(UUID.randomUUID())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(forged) }.status)

        val expired = JwtService(TestDatabase.config.jwtSecret, validity = Duration.ofSeconds(-60))
            .issueToken(UUID.randomUUID())
        val response = client.get("/me") { bearerAuth(expired) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("UNAUTHORIZED", response.body<ErrorResponse>().error)
    }

    @Test
    fun `patch me updates and clears profile fields`() = authTest { client ->
        val token = client.register().body<AuthResponse>().token

        val updated = client.patch("/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateMeRequest(displayName = "  Stelios  ", avatarEmoji = "🍿", avatarColor = "#e50914"))
        }.body<CurrentUserDto>()
        assertEquals("Stelios", updated.displayName)
        assertEquals("🍿", updated.avatarEmoji)
        assertEquals("#E50914", updated.avatarColor)

        // Only displayName is sent: it gets cleared, the avatar stays.
        val cleared = client.patch("/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateMeRequest(displayName = ""))
        }.body<CurrentUserDto>()
        assertNull(cleared.displayName)
        assertEquals("🍿", cleared.avatarEmoji)

        for (bad in listOf(UpdateMeRequest(avatarEmoji = "abc"), UpdateMeRequest(avatarColor = "red"))) {
            val response = client.patch("/me") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(bad)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, bad.toString())
        }
    }

    @Test
    fun `password is never returned`() = authTest { client ->
        val raw = client.register().body<String>()
        assertTrue("password" !in raw.lowercase(), raw)
    }
}

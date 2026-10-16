package com.example.movielist.backend

import com.example.movielist.backend.features.auth.AuthResponse
import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.auth.RegisterRequest
import com.example.movielist.backend.features.friends.FriendRequestBody
import com.example.movielist.backend.features.reactions.ReactionDto
import com.example.movielist.backend.features.reactions.SetReactionRequest
import com.example.movielist.backend.plugins.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Movie id TMDB answers 404 for in [fakeTmdbEngine]. */
const val MISSING_MOVIE = 999_999_999

/** Fake TMDB where every movie id exists (title "Movie <id>"), except [MISSING_MOVIE]. */
fun fakeTmdbEngine() = MockEngine { request ->
    val headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    val id = request.url.encodedPath.substringAfterLast('/').toIntOrNull()
    if (id == null || id == MISSING_MOVIE) {
        respond("""{"status_code":34}""", HttpStatusCode.NotFound, headers)
    } else {
        respond(
            """{"id":$id,"title":"Movie $id","overview":"","genres":[{"id":35,"name":"Comedy"}],"poster_path":"/$id.jpg"}""",
            HttpStatusCode.OK,
            headers,
        )
    }
}

/** The full app on the embedded database and [fakeTmdbEngine], with a JSON client. */
fun apiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
    application { module(TestDatabase.config, TestDatabase.dataSource, fakeTmdbEngine()) }
    block(createClient { install(ContentNegotiation) { json(AppJson) } })
}

data class TestUser(val token: String, val id: String, val username: String)

suspend fun HttpClient.registerUser(prefix: String = "t"): TestUser {
    val name = prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(12)
    val auth = post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(name, "$name@example.com", "correct-horse"))
    }.body<AuthResponse>()
    return TestUser(auth.token, auth.user.id, name)
}

private val movieIds = AtomicInteger((System.nanoTime() % 1_000_000).toInt() * 100)

/** A movie id no other test uses, so tests sharing the database don't share movies. */
fun uniqueMovieId() = 20_000_000 + movieIds.incrementAndGet()

suspend fun HttpClient.react(user: TestUser, movieId: Int, reaction: Reaction = Reaction.LIKE): ReactionDto =
    put("/movies/$movieId/reaction") {
        bearerAuth(user.token)
        contentType(ContentType.Application.Json)
        setBody(SetReactionRequest(reaction))
    }.body()

/** [a] asks, [b] accepts. */
suspend fun HttpClient.befriend(a: TestUser, b: TestUser) {
    post("/friends/requests") {
        bearerAuth(a.token)
        contentType(ContentType.Application.Json)
        setBody(FriendRequestBody(b.id))
    }
    post("/friends/requests/${a.id}/accept") { bearerAuth(b.token) }
}

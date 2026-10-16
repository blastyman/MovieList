package com.example.movielist.backend

import com.example.movielist.backend.db.MoviesCache
import com.example.movielist.backend.features.auth.AuthResponse
import com.example.movielist.backend.features.auth.RegisterRequest
import com.example.movielist.backend.features.movies.MovieDetailsDto
import com.example.movielist.backend.features.movies.MoviePageDto
import com.example.movielist.backend.plugins.AppJson
import com.example.movielist.backend.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovieTest {

    /** Fake TMDB: records every request and answers with [respond]. */
    private class FakeTmdb(
        val respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val requests = CopyOnWriteArrayList<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(request)
        }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun movieTest(
        tmdb: FakeTmdb,
        block: suspend ApplicationTestBuilder.(client: HttpClient, token: String) -> Unit,
    ) = testApplication {
        application {
            module(
                TestDatabase.config.copy(tmdbToken = "Bearer test-tmdb-token"),
                TestDatabase.dataSource,
                tmdb.engine,
            )
        }
        val client = createClient { install(ContentNegotiation) { json(AppJson) } }
        val name = "m_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val token = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(name, "$name@example.com", "correct-horse"))
        }.body<AuthResponse>().token
        block(client, token)
    }

    private val pageJson = """
        {"page":2,"total_pages":40,"total_results":800,"results":[
          {"id":550,"title":"Fight Club","overview":"An insomniac…","poster_path":"/p.jpg",
           "backdrop_path":"/b.jpg","genre_ids":[18],"release_date":"1999-10-15","vote_average":8.4,
           "popularity":61.4,"adult":false},
          {"id":13,"title":null,"genre_ids":[]}
        ]}
    """.trimIndent()

    private fun detailsJson(id: Int) = """
        {"id":$id,"title":"Movie $id","overview":"Plot","tagline":"","poster_path":"/p.jpg",
         "backdrop_path":null,"genres":[{"id":18,"name":"Drama"}],"release_date":"2001-01-01",
         "runtime":120,"vote_average":7.1,"budget":1000}
    """.trimIndent()

    @Test
    fun `movies require login`() = movieTest(FakeTmdb { json(pageJson) }) { client, _ ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/movies/discover").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/movies/550").status)
    }

    @Test
    fun `discover maps TMDB to our format and forwards parameters`() {
        val tmdb = FakeTmdb { json(pageJson) }
        movieTest(tmdb) { client, token ->
            val page = client.get("/movies/discover?page=2&genre=35") { bearerAuth(token) }.body<MoviePageDto>()

            assertEquals(2, page.page)
            assertEquals(40, page.totalPages)
            assertEquals(listOf(550, 13), page.results.map { it.id })
            assertEquals("/p.jpg", page.results[0].posterPath)
            assertEquals(listOf(18), page.results[0].genreIds)
            assertEquals("", page.results[1].title) // null from TMDB becomes empty

            val request = tmdb.requests.single()
            assertEquals("/3/discover/movie", request.url.encodedPath)
            assertEquals("2", request.url.parameters["page"])
            assertEquals("35", request.url.parameters["with_genres"])
            // "Bearer " from the env value is not doubled, and the user's JWT is never forwarded.
            assertEquals("Bearer test-tmdb-token", request.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun `discover pages are cached`() {
        val tmdb = FakeTmdb { json(pageJson) }
        movieTest(tmdb) { client, token ->
            repeat(3) { client.get("/movies/discover?page=7") { bearerAuth(token) } }
            client.get("/movies/discover?page=8") { bearerAuth(token) }
            assertEquals(2, tmdb.requests.size)
        }
    }

    @Test
    fun `invalid parameters are rejected before calling TMDB`() {
        val tmdb = FakeTmdb { json(pageJson) }
        movieTest(tmdb) { client, token ->
            listOf(
                "/movies/discover?page=0",
                "/movies/discover?page=501",
                "/movies/discover?page=abc",
                "/movies/discover?genre=-1",
                "/movies/search",
                "/movies/search?q=%20%20",
                "/movies/search?q=" + "x".repeat(101),
                "/movies/not-a-number",
                "/movies/-5",
            ).forEach { path ->
                val response = client.get(path) { bearerAuth(token) }
                assertEquals(HttpStatusCode.BadRequest, response.status, path)
                assertEquals("VALIDATION_ERROR", response.body<ErrorResponse>().error, path)
            }
            assertTrue(tmdb.requests.isEmpty())
        }
    }

    @Test
    fun `search forwards the trimmed query`() {
        val tmdb = FakeTmdb { json(pageJson) }
        movieTest(tmdb) { client, token ->
            val page = client.get("/movies/search?q=%20fight%20club%20") { bearerAuth(token) }.body<MoviePageDto>()
            assertEquals(2, page.results.size)
            assertEquals("/3/search/movie", tmdb.requests.single().url.encodedPath)
            assertEquals("fight club", tmdb.requests.single().url.parameters["query"])
        }
    }

    @Test
    fun `details are fetched once then served from the database`() {
        val id = uniqueMovieId()
        val tmdb = FakeTmdb { json(detailsJson(id)) }
        movieTest(tmdb) { client, token ->
            val first = client.get("/movies/$id") { bearerAuth(token) }.body<MovieDetailsDto>()
            val second = client.get("/movies/$id") { bearerAuth(token) }.body<MovieDetailsDto>()

            assertEquals("Movie $id", first.title)
            assertEquals("Drama", first.genres.single().name)
            assertNull(first.tagline) // blank tagline becomes null
            assertEquals(first, second)
            assertEquals(1, tmdb.requests.size)
        }
    }

    @Test
    fun `unknown movie is a 404`() {
        val tmdb = FakeTmdb { json("""{"success":false,"status_code":34}""", HttpStatusCode.NotFound) }
        movieTest(tmdb) { client, token ->
            val response = client.get("/movies/${uniqueMovieId()}") { bearerAuth(token) }
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("NOT_FOUND", response.body<ErrorResponse>().error)
        }
    }

    @Test
    fun `TMDB failure becomes 502 without leaking details`() {
        val tmdb = FakeTmdb { json("""{"status_message":"Invalid API key"}""", HttpStatusCode.Unauthorized) }
        movieTest(tmdb) { client, token ->
            val response = client.get("/movies/discover") { bearerAuth(token) }
            assertEquals(HttpStatusCode.BadGateway, response.status)
            val error = response.body<ErrorResponse>()
            assertEquals("TMDB_UNAVAILABLE", error.error)
            assertTrue("API key" !in error.message)
        }
    }

    @Test
    fun `stale cached details are served when TMDB is down`() {
        val id = uniqueMovieId()
        val stale = MovieDetailsDto(id, "Old title", "", null, null, null, emptyList(), null, null, null)
        transaction(Database.connect(TestDatabase.dataSource)) {
            MoviesCache.upsert {
                it[movieId] = id
                it[json] = stale
                it[fetchedAt] = OffsetDateTime.now().minusDays(3)
            }
        }
        val tmdb = FakeTmdb { json("{}", HttpStatusCode.ServiceUnavailable) }
        movieTest(tmdb) { client, token ->
            val response = client.get("/movies/$id") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Old title", response.body<MovieDetailsDto>().title)
            assertEquals(1, tmdb.requests.size) // it did try to refresh first
        }
    }

    /** Distinct ids so tests sharing the database don't see each other's cache rows. */
    private fun uniqueMovieId() = Random.nextInt(1_000_000, 2_000_000_000)
}

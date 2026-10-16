package com.example.movielist.backend.tmdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** TMDB answered with an error, or couldn't be reached. [status] is null for network failures. */
class TmdbException(val status: HttpStatusCode?, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * The only place the backend talks to TMDB. The token never leaves the server, so the
 * Android app doesn't need to ship it.
 *
 * @param engine CIO in production, a MockEngine in tests.
 */
class TmdbClient(
    engine: HttpClientEngine,
    token: String,
    baseUrl: String = DEFAULT_BASE_URL,
) : AutoCloseable {

    private val bearerToken = token.removePrefix("Bearer ").trim()

    private val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
        defaultRequest {
            url(baseUrl)
            if (bearerToken.isNotEmpty()) bearerAuth(bearerToken)
        }
    }

    suspend fun discover(page: Int, genreId: Int?): TmdbPage =
        request {
            get("discover/movie") {
                parameter("sort_by", "popularity.desc")
                parameter("include_adult", false)
                parameter("page", page)
                genreId?.let { parameter("with_genres", it) }
            }
        }

    suspend fun search(query: String, page: Int): TmdbPage =
        request {
            get("search/movie") {
                parameter("query", query)
                parameter("include_adult", false)
                parameter("page", page)
            }
        }

    /** Trailers, teasers, clips… of a movie. Empty for an unknown movie id. */
    suspend fun videos(id: Int): TmdbVideos =
        try {
            request { get("movie/$id/videos") }
        } catch (e: TmdbException) {
            if (e.status == HttpStatusCode.NotFound) TmdbVideos() else throw e
        }

    /** Returns null when TMDB has no movie with that id. */
    suspend fun movieDetails(id: Int): TmdbMovieDetails? =
        try {
            request { get("movie/$id") }
        } catch (e: TmdbException) {
            if (e.status == HttpStatusCode.NotFound) null else throw e
        }

    private suspend inline fun <reified T> request(block: HttpClient.() -> HttpResponse): T {
        val response = try {
            http.block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TmdbException(null, "TMDB unreachable: ${e.message}", e)
        }
        if (!response.status.isSuccess()) {
            throw TmdbException(response.status, "TMDB returned ${response.status}")
        }
        return response.body()
    }

    override fun close() = http.close()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
    }
}

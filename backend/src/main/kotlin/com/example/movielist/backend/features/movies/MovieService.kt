package com.example.movielist.backend.features.movies

import com.example.movielist.backend.db.MoviesCache
import com.example.movielist.backend.db.query
import com.example.movielist.backend.plugins.ApiException
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.ValidationException
import com.example.movielist.backend.tmdb.TmdbClient
import com.example.movielist.backend.tmdb.TmdbException
import com.example.movielist.backend.tmdb.TmdbMovieDetails
import com.example.movielist.backend.tmdb.TmdbMovieSummary
import com.example.movielist.backend.tmdb.TmdbPage
import com.example.movielist.backend.util.TtlCache
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TmdbUnavailableException :
    ApiException(HttpStatusCode.BadGateway, "TMDB_UNAVAILABLE", "The movie database is unavailable right now")

class MovieService(
    private val tmdb: TmdbClient,
    private val db: Database,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(MovieService::class.java)

    // Lists are identical for every user, so one cached copy serves everybody.
    private val discoverCache = TtlCache<Pair<Int, Int?>, MoviePageDto>(Duration.ofMinutes(10), clock = clock)
    private val searchCache = TtlCache<Pair<String, Int>, MoviePageDto>(Duration.ofMinutes(10), clock = clock)

    suspend fun discover(page: Int?, genreId: Int?): MoviePageDto {
        val validPage = validatePage(page)
        if (genreId != null && genreId <= 0) throw ValidationException("genre must be a positive id")
        return discoverCache.getOrPut(validPage to genreId) {
            callTmdb { tmdb.discover(validPage, genreId) }.toDto()
        }
    }

    suspend fun search(query: String?, page: Int?): MoviePageDto {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty()) throw ValidationException("q is required")
        if (trimmed.length > MAX_QUERY_LENGTH) throw ValidationException("q must be at most $MAX_QUERY_LENGTH characters")
        val validPage = validatePage(page)
        return searchCache.getOrPut(trimmed.lowercase() to validPage) {
            callTmdb { tmdb.search(trimmed, validPage) }.toDto()
        }
    }

    /** Served from movies_cache when fresh; otherwise fetched from TMDB and stored. */
    suspend fun details(id: Int): MovieDetailsDto {
        if (id <= 0) throw ValidationException("Movie id must be positive")

        val cached = db.query {
            MoviesCache.selectAll().where { MoviesCache.movieId eq id }.singleOrNull()
        }
        val now = OffsetDateTime.now(clock)
        if (cached != null && cached[MoviesCache.fetchedAt].plus(DETAILS_TTL).isAfter(now)) {
            return cached[MoviesCache.json]
        }

        val fresh = try {
            callTmdb { tmdb.movieDetails(id) }?.toDto()
        } catch (e: TmdbUnavailableException) {
            // A stale copy is better than an error when TMDB is down.
            if (cached != null) return cached[MoviesCache.json] else throw e
        } ?: throw NotFoundException("No movie with id $id")

        db.query {
            MoviesCache.upsert {
                it[movieId] = id
                it[json] = fresh
                it[fetchedAt] = now.withOffsetSameInstant(ZoneOffset.UTC)
            }
        }
        return fresh
    }

    private fun validatePage(page: Int?): Int {
        val value = page ?: 1
        // TMDB refuses pages above 500.
        if (value !in 1..MAX_PAGE) throw ValidationException("page must be between 1 and $MAX_PAGE")
        return value
    }

    private suspend fun <T> callTmdb(block: suspend () -> T): T =
        try {
            block()
        } catch (e: TmdbException) {
            log.warn("TMDB call failed (status={}): {}", e.status, e.message)
            throw TmdbUnavailableException()
        }

    private companion object {
        const val MAX_PAGE = 500
        const val MAX_QUERY_LENGTH = 100
        val DETAILS_TTL: Duration = Duration.ofHours(24)
    }
}

private fun TmdbPage.toDto() = MoviePageDto(
    page = page,
    totalPages = totalPages.coerceAtMost(500),
    results = results.map { it.toDto() },
)

private fun TmdbMovieSummary.toDto() = MovieSummaryDto(
    id = id,
    title = title.orEmpty(),
    overview = overview.orEmpty(),
    posterPath = posterPath,
    backdropPath = backdropPath,
    genreIds = genreIds,
    releaseDate = releaseDate?.ifBlank { null },
    voteAverage = voteAverage,
)

private fun TmdbMovieDetails.toDto() = MovieDetailsDto(
    id = id,
    title = title.orEmpty(),
    overview = overview.orEmpty(),
    tagline = tagline?.ifBlank { null },
    posterPath = posterPath,
    backdropPath = backdropPath,
    genres = genres.map { GenreDto(it.id, it.name) },
    releaseDate = releaseDate?.ifBlank { null },
    runtime = runtime,
    voteAverage = voteAverage,
)

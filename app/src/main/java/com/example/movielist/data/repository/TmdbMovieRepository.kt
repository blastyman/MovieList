package com.example.movielist.data.repository

import com.example.movielist.data.model.MovieResponse
import com.example.movielist.data.remote.TmdbApi
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.repository.MovieRepository
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

class TmdbMovieRepository(private val api: TmdbApi, private val random: Random = Random.Default) :
    MovieRepository {
    private var cachedMovies: List<Movie>? = null
    private var cacheTimestamp = 0L

    override suspend fun getMovies(): List<Movie> {
        val now = System.currentTimeMillis()
        cachedMovies
            ?.takeIf { now - cacheTimestamp < CACHE_DURATION_MILLIS }
            ?.let {
                return it
            }

        val responses = coroutineScope {
            (1..MAX_PAGE)
                .shuffled(random)
                .take(PAGE_COUNT)
                .map { page -> async { loadPage(page) } }
                .awaitAll()
        }

        val movies =
            responses
                .filter { it.isSuccess }
                .flatMap { it.getOrThrow().results }
                .map { it.toMovie() }
        if (responses.none { it.isSuccess }) {
            throw responses.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IOException("No movies returned")
        }
        val result = movies.distinctBy(Movie::id).shuffled(random)
        if (responses.all { it.isSuccess }) {
            cachedMovies = result
            cacheTimestamp = now
        }
        return result
    }

    private suspend fun loadPage(page: Int): Result<MovieResponse> =
        try {
            Result.success(api.getMovies(page = page))
        } catch (exception: IOException) {
            Result.failure(exception)
        } catch (exception: HttpException) {
            Result.failure(exception)
        }

    private companion object {
        const val PAGE_COUNT = 3
        const val MAX_PAGE = 30
        const val CACHE_DURATION_MILLIS = 5 * 60 * 1000L
    }
}

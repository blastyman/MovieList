package com.example.movielist.data.repository

import com.example.movielist.data.model.MovieDto
import com.example.movielist.data.model.MoviePageDto
import com.example.movielist.data.remote.MovieApi
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackendMovieRepositoryTest {
    @Test
    fun requestsDistinctPagesAndDeduplicatesMovies() = runTest {
        val pages = mutableListOf<Int>()
        val repository =
            BackendMovieRepository(
                api { page ->
                    pages += page
                    MoviePageDto(results = listOf(movie(1), movie(page + 100)))
                },
                Random(1),
            )

        val movies = repository.getMovies()
        assertEquals(3, pages.distinct().size)
        assertTrue(pages.all { it in 1..30 })
        assertEquals(4, movies.size)
    }

    @Test
    fun keepsSuccessfulPagesWhenOneRequestFails() = runTest {
        var calls = 0
        val repository =
            BackendMovieRepository(
                api {
                    if (calls++ == 0) throw IOException("Offline")
                    MoviePageDto(results = listOf(movie(1)))
                }
            )
        assertEquals(listOf(1), repository.getMovies().map { it.id })
    }

    @Test
    fun propagatesFailureWhenEveryRequestFails() = runTest {
        val failure = IOException("Offline")
        val repository = BackendMovieRepository(api { throw failure })
        try {
            repository.getMovies()
            fail("Expected loading to fail")
        } catch (exception: IOException) {
            assertSame(failure, exception)
        }
    }

    @Test
    fun cancellationStopsFurtherRequests() = runTest {
        var calls = 0
        val cancellation = CancellationException("Cancelled")
        val repository =
            BackendMovieRepository(
                api {
                    calls++
                    throw cancellation
                }
            )
        try {
            repository.getMovies()
            fail("Expected cancellation")
        } catch (exception: CancellationException) {
            assertEquals("Cancelled", exception.message)
        }
        assertTrue(calls in 1..3)
    }

    @Test
    fun successfulEmptyResponsesAreNotErrors() = runTest {
        assertTrue(BackendMovieRepository(api { MoviePageDto() }).getMovies().isEmpty())
    }

    @Test
    fun successfulResultsAreServedFromCache() = runTest {
        var calls = 0
        val repository =
            BackendMovieRepository(
                api {
                    calls++
                    MoviePageDto(results = listOf(movie(1)))
                }
            )

        repository.getMovies()
        repository.getMovies()

        assertEquals(3, calls)
    }

    private fun movie(id: Int) = MovieDto(id, "Movie $id", "", null, emptyList())

    private fun api(response: suspend (Int) -> MoviePageDto): MovieApi = MovieApi { page -> response(page) }
}

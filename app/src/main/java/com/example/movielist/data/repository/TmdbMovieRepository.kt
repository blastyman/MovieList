package com.example.movielist.data.repository

import com.example.movielist.data.remote.TmdbApi
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.repository.MovieRepository
import java.io.IOException
import kotlin.random.Random
import retrofit2.HttpException

class TmdbMovieRepository(private val api: TmdbApi, private val random: Random = Random.Default) :
    MovieRepository {
    override suspend fun getMovies(): List<Movie> {
        val movies = mutableListOf<Movie>()
        var lastFailure: Exception? = null
        var successfulRequests = 0

        for (page in (1..MAX_PAGE).shuffled(random).take(PAGE_COUNT)) {
            try {
                movies += api.getMovies(page = page).results.map { it.toMovie() }
                successfulRequests++
            } catch (exception: IOException) {
                lastFailure = exception
            } catch (exception: HttpException) {
                lastFailure = exception
            }
        }

        if (successfulRequests == 0) {
            throw checkNotNull(lastFailure)
        }
        return movies.distinctBy(Movie::id).shuffled(random)
    }

    private companion object {
        const val PAGE_COUNT = 3
        const val MAX_PAGE = 30
    }
}

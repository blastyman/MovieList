package com.example.movielist.data.repository

import com.example.movielist.data.remote.MovieSearchApi
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.repository.MovieSearchRepository

class BackendMovieSearchRepository(private val api: MovieSearchApi) : MovieSearchRepository {
    override suspend fun search(query: String, page: Int): SearchPage<Movie> {
        val result = api.search(query, page)
        return SearchPage(result.results.map { it.toMovie() }, hasMore = result.page < result.totalPages)
    }
}

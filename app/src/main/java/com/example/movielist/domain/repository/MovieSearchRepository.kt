package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.SearchPage

fun interface MovieSearchRepository {
    /** Movies whose title matches [query]. */
    suspend fun search(query: String, page: Int): SearchPage<Movie>
}

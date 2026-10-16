package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Trailer

fun interface TrailerRepository {
    /** Best first; empty when the movie has no trailer. */
    suspend fun trailers(movieId: Int): List<Trailer>
}

package com.example.movielist.data.repository

import com.example.movielist.data.remote.TrailerApi
import com.example.movielist.domain.model.Trailer
import com.example.movielist.domain.repository.TrailerRepository

class BackendTrailerRepository(private val api: TrailerApi) : TrailerRepository {
    override suspend fun trailers(movieId: Int): List<Trailer> = api.trailers(movieId).map { it.toTrailer() }
}

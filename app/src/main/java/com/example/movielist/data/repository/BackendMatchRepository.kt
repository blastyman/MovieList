package com.example.movielist.data.repository

import com.example.movielist.data.remote.MatchApi
import com.example.movielist.domain.model.Match
import com.example.movielist.domain.repository.MatchRepository

class BackendMatchRepository(private val api: MatchApi) : MatchRepository {
    override suspend fun matches(): List<Match> = api.matches().map { it.toMatch() }
}

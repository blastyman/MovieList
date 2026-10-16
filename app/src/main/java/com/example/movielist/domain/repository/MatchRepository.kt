package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Match

fun interface MatchRepository {
    /** Newest like first. */
    suspend fun matches(): List<Match>
}

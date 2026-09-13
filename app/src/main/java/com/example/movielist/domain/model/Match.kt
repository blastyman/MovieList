package com.example.movielist.domain.model

data class Match(
    val userName: String,
    val movieId: Int,
    val movieTitle: String,
    val posterPath: String?,
)

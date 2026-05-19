package com.example.movielist.domain.model

data class AppUser(
    val name: String,
    val likedMovies: MutableSet<Int> = mutableSetOf(),
    val dislikedMovies: MutableList<Int> = mutableListOf()
)
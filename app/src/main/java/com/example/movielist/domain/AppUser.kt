package com.example.movielist.domain

data class AppUser(
    val name: String,
    val likedMovies: MutableSet<Int> = mutableSetOf()
)
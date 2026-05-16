package com.example.movielist.domain

enum class MovieCategory(val displayName: String) {
    RECOMMENDED("Recommended"),
    POPULAR("Popular Movies"),
    COMEDY("Comedy"),
    ACTION("Action"),
    ROMANCE("Romance"),
    SCIFI("Sci-Fi")
}
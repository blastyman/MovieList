package com.example.movielist.domain

enum class MovieCategory(
    val displayName: String,
    val genreId: Int?
) {
    RECOMMENDED("Recommended", null),
    POPULAR("Popular Movies", null),
    COMEDY("Comedy", 35),
    ACTION("Action", 28),
    ROMANCE("Romance", 10749),
    SCIFI("Sci-Fi", 878),
    ANIMATED("Animated", 16)
}
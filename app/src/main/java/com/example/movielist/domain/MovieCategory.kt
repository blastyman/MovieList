package com.example.movielist.domain

enum class MovieCategory(
    val displayName: String,
    val genreId: Int?
) {
    RECOMMENDED("recommended", null),
    POPULAR("popular", null),
    COMEDY("comedy", 35),
    ACTION("action", 28),
    ROMANCE("romance", 10749),
    SCIFI("sci-fi", 878),


    LIKED("liked", null),
    DISLIKED("disliked", null)
}
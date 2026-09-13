package com.example.movielist.domain.model

enum class MovieCategory(val displayName: String, val genreId: Int? = null) {
    RECOMMENDED("recommended"),
    POPULAR("popular"),
    COMEDY("comedy", 35),
    ACTION("action", 28),
    ROMANCE("romance", 10749),
    SCI_FI("sci-fi", 878),
    LIKED("liked"),
    DISLIKED("disliked"),
}

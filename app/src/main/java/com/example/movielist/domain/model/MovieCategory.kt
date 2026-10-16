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

    // Where a movie was opened from; not shelves on the home screen.
    SEARCH("search"),
    PROFILE("profile");

    /** A shelf on the home screen, filled by [com.example.movielist.domain.service.MovieCatalog.categorize]. */
    val isHomeShelf: Boolean
        get() = this != LIKED && this != DISLIKED && this != SEARCH && this != PROFILE
}

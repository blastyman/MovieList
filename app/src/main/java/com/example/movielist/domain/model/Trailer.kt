package com.example.movielist.domain.model

/** A YouTube trailer of a movie. */
data class Trailer(val youtubeKey: String, val name: String) {
    val youtubeUrl: String
        get() = "https://www.youtube.com/watch?v=$youtubeKey"
}

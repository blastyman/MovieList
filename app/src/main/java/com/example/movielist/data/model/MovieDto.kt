package com.example.movielist.data.model

import com.example.movielist.domain.model.Movie

/** A movie as our backend lists it (already camelCase; TMDB's snake_case stays on the server). */
data class MovieDto(
    val id: Int,
    val title: String?,
    val overview: String?,
    val posterPath: String?,
    val genreIds: List<Int>?,
) {
    fun toMovie(): Movie =
        Movie(
            id = id,
            title = title.orEmpty(),
            overview = overview.orEmpty(),
            posterPath = posterPath,
            genreIds = genreIds.orEmpty(),
        )
}

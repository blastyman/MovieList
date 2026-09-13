package com.example.movielist.data.model

import com.example.movielist.domain.model.Movie
import com.google.gson.annotations.SerializedName

data class MovieDto(
    val id: Int,
    val title: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>?,
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

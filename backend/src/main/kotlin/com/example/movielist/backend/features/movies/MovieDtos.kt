package com.example.movielist.backend.features.movies

import kotlinx.serialization.Serializable

/** A movie in a list (discover, search). */
@Serializable
data class MovieSummaryDto(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val genreIds: List<Int>,
    val releaseDate: String?,
    val voteAverage: Double?,
)

@Serializable
data class MoviePageDto(
    val page: Int,
    val totalPages: Int,
    val results: List<MovieSummaryDto>,
)

@Serializable
data class GenreDto(val id: Int, val name: String)

/**
 * One movie in full. Phase 4 adds the social fields (avgRating, myReaction, …) next to these,
 * computed per request rather than cached.
 */
@Serializable
data class MovieDetailsDto(
    val id: Int,
    val title: String,
    val overview: String,
    val tagline: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val genres: List<GenreDto>,
    val releaseDate: String?,
    val runtime: Int?,
    val voteAverage: Double?,
)

package com.example.movielist.backend.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Raw TMDB v3 response shapes. Only the fields we use; unknown keys are ignored.
// They never leave the backend: MovieService maps them to our own DTOs.

@Serializable
data class TmdbPage(
    val page: Int = 1,
    val results: List<TmdbMovieSummary> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TmdbMovieSummary(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class TmdbMovieDetails(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class TmdbGenre(val id: Int, val name: String)

@Serializable
data class TmdbVideos(val results: List<TmdbVideo> = emptyList())

@Serializable
data class TmdbVideo(
    val key: String,
    val name: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean = false,
    val size: Int? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

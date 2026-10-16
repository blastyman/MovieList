package com.example.movielist.backend.features.movies

import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.ratings.RatingStatsDto
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
 * One movie in full, as cached in movies_cache. Shared by every user; the per-user social
 * fields are added by [MovieDetailsResponse].
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

/** What GET /movies/{id} returns: the cached TMDB details plus this user's social data. */
@Serializable
data class MovieDetailsResponse(
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
    val avgRating: Double?,
    val ratingCount: Int,
    val myRating: Int?,
    val myReaction: Reaction?,
)

fun MovieDetailsDto.withSocial(stats: RatingStatsDto, myReaction: Reaction?) = MovieDetailsResponse(
    id = id,
    title = title,
    overview = overview,
    tagline = tagline,
    posterPath = posterPath,
    backdropPath = backdropPath,
    genres = genres,
    releaseDate = releaseDate,
    runtime = runtime,
    voteAverage = voteAverage,
    avgRating = stats.avgRating,
    ratingCount = stats.ratingCount,
    myRating = stats.myRating,
    myReaction = myReaction,
)

fun MovieDetailsDto.toSummary() = MovieSummaryDto(
    id = id,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    genreIds = genres.map { it.id },
    releaseDate = releaseDate,
    voteAverage = voteAverage,
)

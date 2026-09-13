package com.example.movielist.ui.utils

private const val POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"

fun posterUrl(posterPath: String?): String? =
    posterPath?.takeIf { it.isNotBlank() }?.let { "$POSTER_BASE_URL$it" }

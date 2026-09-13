package com.example.movielist.ui.utils

private const val POSTER_BASE_URL = "https://image.tmdb.org/t/p"

fun posterUrl(posterPath: String?, width: Int = 500): String? =
    posterPath?.takeIf { it.isNotBlank() }?.let { "$POSTER_BASE_URL/w$width$it" }

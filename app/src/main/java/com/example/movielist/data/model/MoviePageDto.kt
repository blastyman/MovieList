package com.example.movielist.data.model

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/movies/MovieDtos.kt

data class MoviePageDto(
    val page: Int = 1,
    val totalPages: Int = 0,
    val results: List<MovieDto> = emptyList(),
)

package com.example.movielist.data.model

import com.example.movielist.domain.model.Trailer

// Mirror of TrailerDto in backend/src/main/kotlin/com/example/movielist/backend/features/movies/MovieDtos.kt

data class TrailerDto(val youtubeKey: String, val name: String?, val type: String?, val official: Boolean) {
    fun toTrailer() = Trailer(youtubeKey = youtubeKey, name = name.orEmpty())
}

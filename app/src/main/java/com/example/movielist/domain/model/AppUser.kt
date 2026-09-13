package com.example.movielist.domain.model

data class AppUser(
    val name: String,
    val likedMovieIds: Set<Int> = emptySet(),
    val dislikedMovieIds: Set<Int> = emptySet(),
) {
    fun rateMovie(movieId: Int, liked: Boolean): AppUser =
        copy(
            likedMovieIds = if (liked) likedMovieIds + movieId else likedMovieIds - movieId,
            dislikedMovieIds = if (liked) dislikedMovieIds - movieId else dislikedMovieIds + movieId,
        )
}

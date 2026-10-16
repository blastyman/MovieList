package com.example.movielist.backend.features.reactions

import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.movies.MovieSummaryDto
import kotlinx.serialization.Serializable

@Serializable
data class SetReactionRequest(val reaction: Reaction)

@Serializable
data class ReactionDto(val movieId: Int, val reaction: Reaction)

/** Every movie the user reacted to, as ids only. Small enough to load at start-up. */
@Serializable
data class ReactionIdsDto(val liked: List<Int>, val disliked: List<Int>)

@Serializable
data class ReactedMovieDto(
    val movie: MovieSummaryDto,
    val reaction: Reaction,
    val reactedAt: String,
)

/** One page of reactions, newest first. Pass [nextCursor] as `before` to get the next page. */
@Serializable
data class ReactionPageDto(
    val items: List<ReactedMovieDto>,
    val nextCursor: String?,
)

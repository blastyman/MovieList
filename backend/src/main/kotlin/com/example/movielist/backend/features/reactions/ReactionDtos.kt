package com.example.movielist.backend.features.reactions

import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.movies.MovieSummaryDto
import com.example.movielist.backend.features.matches.MatchPersonDto
import kotlinx.serialization.Serializable

@Serializable
data class SetReactionRequest(val reaction: Reaction)

@Serializable
data class ReactionDto(
    val movieId: Int,
    val reaction: Reaction,
    /** For a like: the best matches among the people who like this movie too. Empty for a dislike. */
    val matchedPeople: List<MatchPersonDto> = emptyList(),
    /** Everyone else who likes it, including those cut from [matchedPeople]. */
    val matchedCount: Int = 0,
)

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

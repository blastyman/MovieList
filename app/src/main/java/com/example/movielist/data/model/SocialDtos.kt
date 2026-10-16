package com.example.movielist.data.model

import com.example.movielist.domain.model.RatingStats
import com.example.movielist.domain.model.Reaction

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/{reactions,ratings}

data class SetReactionRequest(val reaction: Reaction)

/** Reply to a like or dislike; for a like, [matchedPeople] are the closest of the [matchedCount] others who like it. */
data class ReactionDto(
    val movieId: Int,
    val reaction: Reaction,
    val matchedPeople: List<MatchPersonDto>?,
    val matchedCount: Int,
    val completedGroups: List<GroupBriefDto>?,
)

data class ReactionIdsDto(val liked: List<Int>?, val disliked: List<Int>?)

data class ReactedMovieDto(val movie: MovieDto, val reaction: Reaction, val reactedAt: String)

data class ReactionPageDto(val items: List<ReactedMovieDto>?, val nextCursor: String?)

data class SetRatingRequest(val score: Int)

/**
 * Rating fields. Returned by the rating endpoints, and also part of GET /movies/{id}, which
 * is why that response can be read as this type (Gson ignores the other fields).
 */
data class RatingStatsDto(val avgRating: Double?, val ratingCount: Int, val myRating: Int?) {
    fun toRatingStats() = RatingStats(average = avgRating, count = ratingCount, myScore = myRating)
}

package com.example.movielist.domain.repository

import com.example.movielist.domain.model.RatingStats

interface RatingRepository {
    suspend fun loadStats(movieId: Int): RatingStats

    /** [score] 1–10, or null to remove the rating. Returns the updated stats. */
    suspend fun setRating(movieId: Int, score: Int?): RatingStats
}

/** One user's ratings in memory. Used by tests and previews. */
class InMemoryRatingRepository : RatingRepository {
    private val scores = mutableMapOf<Int, Int>()

    override suspend fun loadStats(movieId: Int): RatingStats = statsFor(movieId)

    override suspend fun setRating(movieId: Int, score: Int?): RatingStats {
        if (score == null) scores.remove(movieId) else scores[movieId] = score
        return statsFor(movieId)
    }

    private fun statsFor(movieId: Int): RatingStats {
        val score = scores[movieId]
        return RatingStats(average = score?.toDouble(), count = if (score == null) 0 else 1, myScore = score)
    }
}

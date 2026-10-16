package com.example.movielist.data.repository

import com.example.movielist.data.model.SetRatingRequest
import com.example.movielist.data.remote.RatingApi
import com.example.movielist.domain.model.RatingStats
import com.example.movielist.domain.repository.RatingRepository

class BackendRatingRepository(private val api: RatingApi) : RatingRepository {
    override suspend fun loadStats(movieId: Int): RatingStats = api.stats(movieId).toRatingStats()

    override suspend fun setRating(movieId: Int, score: Int?): RatingStats =
        (if (score == null) api.removeRating(movieId)
            else api.setRating(movieId, SetRatingRequest(score)))
            .toRatingStats()
}

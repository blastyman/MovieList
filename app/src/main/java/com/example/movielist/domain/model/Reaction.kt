package com.example.movielist.domain.model

enum class Reaction {
    LIKE,
    DISLIKE,
}

/** Rating summary of one movie. Scores are 1–10; the UI shows them as five stars with halves. */
data class RatingStats(val average: Double?, val count: Int, val myScore: Int?)

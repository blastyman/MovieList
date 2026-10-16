package com.example.movielist.backend.features.ratings

import com.example.movielist.backend.db.Ratings
import com.example.movielist.backend.db.query
import com.example.movielist.backend.features.movies.MovieService
import com.example.movielist.backend.plugins.ValidationException
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.avg
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import java.math.RoundingMode
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

/** Score 1–10. The app shows it as five stars with halves (score 7 = 3½ stars). */
@Serializable
data class SetRatingRequest(val score: Int)

@Serializable
data class RatingStatsDto(
    /** Average of all users' scores, one decimal; null when nobody has rated yet. */
    val avgRating: Double?,
    val ratingCount: Int,
    val myRating: Int?,
)

class RatingService(
    private val db: Database,
    private val movies: MovieService,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun setRating(userId: UUID, movieId: Int, score: Int): RatingStatsDto {
        if (score !in MIN_SCORE..MAX_SCORE) throw ValidationException("score must be between $MIN_SCORE and $MAX_SCORE")
        movies.details(movieId) // 404 for movies TMDB doesn't know
        db.query {
            Ratings.upsert {
                it[Ratings.userId] = userId
                it[Ratings.movieId] = movieId
                it[Ratings.score] = score.toShort()
                it[updatedAt] = OffsetDateTime.now(clock)
            }
        }
        return stats(userId, movieId)
    }

    suspend fun removeRating(userId: UUID, movieId: Int): RatingStatsDto {
        db.query {
            Ratings.deleteWhere { (Ratings.userId eq userId) and (Ratings.movieId eq movieId) }
        }
        return stats(userId, movieId)
    }

    suspend fun stats(userId: UUID, movieId: Int): RatingStatsDto = db.query {
        val average = Ratings.score.avg(scale = 2)
        val count = Ratings.score.count()
        val totals = Ratings.select(average, count).where { Ratings.movieId eq movieId }.single()
        val mine = Ratings.select(Ratings.score)
            .where { (Ratings.movieId eq movieId) and (Ratings.userId eq userId) }
            .singleOrNull()
            ?.get(Ratings.score)
            ?.toInt()

        RatingStatsDto(
            avgRating = totals[average]?.setScale(1, RoundingMode.HALF_UP)?.toDouble(),
            ratingCount = totals[count].toInt(),
            myRating = mine,
        )
    }

    private companion object {
        const val MIN_SCORE = 1
        const val MAX_SCORE = 10
    }
}

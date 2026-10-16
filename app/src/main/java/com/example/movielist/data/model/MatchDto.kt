package com.example.movielist.data.model

import com.example.movielist.domain.model.Match
import com.example.movielist.domain.model.MatchPerson
import java.time.Instant

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/matches/MatchService.kt

data class MatchPersonDto(val user: UserSummaryDto, val sharedLikes: Int) {
    fun toMatchPerson() = MatchPerson(user.toUserSummary(), sharedLikes)
}

data class MatchDto(val movie: MovieDto, val people: List<MatchPersonDto>?, val peopleCount: Int, val likedAt: String) {
    fun toMatch() =
        Match(movie.toMovie(), people.orEmpty().map { it.toMatchPerson() }, peopleCount, Instant.parse(likedAt))
}

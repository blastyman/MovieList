package com.example.movielist.domain.model

import java.time.Instant

/** Someone, friend or stranger, who likes a movie you like, and how many likes you share. */
data class MatchPerson(val user: UserSummary, val sharedLikes: Int)

/**
 * Who else likes a movie: the closest [people] first, and how many there are in all; plus your
 * groups in which everyone now likes it.
 */
data class MatchPeople(
    val people: List<MatchPerson>,
    val count: Int,
    val completedGroups: List<GroupBrief> = emptyList(),
) {
    companion object {
        val NONE = MatchPeople(emptyList(), 0)
    }
}

/** A movie the signed-in user and at least one other person both liked. */
data class Match(
    val movie: Movie,
    val people: List<MatchPerson>,
    val peopleCount: Int,
    val likedAt: Instant,
    /** Groups in which everyone likes it, for the popup right after a like. */
    val completedGroups: List<GroupBrief> = emptyList(),
)

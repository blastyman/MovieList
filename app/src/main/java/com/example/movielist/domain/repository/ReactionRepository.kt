package com.example.movielist.domain.repository

import com.example.movielist.domain.model.MatchPeople
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.Reaction

/** The signed-in account's likes and dislikes, stored on the server. */
interface ReactionRepository {
    /** Movie id → reaction, for every movie the user reacted to. */
    suspend fun loadReactions(): Map<Int, Reaction>

    /** Movies with [reaction], newest first. */
    suspend fun loadMovies(reaction: Reaction): List<Movie>

    /**
     * Null removes the reaction. For a like, returns who else likes the movie (a match, friends
     * and strangers alike); otherwise [MatchPeople.NONE].
     */
    suspend fun setReaction(movieId: Int, reaction: Reaction?): MatchPeople
}

/** Keeps everything in memory. Used by tests and previews. */
class InMemoryReactionRepository : ReactionRepository {
    private val reactions = linkedMapOf<Int, Reaction>()
    val movies = mutableMapOf<Int, Movie>()

    /** Who else likes each movie, for tests that need a match. */
    val likedBy = mutableMapOf<Int, MatchPeople>()

    override suspend fun loadReactions(): Map<Int, Reaction> = reactions.toMap()

    override suspend fun loadMovies(reaction: Reaction): List<Movie> =
        reactions.entries.reversed().filter { it.value == reaction }.mapNotNull { movies[it.key] }

    override suspend fun setReaction(movieId: Int, reaction: Reaction?): MatchPeople {
        reactions.remove(movieId)
        if (reaction != null) reactions[movieId] = reaction
        return if (reaction == Reaction.LIKE) likedBy[movieId] ?: MatchPeople.NONE else MatchPeople.NONE
    }
}

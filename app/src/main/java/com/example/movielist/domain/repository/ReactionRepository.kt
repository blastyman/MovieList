package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.Reaction

/** The signed-in account's likes and dislikes, stored on the server. */
interface ReactionRepository {
    /** Movie id → reaction, for every movie the user reacted to. */
    suspend fun loadReactions(): Map<Int, Reaction>

    /** Movies with [reaction], newest first. */
    suspend fun loadMovies(reaction: Reaction): List<Movie>

    /** Null removes the reaction. */
    suspend fun setReaction(movieId: Int, reaction: Reaction?)
}

/** Keeps everything in memory. Used by tests and previews. */
class InMemoryReactionRepository : ReactionRepository {
    private val reactions = linkedMapOf<Int, Reaction>()
    val movies = mutableMapOf<Int, Movie>()

    override suspend fun loadReactions(): Map<Int, Reaction> = reactions.toMap()

    override suspend fun loadMovies(reaction: Reaction): List<Movie> =
        reactions.entries.reversed().filter { it.value == reaction }.mapNotNull { movies[it.key] }

    override suspend fun setReaction(movieId: Int, reaction: Reaction?) {
        reactions.remove(movieId)
        if (reaction != null) reactions[movieId] = reaction
    }
}

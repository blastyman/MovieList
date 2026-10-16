package com.example.movielist.data.repository

import com.example.movielist.data.model.SetReactionRequest
import com.example.movielist.data.remote.ReactionApi
import com.example.movielist.domain.model.MatchPeople
import com.example.movielist.domain.model.Movie
import com.example.movielist.domain.model.Reaction
import com.example.movielist.domain.repository.ReactionRepository

class BackendReactionRepository(private val api: ReactionApi) : ReactionRepository {

    override suspend fun loadReactions(): Map<Int, Reaction> {
        val ids = api.ids()
        return ids.liked.orEmpty().associateWith { Reaction.LIKE } +
            ids.disliked.orEmpty().associateWith { Reaction.DISLIKE }
    }

    /** Follows the server's cursor up to [MAX_MOVIES] movies, which is plenty for a list screen. */
    override suspend fun loadMovies(reaction: Reaction): List<Movie> {
        val movies = mutableListOf<Movie>()
        var cursor: String? = null
        do {
            val page = api.page(reaction, PAGE_SIZE, cursor)
            movies += page.items.orEmpty().map { it.movie.toMovie() }
            cursor = page.nextCursor
        } while (cursor != null && movies.size < MAX_MOVIES)
        return movies
    }

    override suspend fun setReaction(movieId: Int, reaction: Reaction?): MatchPeople {
        if (reaction == null) {
            api.removeReaction(movieId)
            return MatchPeople.NONE
        }
        val saved = api.setReaction(movieId, SetReactionRequest(reaction))
        return MatchPeople(saved.matchedPeople.orEmpty().map { it.toMatchPerson() }, saved.matchedCount)
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_MOVIES = 500
    }
}

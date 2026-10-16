package com.example.movielist.ui.friends

import com.example.movielist.domain.model.FriendRequest
import com.example.movielist.domain.model.FriendRequests
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.FriendRepository
import java.io.IOException
import java.time.Instant

/** In-memory friendships of the signed-in user with others, keyed by user id. */
class FakeFriends(var failWith: IOException? = null) : FriendRepository {
    val states = mutableMapOf<String, FriendshipState>()
    private val people = mutableMapOf<String, UserSummary>()

    fun add(id: String, state: FriendshipState): UserSummary =
        UserSummary(id, "user$id", null, state).also {
            people[id] = it
            states[id] = state
        }

    private fun having(state: FriendshipState) =
        states.filterValues { it == state }.keys.map { people.getValue(it).copy(friendship = state) }

    override suspend fun friends() = having(FriendshipState.FRIENDS)

    override suspend fun requests() =
        FriendRequests(
            incoming = having(FriendshipState.REQUEST_RECEIVED).map { FriendRequest(it, Instant.EPOCH) },
            outgoing = having(FriendshipState.REQUEST_SENT).map { FriendRequest(it, Instant.EPOCH) },
        )

    private fun set(userId: String, state: FriendshipState): FriendshipState {
        failWith?.let { throw it }
        states[userId] = state
        return state
    }

    override suspend fun sendRequest(userId: String) =
        set(userId, if (states[userId] == FriendshipState.REQUEST_RECEIVED) FriendshipState.FRIENDS else FriendshipState.REQUEST_SENT)

    override suspend fun accept(userId: String) = set(userId, FriendshipState.FRIENDS)

    override suspend fun decline(userId: String) = set(userId, FriendshipState.NONE)

    override suspend fun cancel(userId: String) = set(userId, FriendshipState.NONE)

    override suspend fun remove(userId: String) = set(userId, FriendshipState.NONE)
}

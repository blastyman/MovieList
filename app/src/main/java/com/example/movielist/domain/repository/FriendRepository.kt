package com.example.movielist.domain.repository

import com.example.movielist.domain.model.FriendRequests
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.UserSummary

/** Friend requests and friendships. Every change returns the new state with that user. */
interface FriendRepository {
    suspend fun friends(): List<UserSummary>

    suspend fun requests(): FriendRequests

    /** Accepts instead if they already asked; asking again changes nothing. */
    suspend fun sendRequest(userId: String): FriendshipState

    suspend fun accept(userId: String): FriendshipState

    suspend fun decline(userId: String): FriendshipState

    /** Withdraws a request the signed-in user sent. */
    suspend fun cancel(userId: String): FriendshipState

    suspend fun remove(userId: String): FriendshipState
}

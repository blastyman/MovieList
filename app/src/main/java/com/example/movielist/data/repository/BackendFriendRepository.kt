package com.example.movielist.data.repository

import com.example.movielist.data.model.FriendRequestBody
import com.example.movielist.data.remote.FriendApi
import com.example.movielist.domain.model.FriendRequests
import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.FriendRepository

class BackendFriendRepository(private val api: FriendApi) : FriendRepository {
    override suspend fun friends(): List<UserSummary> = api.friends().map { it.toUserSummary() }

    override suspend fun requests(): FriendRequests = api.requests().toFriendRequests()

    override suspend fun sendRequest(userId: String): FriendshipState = api.send(FriendRequestBody(userId)).state()

    override suspend fun accept(userId: String): FriendshipState = api.accept(userId).state()

    override suspend fun decline(userId: String): FriendshipState = api.decline(userId).state()

    override suspend fun cancel(userId: String): FriendshipState = api.cancel(userId).state()

    override suspend fun remove(userId: String): FriendshipState = api.remove(userId).state()
}

package com.example.movielist.data.remote

import com.example.movielist.data.model.FriendRequestBody
import com.example.movielist.data.model.FriendRequestsDto
import com.example.movielist.data.model.FriendshipDto
import com.example.movielist.data.model.UserSummaryDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FriendApi {
    @GET("friends") suspend fun friends(): List<UserSummaryDto>

    @DELETE("friends/{userId}") suspend fun remove(@Path("userId") userId: String): FriendshipDto

    @GET("friends/requests") suspend fun requests(): FriendRequestsDto

    @POST("friends/requests") suspend fun send(@Body body: FriendRequestBody): FriendshipDto

    @POST("friends/requests/{userId}/accept") suspend fun accept(@Path("userId") userId: String): FriendshipDto

    @POST("friends/requests/{userId}/decline") suspend fun decline(@Path("userId") userId: String): FriendshipDto

    @DELETE("friends/requests/{userId}") suspend fun cancel(@Path("userId") userId: String): FriendshipDto
}

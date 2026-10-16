package com.example.movielist.data.remote

import com.example.movielist.data.model.UserProfileDto
import com.example.movielist.data.model.UserSearchPageDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface UserApi {
    @GET("users/search")
    suspend fun search(@Query("q") query: String, @Query("page") page: Int): UserSearchPageDto

    @GET("users/{userId}") suspend fun profile(@Path("userId") userId: String): UserProfileDto
}

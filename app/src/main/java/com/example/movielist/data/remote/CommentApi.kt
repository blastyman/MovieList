package com.example.movielist.data.remote

import com.example.movielist.data.model.CommentDto
import com.example.movielist.data.model.CommentPageDto
import com.example.movielist.data.model.CommentRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommentApi {
    @GET("movies/{id}/comments")
    suspend fun page(
        @Path("id") movieId: Int,
        @Query("before") before: String?,
        @Query("limit") limit: Int = PAGE_SIZE,
    ): CommentPageDto

    @POST("movies/{id}/comments")
    suspend fun post(@Path("id") movieId: Int, @Body request: CommentRequest): CommentDto

    @GET("comments/{commentId}/replies")
    suspend fun replies(
        @Path("commentId") commentId: String,
        @Query("after") after: String?,
        @Query("limit") limit: Int = PAGE_SIZE,
    ): CommentPageDto

    @POST("comments/{commentId}/replies")
    suspend fun reply(@Path("commentId") commentId: String, @Body request: CommentRequest): CommentDto

    @PATCH("comments/{commentId}")
    suspend fun edit(@Path("commentId") commentId: String, @Body request: CommentRequest): CommentDto

    @DELETE("comments/{commentId}") suspend fun delete(@Path("commentId") commentId: String)

    companion object {
        const val PAGE_SIZE = 20
    }
}

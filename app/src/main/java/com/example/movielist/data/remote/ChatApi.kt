package com.example.movielist.data.remote

import com.example.movielist.data.model.ConversationDto
import com.example.movielist.data.model.MessageDto
import com.example.movielist.data.model.MessagePageDto
import com.example.movielist.data.model.SendMessageRequest
import com.example.movielist.data.model.StartDirectRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("conversations") suspend fun conversations(): List<ConversationDto>

    @GET("conversations/{id}") suspend fun conversation(@Path("id") conversationId: String): ConversationDto

    @POST("conversations/direct") suspend fun startDirect(@Body body: StartDirectRequest): ConversationDto

    @GET("conversations/{id}/messages")
    suspend fun messages(@Path("id") conversationId: String, @Query("before") before: String?): MessagePageDto

    @POST("conversations/{id}/messages")
    suspend fun send(@Path("id") conversationId: String, @Body body: SendMessageRequest): MessageDto

    @POST("conversations/{id}/read") suspend fun markRead(@Path("id") conversationId: String): Response<Unit>
}

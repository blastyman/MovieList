package com.example.movielist.data.remote

import com.example.movielist.data.model.AddMemberRequest
import com.example.movielist.data.model.CreateGroupRequest
import com.example.movielist.data.model.GroupDto
import com.example.movielist.data.model.GroupMatchDto
import com.example.movielist.data.model.RenameGroupRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface GroupApi {
    @GET("groups") suspend fun groups(): List<GroupDto>

    @POST("groups") suspend fun create(@Body body: CreateGroupRequest): GroupDto

    @GET("groups/{id}") suspend fun group(@Path("id") groupId: String): GroupDto

    @PATCH("groups/{id}") suspend fun rename(@Path("id") groupId: String, @Body body: RenameGroupRequest): GroupDto

    @GET("groups/{id}/matches") suspend fun matches(@Path("id") groupId: String): List<GroupMatchDto>

    @POST("groups/{id}/members")
    suspend fun addMember(@Path("id") groupId: String, @Body body: AddMemberRequest): GroupDto

    @DELETE("groups/{id}") suspend fun disband(@Path("id") groupId: String): Response<Unit>

    /** 200 with the group when removing someone else; 204 with no body when leaving. */
    @DELETE("groups/{id}/members/{userId}")
    suspend fun removeMember(@Path("id") groupId: String, @Path("userId") userId: String): Response<GroupDto>
}

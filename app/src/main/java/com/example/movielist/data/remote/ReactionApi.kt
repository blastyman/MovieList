package com.example.movielist.data.remote

import com.example.movielist.data.model.ReactionIdsDto
import com.example.movielist.data.model.ReactionPageDto
import com.example.movielist.data.model.SetReactionRequest
import com.example.movielist.domain.model.Reaction
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ReactionApi {
    @GET("me/reactions/ids") suspend fun ids(): ReactionIdsDto

    @GET("me/reactions")
    suspend fun page(
        @Query("type") type: Reaction,
        @Query("limit") limit: Int,
        @Query("before") before: String? = null,
    ): ReactionPageDto

    @PUT("movies/{id}/reaction")
    suspend fun setReaction(@Path("id") movieId: Int, @Body request: SetReactionRequest)

    @DELETE("movies/{id}/reaction") suspend fun removeReaction(@Path("id") movieId: Int)
}

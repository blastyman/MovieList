package com.example.movielist.data.remote

import com.example.movielist.data.model.MatchDto
import retrofit2.http.GET

fun interface MatchApi {
    @GET("matches") suspend fun matches(): List<MatchDto>
}

package com.example.movielist.data.remote

import com.example.movielist.data.model.AccountDto
import com.example.movielist.data.model.AuthResponse
import com.example.movielist.data.model.LoginRequest
import com.example.movielist.data.model.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Our own server (backend/). The JWT is attached by [BackendClient], not per call. */
interface BackendApi {
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("me") suspend fun me(): AccountDto
}

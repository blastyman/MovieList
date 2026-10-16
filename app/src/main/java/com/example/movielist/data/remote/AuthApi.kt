package com.example.movielist.data.remote

import com.example.movielist.data.model.AccountDto
import com.example.movielist.data.model.AuthResponse
import com.example.movielist.data.model.ChangeEmailRequest
import com.example.movielist.data.model.ChangePasswordRequest
import com.example.movielist.data.model.DeleteAccountRequest
import com.example.movielist.data.model.LoginRequest
import com.example.movielist.data.model.RegisterRequest
import com.example.movielist.data.model.UpdateMeRequest
import com.example.movielist.data.model.UpdatePrivacyRequest
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

/** Accounts on our server (backend/). The JWT is attached by [BackendClient], not per call. */
interface AuthApi {
    @POST("auth/register") suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("me") suspend fun me(): AccountDto

    @PATCH("me") suspend fun updateMe(@Body request: UpdateMeRequest): AccountDto

    @PATCH("me/privacy") suspend fun updatePrivacy(@Body request: UpdatePrivacyRequest): AccountDto

    @PUT("me/email") suspend fun changeEmail(@Body request: ChangeEmailRequest): AccountDto

    @PUT("me/password") suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    /** The body is the picture itself (JPEG). */
    @POST("me/avatar") suspend fun uploadAvatar(@Body image: RequestBody): AccountDto

    @DELETE("me/avatar") suspend fun removeAvatar(): AccountDto

    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): Response<Unit>
}

package com.example.movielist.backend.features.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

/** `login` can be either the username or the email. */
@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
)

@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: CurrentUserDto,
)

/** The signed-in user's own profile. Includes the email, unlike public profiles. */
@Serializable
data class CurrentUserDto(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val createdAt: String,
)

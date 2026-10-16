package com.example.movielist.data.model

import com.example.movielist.domain.model.Account

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/auth/AuthDtos.kt

data class RegisterRequest(val username: String, val email: String, val password: String)

data class LoginRequest(val login: String, val password: String)

data class AuthResponse(val token: String, val user: AccountDto)

data class AccountDto(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val createdAt: String,
) {
    fun toAccount() = Account(id, username, email, displayName, avatarUrl)
}

/** Every backend error has this shape: `{ "error": "CODE", "message": "…" }`. */
data class ErrorResponse(val error: String?, val message: String?)

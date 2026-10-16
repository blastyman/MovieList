package com.example.movielist.domain.model

/** The signed-in user's own account on the backend. */
data class Account(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
)

sealed interface SessionState {
    /** The saved token hasn't been read from disk yet. */
    data object Loading : SessionState

    data object LoggedOut : SessionState

    data object LoggedIn : SessionState
}

/** Why a login or registration failed, independent of how the UI words it. */
sealed interface AuthError {
    data object InvalidCredentials : AuthError

    data object UsernameTaken : AuthError

    data object EmailTaken : AuthError

    data object Network : AuthError

    /** Any other server error, with the server's own message. */
    data class Server(val message: String) : AuthError
}

class AuthException(val error: AuthError) : Exception(error.toString())

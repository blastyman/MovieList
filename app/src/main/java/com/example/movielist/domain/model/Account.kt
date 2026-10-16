package com.example.movielist.domain.model

/** The signed-in user's own account on the backend. */
data class Account(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val avatar: Avatar = Avatar.NONE,
    val privacy: Privacy = Privacy(),
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

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

    /** The current password, asked for before a sensitive change, was wrong. */
    data object WrongPassword : AuthError

    data object Network : AuthError

    /** Any other server error, with the server's own message. */
    data class Server(val message: String) : AuthError
}

class AuthException(val error: AuthError) : Exception(error.toString())

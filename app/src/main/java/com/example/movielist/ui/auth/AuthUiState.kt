package com.example.movielist.ui.auth

import com.example.movielist.domain.model.AuthError

enum class AuthMode {
    LOGIN,
    REGISTER,
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    /** Login mode: username or email. */
    val login: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: AuthFormError? = null,
)

/** Everything the form can complain about. The screen maps each to a string resource. */
sealed interface AuthFormError {
    data object EmptyLogin : AuthFormError

    data object EmptyPassword : AuthFormError

    data object InvalidUsername : AuthFormError

    data object InvalidEmail : AuthFormError

    data object PasswordTooShort : AuthFormError

    data object PasswordTooLong : AuthFormError

    data class Remote(val error: AuthError) : AuthFormError
}

package com.example.movielist.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiStateFlow: StateFlow<AuthUiState> = _uiState.asStateFlow()
    val uiState: AuthUiState
        get() = _uiState.value

    fun onLoginChange(value: String) = _uiState.update { it.copy(login = value, error = null) }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun toggleMode() =
        _uiState.update {
            it.copy(
                mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
                password = "",
                error = null,
            )
        }

    fun submit() {
        val state = uiState
        if (state.isSubmitting) return

        validate(state)?.let { error ->
            _uiState.update { it.copy(error = error) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result =
                when (state.mode) {
                    AuthMode.LOGIN -> authRepository.login(state.login, state.password)
                    AuthMode.REGISTER ->
                        authRepository.register(state.username, state.email, state.password)
                }
            // On success the repository switches the session and MainActivity leaves this screen.
            val error =
                result.exceptionOrNull()?.let { exception ->
                    AuthFormError.Remote(
                        (exception as? AuthException)?.error
                            ?: AuthError.Server(exception.message.orEmpty())
                    )
                }
            _uiState.update {
                if (error == null) {
                    // This ViewModel outlives the session: after a logout the form would still hold
                    // the previous account's details, password included. Start empty instead.
                    AuthUiState()
                } else {
                    it.copy(isSubmitting = false, error = error)
                }
            }
        }
    }

    /** Same rules as the backend, checked first so the user gets instant feedback. */
    private fun validate(state: AuthUiState): AuthFormError? =
        when (state.mode) {
            AuthMode.LOGIN ->
                when {
                    state.login.isBlank() -> AuthFormError.EmptyLogin
                    state.password.isEmpty() -> AuthFormError.EmptyPassword
                    else -> null
                }
            AuthMode.REGISTER ->
                when {
                    !USERNAME_REGEX.matches(state.username.trim()) -> AuthFormError.InvalidUsername
                    !EMAIL_REGEX.matches(state.email.trim()) -> AuthFormError.InvalidEmail
                    state.password.length < MIN_PASSWORD_LENGTH -> AuthFormError.PasswordTooShort
                    // BCrypt on the server only uses the first 72 bytes.
                    state.password.toByteArray().size > MAX_PASSWORD_BYTES ->
                        AuthFormError.PasswordTooLong
                    else -> null
                }
        }

    private companion object {
        val USERNAME_REGEX = Regex("^[A-Za-z0-9._]{3,30}$")
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_BYTES = 72
    }
}

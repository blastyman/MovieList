package com.example.movielist.data.repository

import com.example.movielist.data.model.AuthResponse
import com.example.movielist.data.model.ErrorResponse
import com.example.movielist.data.model.LoginRequest
import com.example.movielist.data.model.RegisterRequest
import com.example.movielist.data.model.UpdateMeRequest
import com.example.movielist.data.preferences.DataStoreTokenStore
import com.example.movielist.data.remote.AuthApi
import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.model.SessionState
import com.example.movielist.domain.repository.AuthRepository
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class BackendAuthRepository(
    private val api: AuthApi,
    private val tokenStore: DataStoreTokenStore,
    private val scope: CoroutineScope,
) : AuthRepository {
    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        scope.launch {
            _session.value =
                if (tokenStore.load() != null) SessionState.LoggedIn else SessionState.LoggedOut
        }
    }

    override suspend fun register(username: String, email: String, password: String) =
        authenticate { api.register(RegisterRequest(username.trim(), email.trim(), password)) }

    override suspend fun login(login: String, password: String) =
        authenticate { api.login(LoginRequest(login.trim(), password)) }

    override suspend fun refreshAccount(): Result<Account> =
        call { api.me().toAccount() }.onSuccess { _account.value = it }

    override suspend fun updateDisplayName(displayName: String): Result<Account> =
        call { api.updateMe(UpdateMeRequest(displayName = displayName.trim())).toAccount() }
            .onSuccess { _account.value = it }

    override suspend fun logout() {
        _account.value = null
        _session.value = SessionState.LoggedOut
        tokenStore.clear()
    }

    /** Called by the HTTP client when the server rejects our token. */
    fun onTokenRejected() {
        scope.launch { logout() }
    }

    private suspend fun authenticate(
        request: suspend () -> AuthResponse
    ): Result<Account> =
        call { request() }
            .map { response ->
                tokenStore.save(response.token)
                response.user.toAccount().also {
                    _account.value = it
                    _session.value = SessionState.LoggedIn
                }
            }

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(AuthException(exception.toAuthError()))
        }

    private fun Exception.toAuthError(): AuthError =
        when (this) {
            is HttpException -> {
                val body = response()?.errorBody()?.string()
                val parsed = runCatching { gson.fromJson(body, ErrorResponse::class.java) }.getOrNull()
                when (parsed?.error) {
                    "INVALID_CREDENTIALS" -> AuthError.InvalidCredentials
                    "USERNAME_TAKEN" -> AuthError.UsernameTaken
                    "EMAIL_TAKEN" -> AuthError.EmailTaken
                    else -> AuthError.Server(parsed?.message ?: "Server error (${code()})")
                }
            }
            is IOException -> AuthError.Network
            else -> AuthError.Server(message ?: javaClass.simpleName)
        }

    private companion object {
        val gson = Gson()
    }
}

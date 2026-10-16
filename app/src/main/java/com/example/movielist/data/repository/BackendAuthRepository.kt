package com.example.movielist.data.repository

import com.example.movielist.data.model.AccountDto
import com.example.movielist.data.model.AuthResponse
import com.example.movielist.data.model.ChangeEmailRequest
import com.example.movielist.data.model.ChangePasswordRequest
import com.example.movielist.data.model.DeleteAccountRequest
import com.example.movielist.data.model.ErrorResponse
import com.example.movielist.data.model.LoginRequest
import com.example.movielist.data.model.RegisterRequest
import com.example.movielist.data.model.UpdateMeRequest
import com.example.movielist.data.model.UpdatePrivacyRequest
import com.example.movielist.data.preferences.DataStoreTokenStore
import com.example.movielist.data.remote.AuthApi
import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.AuthError
import com.example.movielist.domain.model.AuthException
import com.example.movielist.domain.model.Privacy
import com.example.movielist.domain.model.SessionState
import com.example.movielist.domain.repository.AccountSettings
import com.example.movielist.domain.repository.AuthRepository
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response

class BackendAuthRepository(
    private val api: AuthApi,
    private val tokenStore: DataStoreTokenStore,
    private val scope: CoroutineScope,
) : AuthRepository, AccountSettings {
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

    override suspend fun updateProfile(
        displayName: String?,
        username: String?,
        avatarEmoji: String?,
        avatarColor: String?,
    ): Result<Account> =
        update { api.updateMe(UpdateMeRequest(displayName?.trim(), username?.trim(), avatarEmoji, avatarColor)) }

    override suspend fun updatePrivacy(privacy: Privacy): Result<Account> =
        update {
            api.updatePrivacy(
                UpdatePrivacyRequest(privacy.showInMatches, privacy.privateLikes, privacy.friendRequestsFrom, privacy.messagesFrom)
            )
        }

    override suspend fun changeEmail(email: String, password: String): Result<Account> =
        update { api.changeEmail(ChangeEmailRequest(email.trim(), password)) }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        call { api.changePassword(ChangePasswordRequest(currentPassword, newPassword)).orThrow() }

    override suspend fun uploadAvatar(jpeg: ByteArray): Result<Account> =
        update { api.uploadAvatar(jpeg.toRequestBody("image/jpeg".toMediaType())) }

    override suspend fun removeAvatar(): Result<Account> = update { api.removeAvatar() }

    override suspend fun deleteAccount(password: String): Result<Unit> =
        call { api.deleteAccount(DeleteAccountRequest(password)).orThrow() }.onSuccess { logout() }

    /** Runs a call that returns the account, and shows the new one everywhere. */
    private suspend fun update(request: suspend () -> AccountDto): Result<Account> =
        call { request().toAccount() }.onSuccess { _account.value = it }

    private fun Response<Unit>.orThrow() {
        if (!isSuccessful) throw HttpException(this)
    }

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
                    "WRONG_PASSWORD" -> AuthError.WrongPassword
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

package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.SessionState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val session: StateFlow<SessionState>

    /** Null until loaded after login or [refreshAccount]. */
    val account: StateFlow<Account?>

    /** Failures are an [com.example.movielist.domain.model.AuthException]. */
    suspend fun register(username: String, email: String, password: String): Result<Account>

    /** [login] is a username or an email. */
    suspend fun login(login: String, password: String): Result<Account>

    /** Reloads the account for a saved session. A rejected token ends the session. */
    suspend fun refreshAccount(): Result<Account>

    /** Sets the name shown instead of the username; blank removes it. */
    suspend fun updateDisplayName(displayName: String): Result<Account>

    suspend fun logout()
}

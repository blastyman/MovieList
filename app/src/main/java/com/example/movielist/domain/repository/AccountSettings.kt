package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.Privacy
import kotlinx.coroutines.flow.StateFlow

/**
 * Changing the signed-in account: how you look, your handle, privacy, email, password, or
 * deleting it. Failures are an [com.example.movielist.domain.model.AuthException]. Every change
 * updates [account], so screens showing it follow along.
 */
interface AccountSettings {
    val account: StateFlow<Account?>

    /** Null leaves a field as it is; a blank string removes it (the username can only change). */
    suspend fun updateProfile(
        displayName: String? = null,
        username: String? = null,
        avatarEmoji: String? = null,
        avatarColor: String? = null,
    ): Result<Account>

    suspend fun updatePrivacy(privacy: Privacy): Result<Account>

    suspend fun changeEmail(email: String, password: String): Result<Account>

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /** [jpeg] is already cropped square and shrunk. */
    suspend fun uploadAvatar(jpeg: ByteArray): Result<Account>

    suspend fun removeAvatar(): Result<Account>

    /** Deletes everything and ends the session. */
    suspend fun deleteAccount(password: String): Result<Unit>
}

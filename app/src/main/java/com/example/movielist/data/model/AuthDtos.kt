package com.example.movielist.data.model

import com.example.movielist.domain.model.Account
import com.example.movielist.domain.model.FriendRequestsFrom
import com.example.movielist.domain.model.MessagesFrom
import com.example.movielist.domain.model.Privacy

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/auth/AuthDtos.kt

data class RegisterRequest(val username: String, val email: String, val password: String)

data class LoginRequest(val login: String, val password: String)

/** PATCH /me. A blank value removes it (the username can only change); null leaves it as it is. */
data class UpdateMeRequest(
    val displayName: String? = null,
    val username: String? = null,
    val avatarEmoji: String? = null,
    val avatarColor: String? = null,
)

/** PATCH /me/privacy. Null leaves a setting as it is. */
data class UpdatePrivacyRequest(
    val showInMatches: Boolean? = null,
    val privateLikes: Boolean? = null,
    val friendRequestsFrom: FriendRequestsFrom? = null,
    val messagesFrom: MessagesFrom? = null,
)

data class ChangeEmailRequest(val email: String, val password: String)

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

data class DeleteAccountRequest(val password: String)

data class AuthResponse(val token: String, val user: AccountDto)

data class PrivacyDto(
    val showInMatches: Boolean,
    val privateLikes: Boolean,
    val friendRequestsFrom: FriendRequestsFrom,
    val messagesFrom: MessagesFrom,
) {
    fun toPrivacy() = Privacy(showInMatches, privateLikes, friendRequestsFrom, messagesFrom)
}

data class AccountDto(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val avatarColor: String?,
    val privacy: PrivacyDto?,
    val createdAt: String,
) {
    fun toAccount() =
        Account(
            id = id,
            username = username,
            email = email,
            displayName = displayName,
            avatar = avatarOf(avatarUrl, avatarEmoji, avatarColor),
            privacy = privacy?.toPrivacy() ?: Privacy(),
        )
}

/** Every backend error has this shape: `{ "error": "CODE", "message": "…" }`. */
data class ErrorResponse(val error: String?, val message: String?)

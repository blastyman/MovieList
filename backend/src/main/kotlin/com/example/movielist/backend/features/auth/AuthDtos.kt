package com.example.movielist.backend.features.auth

import com.example.movielist.backend.features.users.FriendRequestsFrom
import com.example.movielist.backend.features.users.MessagesFrom
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

/** `login` can be either the username or the email. */
@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
)

/**
 * A missing field leaves it unchanged; a blank string clears it (except the username, which
 * can only be changed). The photo has its own endpoint, `/me/avatar`.
 */
@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    /** The @handle. */
    val username: String? = null,
    val avatarEmoji: String? = null,
    /** `#RRGGBB`. */
    val avatarColor: String? = null,
)

/** A missing field leaves that setting as it is. */
@Serializable
data class UpdatePrivacyRequest(
    val showInMatches: Boolean? = null,
    val privateLikes: Boolean? = null,
    val friendRequestsFrom: FriendRequestsFrom? = null,
    val messagesFrom: MessagesFrom? = null,
)

/** Changing the email needs the password, so a borrowed phone can't take the account over. */
@Serializable
data class ChangeEmailRequest(val email: String, val password: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class AuthResponse(
    val token: String,
    val user: CurrentUserDto,
)

@Serializable
data class PrivacyDto(
    /** Strangers see you in their matches; friends always do. */
    val showInMatches: Boolean,
    /** Only friends see your liked movies. */
    val privateLikes: Boolean,
    val friendRequestsFrom: FriendRequestsFrom,
    val messagesFrom: MessagesFrom,
)

/** The signed-in user's own profile. Includes the email and settings, unlike public profiles. */
@Serializable
data class CurrentUserDto(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val avatarColor: String?,
    val privacy: PrivacyDto,
    val createdAt: String,
)

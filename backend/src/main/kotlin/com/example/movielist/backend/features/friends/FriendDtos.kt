package com.example.movielist.backend.features.friends

import com.example.movielist.backend.features.users.FriendshipState
import com.example.movielist.backend.features.users.UserSummaryDto
import kotlinx.serialization.Serializable

@Serializable
data class FriendRequestBody(val userId: String)

/** The friendship with [userId] after an action, from the caller's side. */
@Serializable
data class FriendshipDto(val userId: String, val friendship: FriendshipState)

@Serializable
data class FriendRequestDto(val user: UserSummaryDto, val requestedAt: String)

@Serializable
data class FriendRequestsDto(
    /** Waiting for the caller to accept or decline, newest first. */
    val incoming: List<FriendRequestDto>,
    /** Sent by the caller, not answered yet, newest first. */
    val outgoing: List<FriendRequestDto>,
)

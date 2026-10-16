package com.example.movielist.data.model

import com.example.movielist.domain.model.FriendRequest
import com.example.movielist.domain.model.FriendRequests
import com.example.movielist.domain.model.FriendshipState
import java.time.Instant

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/friends/FriendDtos.kt

data class FriendRequestBody(val userId: String)

data class FriendshipDto(val userId: String, val friendship: FriendshipState?) {
    fun state() = friendship ?: FriendshipState.NONE
}

data class FriendRequestDto(val user: UserSummaryDto, val requestedAt: String) {
    fun toFriendRequest() = FriendRequest(user.toUserSummary(), Instant.parse(requestedAt))
}

data class FriendRequestsDto(val incoming: List<FriendRequestDto>?, val outgoing: List<FriendRequestDto>?) {
    fun toFriendRequests() =
        FriendRequests(
            incoming = incoming.orEmpty().map { it.toFriendRequest() },
            outgoing = outgoing.orEmpty().map { it.toFriendRequest() },
        )
}

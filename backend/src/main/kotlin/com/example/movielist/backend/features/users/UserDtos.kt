package com.example.movielist.backend.features.users

import com.example.movielist.backend.features.movies.MovieSummaryDto
import kotlinx.serialization.Serializable

/** How the viewer relates to another user. Phase 8 adds the endpoints that change it. */
@Serializable
enum class FriendshipState {
    NONE,
    FRIENDS,
    /** The viewer sent a request that hasn't been answered. */
    REQUEST_SENT,
    /** The other user sent the viewer a request. */
    REQUEST_RECEIVED,
}

/** A user in a list (search results, later friends and group members). */
@Serializable
data class UserSummaryDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val friendship: FriendshipState,
)

@Serializable
data class UserSearchPageDto(
    val page: Int,
    val results: List<UserSummaryDto>,
    val hasMore: Boolean,
)

@Serializable
data class ProfileStatsDto(
    val likes: Int,
    val ratings: Int,
    val comments: Int,
    val friends: Int,
)

/** Someone's public profile. Never contains their email. */
@Serializable
data class UserProfileDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val memberSince: String,
    val isMe: Boolean,
    val friendship: FriendshipState,
    val stats: ProfileStatsDto,
    /** Their newest likes, newest first (at most 10). */
    val recentLikes: List<MovieSummaryDto>,
)

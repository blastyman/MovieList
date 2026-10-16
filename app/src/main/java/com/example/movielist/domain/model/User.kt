package com.example.movielist.domain.model

import java.time.Instant

/** How the signed-in user relates to someone else. */
enum class FriendshipState {
    NONE,
    FRIENDS,
    REQUEST_SENT,
    REQUEST_RECEIVED,
}

/** Someone in a list, e.g. people search results. */
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String?,
    val friendship: FriendshipState,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class ProfileStats(val likes: Int, val ratings: Int, val comments: Int, val friends: Int)

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String?,
    val memberSince: Instant,
    val isMe: Boolean,
    val friendship: FriendshipState,
    val stats: ProfileStats,
    /** Newest first. */
    val recentLikes: List<Movie>,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** One page of search results; [hasMore] says whether the next page has anything. */
data class SearchPage<T>(val items: List<T>, val hasMore: Boolean)

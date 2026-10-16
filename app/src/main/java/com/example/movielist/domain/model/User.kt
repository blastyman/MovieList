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
    val avatar: Avatar = Avatar.NONE,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class ProfileStats(
    /** Null when their likes are private to you. */
    val likes: Int?, val ratings: Int, val comments: Int, val friends: Int)

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
    val avatar: Avatar = Avatar.NONE,
    /** Their likes are private and you aren't friends. */
    val likesHidden: Boolean = false,
    /** Their privacy settings let you send a friend request (or you can answer theirs). */
    val canAddFriend: Boolean = true,
    /** You can chat one-to-one: friends, and neither has messages turned off. */
    val canMessage: Boolean = false,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** One page of search results; [hasMore] says whether the next page has anything. */
data class SearchPage<T>(val items: List<T>, val hasMore: Boolean)

data class FriendRequest(val user: UserSummary, val requestedAt: Instant)

data class FriendRequests(
    /** Waiting for the signed-in user to answer, newest first. */
    val incoming: List<FriendRequest>,
    /** Sent by the signed-in user, newest first. */
    val outgoing: List<FriendRequest>,
)

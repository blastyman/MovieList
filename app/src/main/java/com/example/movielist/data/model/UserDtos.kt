package com.example.movielist.data.model

import com.example.movielist.domain.model.FriendshipState
import com.example.movielist.domain.model.ProfileStats
import com.example.movielist.domain.model.SearchPage
import com.example.movielist.domain.model.UserProfile
import com.example.movielist.domain.model.UserSummary
import java.time.Instant

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/users/UserDtos.kt

data class UserSummaryDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val friendship: FriendshipState?,
) {
    fun toUserSummary() = UserSummary(id, username, displayName, friendship ?: FriendshipState.NONE)
}

data class UserSearchPageDto(val page: Int, val results: List<UserSummaryDto>?, val hasMore: Boolean) {
    fun toSearchPage() = SearchPage(results.orEmpty().map { it.toUserSummary() }, hasMore)
}

data class ProfileStatsDto(val likes: Int, val ratings: Int, val comments: Int, val friends: Int)

data class UserProfileDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val memberSince: String,
    val isMe: Boolean,
    val friendship: FriendshipState?,
    val stats: ProfileStatsDto,
    val recentLikes: List<MovieDto>?,
) {
    fun toUserProfile() =
        UserProfile(
            id = id,
            username = username,
            displayName = displayName,
            memberSince = Instant.parse(memberSince),
            isMe = isMe,
            friendship = friendship ?: FriendshipState.NONE,
            stats = ProfileStats(stats.likes, stats.ratings, stats.comments, stats.friends),
            recentLikes = recentLikes.orEmpty().map { it.toMovie() },
        )
}

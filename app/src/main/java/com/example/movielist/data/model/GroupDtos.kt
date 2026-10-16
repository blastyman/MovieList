package com.example.movielist.data.model

import com.example.movielist.domain.model.Group
import com.example.movielist.domain.model.GroupBrief
import com.example.movielist.domain.model.GroupMatch
import java.time.Instant

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/groups/GroupDtos.kt

data class CreateGroupRequest(val name: String, val memberIds: List<String>)

/** Null leaves a field as it is; a blank emoji or colour removes it. */
data class UpdateGroupRequest(val name: String? = null, val avatarEmoji: String? = null, val avatarColor: String? = null)

data class AddMemberRequest(val userId: String)

data class GroupDto(
    val id: String,
    val name: String,
    val ownerId: String,
    val conversationId: String,
    val members: List<UserSummaryDto>?,
    val maxMembers: Int,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val avatarColor: String?,
) {
    fun toGroup() =
        Group(
            id = id,
            name = name,
            ownerId = ownerId,
            conversationId = conversationId,
            members = members.orEmpty().map { it.toUserSummary() },
            maxMembers = maxMembers,
            avatar = avatarOf(avatarUrl, avatarEmoji, avatarColor),
        )
}

data class GroupMatchDto(val movie: MovieDto, val matchedAt: String) {
    fun toGroupMatch() = GroupMatch(movie.toMovie(), Instant.parse(matchedAt))
}

data class GroupBriefDto(val id: String, val name: String) {
    fun toGroupBrief() = GroupBrief(id, name)
}

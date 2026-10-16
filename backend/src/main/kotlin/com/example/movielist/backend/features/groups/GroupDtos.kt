package com.example.movielist.backend.features.groups

import com.example.movielist.backend.features.movies.MovieSummaryDto
import com.example.movielist.backend.features.users.UserSummaryDto
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(val name: String, val memberIds: List<String> = emptyList())

/** A missing field stays as it is; a blank emoji or colour removes it. Owner only. */
@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val avatarEmoji: String? = null,
    /** `#RRGGBB`. */
    val avatarColor: String? = null,
)

@Serializable
data class AddMemberRequest(val userId: String)

/** A group with all its members (at most [GroupService.MAX_MEMBERS], so always the full list). */
@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    /** The group's icon: a photo (`/avatars/…`), else an emoji on a colour. */
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val avatarColor: String?,
    val ownerId: String,
    /** The group's chat. */
    val conversationId: String,
    /** The owner first, then by when they joined. */
    val members: List<UserSummaryDto>,
    val maxMembers: Int,
    val createdAt: String,
)

/** A movie every member of the group likes. */
@Serializable
data class GroupMatchDto(
    val movie: MovieSummaryDto,
    /** When the last member liked it, which is when it became a match. */
    val matchedAt: String,
)

/** Just enough to name a group, e.g. "everyone in Movie night likes it". */
@Serializable
data class GroupBriefDto(val id: String, val name: String)

package com.example.movielist.data.model

import com.example.movielist.domain.model.Group
import com.example.movielist.domain.model.GroupBrief
import com.example.movielist.domain.model.GroupMatch
import java.time.Instant

// Mirror of backend/src/main/kotlin/com/example/movielist/backend/features/groups/GroupDtos.kt

data class CreateGroupRequest(val name: String, val memberIds: List<String>)

data class RenameGroupRequest(val name: String)

data class AddMemberRequest(val userId: String)

data class GroupDto(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<UserSummaryDto>?,
    val maxMembers: Int,
) {
    fun toGroup() = Group(id, name, ownerId, members.orEmpty().map { it.toUserSummary() }, maxMembers)
}

data class GroupMatchDto(val movie: MovieDto, val matchedAt: String) {
    fun toGroupMatch() = GroupMatch(movie.toMovie(), Instant.parse(matchedAt))
}

data class GroupBriefDto(val id: String, val name: String) {
    fun toGroupBrief() = GroupBrief(id, name)
}

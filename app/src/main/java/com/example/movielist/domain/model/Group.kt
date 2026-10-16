package com.example.movielist.domain.model

import java.time.Instant

/** A friend group: at most [maxMembers] people, owner included, listed owner first. */
data class Group(
    val id: String,
    val name: String,
    val ownerId: String,
    /** The group's chat. */
    val conversationId: String,
    val members: List<UserSummary>,
    val maxMembers: Int,
) {
    val isFull: Boolean
        get() = members.size >= maxMembers

    fun isOwner(userId: String?) = userId == ownerId
}

/** A movie everyone in a group likes. */
data class GroupMatch(val movie: Movie, val matchedAt: Instant)

/** Names a group, e.g. in "everyone in Movie night likes it". */
data class GroupBrief(val id: String, val name: String)

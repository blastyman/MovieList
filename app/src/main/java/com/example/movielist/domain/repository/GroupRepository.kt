package com.example.movielist.domain.repository

import com.example.movielist.domain.model.Group
import com.example.movielist.domain.model.GroupMatch

/** Friend groups the signed-in user is in. Only their friends can be added. */
interface GroupRepository {
    /** Newest first. */
    suspend fun groups(): List<Group>

    suspend fun group(groupId: String): Group

    suspend fun create(name: String, memberIds: List<String>): Group

    /** Owner only. */
    suspend fun rename(groupId: String, name: String): Group

    suspend fun addMember(groupId: String, userId: String): Group

    /** The owner removes someone; returns the group without them. */
    suspend fun removeMember(groupId: String, userId: String): Group

    /** The signed-in user leaves the group. */
    suspend fun leave(groupId: String, myId: String)

    /** Movies every member likes, newest match first. */
    suspend fun matches(groupId: String): List<GroupMatch>
}

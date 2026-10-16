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

    /** Owner only. Null leaves a part as it is; a blank string removes it. */
    suspend fun updateIcon(groupId: String, emoji: String? = null, color: String? = null): Group

    /** Owner only. [jpeg] is already cropped square and shrunk. */
    suspend fun uploadIcon(groupId: String, jpeg: ByteArray): Group

    suspend fun removeIcon(groupId: String): Group

    suspend fun addMember(groupId: String, userId: String): Group

    /** The owner removes someone; returns the group without them. */
    suspend fun removeMember(groupId: String, userId: String): Group

    /** The signed-in user leaves the group. */
    suspend fun leave(groupId: String, myId: String)

    /** Owner only: deletes the group for everyone. */
    suspend fun disband(groupId: String)

    /** Movies every member likes, newest match first. */
    suspend fun matches(groupId: String): List<GroupMatch>
}

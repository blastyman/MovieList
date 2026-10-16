package com.example.movielist.data.repository

import com.example.movielist.data.model.AddMemberRequest
import com.example.movielist.data.model.CreateGroupRequest
import com.example.movielist.data.model.RenameGroupRequest
import com.example.movielist.data.remote.GroupApi
import com.example.movielist.domain.model.Group
import com.example.movielist.domain.model.GroupMatch
import com.example.movielist.domain.repository.GroupRepository
import retrofit2.HttpException

class BackendGroupRepository(private val api: GroupApi) : GroupRepository {
    override suspend fun groups(): List<Group> = api.groups().map { it.toGroup() }

    override suspend fun group(groupId: String): Group = api.group(groupId).toGroup()

    override suspend fun create(name: String, memberIds: List<String>): Group =
        api.create(CreateGroupRequest(name, memberIds)).toGroup()

    override suspend fun rename(groupId: String, name: String): Group =
        api.rename(groupId, RenameGroupRequest(name)).toGroup()

    override suspend fun addMember(groupId: String, userId: String): Group =
        api.addMember(groupId, AddMemberRequest(userId)).toGroup()

    override suspend fun removeMember(groupId: String, userId: String): Group {
        val response = api.removeMember(groupId, userId)
        if (!response.isSuccessful) throw HttpException(response)
        return checkNotNull(response.body()) { "Removing a member should return the group" }.toGroup()
    }

    override suspend fun leave(groupId: String, myId: String) {
        val response = api.removeMember(groupId, myId)
        if (!response.isSuccessful) throw HttpException(response)
    }

    override suspend fun disband(groupId: String) {
        val response = api.disband(groupId)
        if (!response.isSuccessful) throw HttpException(response)
    }

    override suspend fun matches(groupId: String): List<GroupMatch> = api.matches(groupId).map { it.toGroupMatch() }
}

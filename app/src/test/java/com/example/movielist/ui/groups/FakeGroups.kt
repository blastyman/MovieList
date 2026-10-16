package com.example.movielist.ui.groups

import com.example.movielist.domain.model.Group
import com.example.movielist.domain.model.GroupMatch
import com.example.movielist.domain.model.UserSummary
import com.example.movielist.domain.repository.GroupRepository
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Groups kept in memory, behaving like the server: owner first, at most 10, 409 when full. */
class FakeGroups(private val me: UserSummary, private val people: (String) -> UserSummary) : GroupRepository {
    val groups = linkedMapOf<String, Group>()
    val matches = mutableMapOf<String, List<GroupMatch>>()
    var failWith: IOException? = null
    private var nextId = 1

    override suspend fun groups() = groups.values.reversed()

    override suspend fun group(groupId: String) = groups[groupId] ?: throw httpError(404)

    override suspend fun create(name: String, memberIds: List<String>): Group {
        failWith?.let { throw it }
        if (memberIds.size + 1 > MAX_GROUP_MEMBERS) throw httpError(409)
        val group = Group("g${nextId++}", name, me.id, listOf(me) + memberIds.map(people), MAX_GROUP_MEMBERS)
        groups[group.id] = group
        return group
    }

    override suspend fun rename(groupId: String, name: String) = update(groupId) { it.copy(name = name) }

    override suspend fun addMember(groupId: String, userId: String) = update(groupId) {
        if (it.members.any { member -> member.id == userId }) it
        else if (it.isFull) throw httpError(409)
        else it.copy(members = it.members + people(userId))
    }

    override suspend fun removeMember(groupId: String, userId: String) =
        update(groupId) { it.copy(members = it.members.filterNot { member -> member.id == userId }) }

    override suspend fun leave(groupId: String, myId: String) {
        failWith?.let { throw it }
        groups.remove(groupId)
    }

    override suspend fun matches(groupId: String) = matches[groupId].orEmpty()

    private fun update(groupId: String, change: (Group) -> Group): Group {
        failWith?.let { throw it }
        val updated = change(groups[groupId] ?: throw httpError(404))
        groups[groupId] = updated
        return updated
    }

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))
}

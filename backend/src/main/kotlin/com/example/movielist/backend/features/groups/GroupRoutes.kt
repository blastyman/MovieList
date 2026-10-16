package com.example.movielist.backend.features.groups

import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.ValidationException
import com.example.movielist.backend.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.groupRoutes(groupService: GroupService) {
    authenticate(AUTH_JWT) {
        route("/groups") {
            get { call.respond(groupService.groups(call.userId)) }
            post { call.respond(HttpStatusCode.Created, groupService.create(call.userId, call.receive())) }
            route("/{groupId}") {
                get { call.respond(groupService.group(call.userId, call.groupId())) }
                patch { call.respond(groupService.rename(call.userId, call.groupId(), call.receive())) }
                get("/matches") { call.respond(groupService.matches(call.userId, call.groupId())) }
                post("/members") {
                    val body = call.receive<AddMemberRequest>()
                    call.respond(groupService.addMember(call.userId, call.groupId(), parseUuid(body.userId, "User id")))
                }
                // Removing yourself means leaving: 204, since you can't see the group any more.
                delete("/members/{userId}") {
                    val userId = parseUuid(call.parameters["userId"], "User id")
                    val group = groupService.removeMember(call.userId, call.groupId(), userId)
                    if (group == null) call.respond(HttpStatusCode.NoContent) else call.respond(group)
                }
            }
        }
    }
}

private fun RoutingCall.groupId(): UUID = parseUuid(parameters["groupId"], "Group id")

private fun parseUuid(raw: String?, what: String): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ValidationException("$what must be a UUID")

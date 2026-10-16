package com.example.movielist.backend.features.friends

import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.ValidationException
import com.example.movielist.backend.plugins.userId
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

/** Every change answers with the new [FriendshipDto], so the app can update without reloading. */
fun Route.friendRoutes(friendService: FriendService) {
    authenticate(AUTH_JWT) {
        route("/friends") {
            get { call.respond(friendService.friends(call.userId)) }
            delete("/{userId}") { call.respond(friendService.remove(call.userId, call.otherUserId())) }

            route("/requests") {
                get { call.respond(friendService.requests(call.userId)) }
                post {
                    val body = call.receive<FriendRequestBody>()
                    call.respond(friendService.sendRequest(call.userId, parseUserId(body.userId)))
                }
                post("/{userId}/accept") { call.respond(friendService.accept(call.userId, call.otherUserId())) }
                post("/{userId}/decline") { call.respond(friendService.decline(call.userId, call.otherUserId())) }
                delete("/{userId}") { call.respond(friendService.cancel(call.userId, call.otherUserId())) }
            }
        }
    }
}

private fun RoutingCall.otherUserId(): UUID = parseUserId(parameters["userId"])

private fun parseUserId(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ValidationException("User id must be a UUID")

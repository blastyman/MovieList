package com.example.movielist.backend.features.users

import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.ValidationException
import com.example.movielist.backend.plugins.userId
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID

fun Route.userRoutes(userService: UserService) {
    authenticate(AUTH_JWT) {
        route("/users") {
            get("/search") {
                val params = call.request.queryParameters
                val page = params["page"]?.let { it.toIntOrNull() ?: throw ValidationException("page must be a number") }
                call.respond(userService.search(call.userId, params["q"], page))
            }
            get("/{userId}") {
                val userId = call.parameters["userId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw ValidationException("User id must be a UUID")
                call.respond(userService.profile(call.userId, userId))
            }
        }
    }
}

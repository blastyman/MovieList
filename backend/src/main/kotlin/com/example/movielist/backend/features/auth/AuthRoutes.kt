package com.example.movielist.backend.features.auth

import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/register") {
            val response = authService.register(call.receive<RegisterRequest>())
            call.respond(HttpStatusCode.Created, response)
        }
        post("/login") {
            call.respond(authService.login(call.receive<LoginRequest>()))
        }
    }

    authenticate(AUTH_JWT) {
        route("/me") {
            get {
                call.respond(authService.me(call.userId))
            }
            patch {
                call.respond(authService.updateMe(call.userId, call.receive<UpdateMeRequest>()))
            }
        }
    }
}

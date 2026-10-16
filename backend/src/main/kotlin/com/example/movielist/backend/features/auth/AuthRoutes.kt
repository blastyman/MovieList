package com.example.movielist.backend.features.auth

import com.example.movielist.backend.features.users.AvatarStore
import com.example.movielist.backend.features.users.receiveAvatar
import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.userId
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.authRoutes(authService: AuthService, avatars: AvatarStore) {
    route("/auth") {
        post("/register") {
            val response = authService.register(call.receive<RegisterRequest>())
            call.respond(HttpStatusCode.Created, response)
        }
        post("/login") {
            call.respond(authService.login(call.receive<LoginRequest>()))
        }
    }

    // Profile pictures are public: anyone who sees a name sees its picture. Each upload has a
    // new random file name, so a picture never changes and can be cached for good.
    get("/avatars/{name}") {
        val file = call.parameters["name"]?.let(avatars::file) ?: throw NotFoundException("No such picture")
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondFile(file)
    }

    authenticate(AUTH_JWT) {
        route("/me") {
            get { call.respond(authService.me(call.userId)) }
            patch { call.respond(authService.updateMe(call.userId, call.receive<UpdateMeRequest>())) }
            delete {
                authService.deleteAccount(call.userId, call.receive<DeleteAccountRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
            patch("/privacy") { call.respond(authService.updatePrivacy(call.userId, call.receive<UpdatePrivacyRequest>())) }
            put("/email") { call.respond(authService.changeEmail(call.userId, call.receive<ChangeEmailRequest>())) }
            put("/password") {
                authService.changePassword(call.userId, call.receive<ChangePasswordRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
            post("/avatar") { call.respond(authService.setAvatar(call.userId, call.receiveAvatar())) }
            delete("/avatar") { call.respond(authService.removeAvatar(call.userId)) }
        }
    }
}

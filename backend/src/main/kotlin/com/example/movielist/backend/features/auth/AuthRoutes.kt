package com.example.movielist.backend.features.auth

import com.example.movielist.backend.features.users.AvatarStore
import com.example.movielist.backend.features.users.AvatarTooLargeException
import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.NotFoundException
import com.example.movielist.backend.plugins.userId
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
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
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

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
            // The picture itself is the body (image/jpeg, png or webp), no multipart needed.
            post("/avatar") {
                val declared = call.request.contentLength()
                if (declared != null && declared > AvatarStore.MAX_BYTES) throw AvatarTooLargeException()
                // Read at most one byte past the limit, so a body without a length can't run away.
                val bytes = call.receiveChannel().readRemaining(AvatarStore.MAX_BYTES + 1L).readByteArray()
                call.respond(authService.setAvatar(call.userId, bytes))
            }
            delete("/avatar") { call.respond(authService.removeAvatar(call.userId)) }
        }
    }
}

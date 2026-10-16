package com.example.movielist.backend.plugins

import com.example.movielist.backend.features.auth.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import java.util.UUID

const val AUTH_JWT = "auth-jwt"

fun Application.configureSecurity(jwtService: JwtService) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = JwtService.REALM
            verifier(jwtService.verifier)
            validate { credential ->
                val subject = credential.payload.subject
                if (subject != null && runCatching { UUID.fromString(subject) }.isSuccess) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("UNAUTHORIZED", "Missing, invalid or expired token"),
                )
            }
        }
    }
}

/** Id of the signed-in user. Only call inside `authenticate(AUTH_JWT) { … }` routes. */
val ApplicationCall.userId: UUID
    get() = UUID.fromString(requireNotNull(principal<JWTPrincipal>()).payload.subject)

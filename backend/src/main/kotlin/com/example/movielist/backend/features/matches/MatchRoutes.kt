package com.example.movielist.backend.features.matches

import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.userId
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.matchRoutes(matchService: MatchService) {
    authenticate(AUTH_JWT) {
        get("/matches") { call.respond(matchService.matches(call.userId)) }
    }
}

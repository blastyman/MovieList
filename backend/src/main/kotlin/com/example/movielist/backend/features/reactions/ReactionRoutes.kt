package com.example.movielist.backend.features.reactions

import com.example.movielist.backend.db.Reaction
import com.example.movielist.backend.features.movies.movieIdParameter
import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.ValidationException
import com.example.movielist.backend.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.reactionRoutes(reactionService: ReactionService) {
    authenticate(AUTH_JWT) {
        put("/movies/{id}/reaction") {
            val request = call.receive<SetReactionRequest>()
            call.respond(reactionService.setReaction(call.userId, call.movieIdParameter(), request.reaction))
        }
        delete("/movies/{id}/reaction") {
            reactionService.removeReaction(call.userId, call.movieIdParameter())
            call.respond(HttpStatusCode.NoContent)
        }
        get("/me/reactions/ids") {
            call.respond(reactionService.ids(call.userId))
        }
        get("/me/reactions") {
            val params = call.request.queryParameters
            val type = params["type"]?.let { raw ->
                Reaction.entries.firstOrNull { it.name == raw.uppercase() }
                    ?: throw ValidationException("type must be LIKE or DISLIKE")
            }
            val limit = params["limit"]?.let { it.toIntOrNull() ?: throw ValidationException("limit must be a number") }
            call.respond(reactionService.page(call.userId, type, limit, params["before"]))
        }
    }
}

package com.example.movielist.backend.features.comments

import com.example.movielist.backend.features.movies.movieIdParameter
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
import java.util.UUID

fun Route.commentRoutes(commentService: CommentService) {
    authenticate(AUTH_JWT) {
        get("/movies/{id}/comments") {
            val params = call.request.queryParameters
            call.respond(commentService.page(call.userId, call.movieIdParameter(), call.limitQuery(), params["before"]))
        }
        post("/movies/{id}/comments") {
            val comment = commentService.create(call.userId, call.movieIdParameter(), call.receive())
            call.respond(HttpStatusCode.Created, comment)
        }
        get("/comments/{commentId}/replies") {
            val params = call.request.queryParameters
            call.respond(commentService.replies(call.userId, call.commentIdParameter(), call.limitQuery(), params["after"]))
        }
        post("/comments/{commentId}/replies") {
            val reply = commentService.reply(call.userId, call.commentIdParameter(), call.receive())
            call.respond(HttpStatusCode.Created, reply)
        }
        patch("/comments/{commentId}") {
            call.respond(commentService.edit(call.userId, call.commentIdParameter(), call.receive()))
        }
        delete("/comments/{commentId}") {
            commentService.delete(call.userId, call.commentIdParameter())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun RoutingCall.commentIdParameter(): UUID =
    parameters["commentId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw ValidationException("Comment id must be a UUID")

private fun RoutingCall.limitQuery(): Int? =
    request.queryParameters["limit"]?.let { it.toIntOrNull() ?: throw ValidationException("limit must be a number") }

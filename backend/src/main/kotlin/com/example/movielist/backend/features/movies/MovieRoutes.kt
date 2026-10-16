package com.example.movielist.backend.features.movies

import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.ValidationException
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.movieRoutes(movieService: MovieService) {
    authenticate(AUTH_JWT) {
        route("/movies") {
            get("/discover") {
                call.respond(
                    movieService.discover(
                        page = call.intQuery("page"),
                        genreId = call.intQuery("genre"),
                    )
                )
            }
            get("/search") {
                call.respond(
                    movieService.search(
                        query = call.request.queryParameters["q"],
                        page = call.intQuery("page"),
                    )
                )
            }
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: throw ValidationException("Movie id must be a number")
                call.respond(movieService.details(id))
            }
        }
    }
}

private fun RoutingCall.intQuery(name: String): Int? {
    val raw = request.queryParameters[name] ?: return null
    return raw.toIntOrNull() ?: throw ValidationException("$name must be a number")
}

package com.example.movielist.backend.features.movies

import com.example.movielist.backend.features.ratings.RatingService
import com.example.movielist.backend.features.reactions.ReactionService
import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.ValidationException
import com.example.movielist.backend.plugins.userId
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.movieRoutes(
    movieService: MovieService,
    ratingService: RatingService,
    reactionService: ReactionService,
) {
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
                val id = call.movieIdParameter()
                // TMDB data is cached and shared; the social part is per user and always fresh.
                val details = movieService.details(id)
                val stats = ratingService.stats(call.userId, id)
                val myReaction = reactionService.myReaction(call.userId, id)
                call.respond(details.withSocial(stats, myReaction))
            }
        }
    }
}

/** The `{id}` path parameter as a TMDB movie id. */
fun RoutingCall.movieIdParameter(): Int {
    val id = parameters["id"]?.toIntOrNull() ?: throw ValidationException("Movie id must be a number")
    if (id <= 0) throw ValidationException("Movie id must be positive")
    return id
}

private fun RoutingCall.intQuery(name: String): Int? {
    val raw = request.queryParameters[name] ?: return null
    return raw.toIntOrNull() ?: throw ValidationException("$name must be a number")
}

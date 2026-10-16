package com.example.movielist.backend.features.ratings

import com.example.movielist.backend.features.movies.movieIdParameter
import com.example.movielist.backend.plugins.AUTH_JWT
import com.example.movielist.backend.plugins.userId
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.put

/** Both return the updated stats, so the app can refresh the average in one round trip. */
fun Route.ratingRoutes(ratingService: RatingService) {
    authenticate(AUTH_JWT) {
        put("/movies/{id}/rating") {
            val request = call.receive<SetRatingRequest>()
            call.respond(ratingService.setRating(call.userId, call.movieIdParameter(), request.score))
        }
        delete("/movies/{id}/rating") {
            call.respond(ratingService.removeRating(call.userId, call.movieIdParameter()))
        }
    }
}

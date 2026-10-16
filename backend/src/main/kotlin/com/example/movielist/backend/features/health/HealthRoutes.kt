package com.example.movielist.backend.features.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class HealthResponse(val status: String, val database: String)

/** Used by uptime monitors and deploy checks. Returns 503 if the database is unreachable. */
fun Route.healthRoutes(database: Database) {
    get("/health") {
        val dbUp = runCatching {
            transaction(database) { exec("SELECT 1") { it.next() } }
        }.isSuccess

        if (dbUp) {
            call.respond(HealthResponse(status = "ok", database = "up"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse(status = "degraded", database = "down"))
        }
    }
}

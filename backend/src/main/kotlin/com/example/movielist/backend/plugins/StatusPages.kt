package com.example.movielist.backend.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/** The single error shape every endpoint returns: `{ "error": "CODE", "message": "…" }`. */
@Serializable
data class ErrorResponse(val error: String, val message: String)

/** Throw from any route or service to send a specific error to the client. */
open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(message: String = "Resource not found") :
    ApiException(HttpStatusCode.NotFound, "NOT_FOUND", message)

class ValidationException(message: String) :
    ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", message)

class ForbiddenException(message: String = "You are not allowed to do this") :
    ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", message)

class ConflictException(code: String, message: String) :
    ApiException(HttpStatusCode.Conflict, code, message)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.code, cause.message))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("BAD_REQUEST", cause.message ?: "Malformed request"),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error on ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Something went wrong"),
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("NOT_FOUND", "No route for ${call.request.local.uri}"))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(status, ErrorResponse("METHOD_NOT_ALLOWED", "Method not allowed"))
        }
    }
}

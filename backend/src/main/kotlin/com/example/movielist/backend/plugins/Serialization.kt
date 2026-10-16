package com.example.movielist.backend.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(AppJson)
    }
}

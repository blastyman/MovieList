package com.example.movielist.backend

import com.example.movielist.backend.config.AppConfig
import com.example.movielist.backend.db.DatabaseFactory
import com.example.movielist.backend.features.auth.AuthService
import com.example.movielist.backend.features.auth.JwtService
import com.example.movielist.backend.features.auth.PasswordHasher
import com.example.movielist.backend.features.auth.authRoutes
import com.example.movielist.backend.features.health.healthRoutes
import com.example.movielist.backend.features.users.UserRepository
import com.example.movielist.backend.plugins.configureMonitoring
import com.example.movielist.backend.plugins.configureSecurity
import com.example.movielist.backend.plugins.configureSerialization
import com.example.movielist.backend.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

fun main() {
    val config = AppConfig.fromEnv()
    val dataSource = DatabaseFactory.createDataSource(config.database)
    DatabaseFactory.migrate(dataSource)

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config, dataSource)
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
    }.start(wait = true)
}

/**
 * Wires every plugin and route. Takes its dependencies as parameters so tests can pass
 * their own config and an embedded database.
 */
fun Application.module(config: AppConfig, dataSource: DataSource) {
    val database = Database.connect(dataSource)

    val jwtService = JwtService(config.jwtSecret)
    val authService = AuthService(UserRepository(database), PasswordHasher(), jwtService)

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureSecurity(jwtService)

    routing {
        healthRoutes(database)
        authRoutes(authService)
    }
}

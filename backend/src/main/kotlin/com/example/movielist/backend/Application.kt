package com.example.movielist.backend

import com.example.movielist.backend.config.AppConfig
import com.example.movielist.backend.db.DatabaseFactory
import com.example.movielist.backend.features.auth.AuthService
import com.example.movielist.backend.features.auth.JwtService
import com.example.movielist.backend.features.auth.PasswordHasher
import com.example.movielist.backend.features.auth.authRoutes
import com.example.movielist.backend.features.health.healthRoutes
import com.example.movielist.backend.features.movies.MovieService
import com.example.movielist.backend.features.movies.movieRoutes
import com.example.movielist.backend.features.users.UserRepository
import com.example.movielist.backend.plugins.configureMonitoring
import com.example.movielist.backend.plugins.configureSecurity
import com.example.movielist.backend.plugins.configureSerialization
import com.example.movielist.backend.plugins.configureStatusPages
import com.example.movielist.backend.tmdb.TmdbClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
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
 * their own config, an embedded database and a fake TMDB.
 */
fun Application.module(
    config: AppConfig,
    dataSource: DataSource,
    tmdbEngine: HttpClientEngine = CIO.create(),
) {
    val database = Database.connect(dataSource)

    val jwtService = JwtService(config.jwtSecret)
    val authService = AuthService(UserRepository(database), PasswordHasher(), jwtService)

    if (config.tmdbToken.isBlank()) log.warn("TMDB_TOKEN is not set: /movies endpoints will fail")
    val tmdbClient = TmdbClient(tmdbEngine, config.tmdbToken)
    monitor.subscribe(ApplicationStopped) { tmdbClient.close() }
    val movieService = MovieService(tmdbClient, database)

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureSecurity(jwtService)

    routing {
        healthRoutes(database)
        authRoutes(authService)
        movieRoutes(movieService)
    }
}

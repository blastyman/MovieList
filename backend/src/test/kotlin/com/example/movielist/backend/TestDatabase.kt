package com.example.movielist.backend

import com.example.movielist.backend.config.AppConfig
import com.example.movielist.backend.config.DatabaseConfig
import com.example.movielist.backend.db.DatabaseFactory
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource

/**
 * One real PostgreSQL 16 instance for the whole test run (no Docker needed),
 * migrated with the same Flyway scripts as production.
 */
object TestDatabase {
    private val postgres: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().start().also { pg ->
            Runtime.getRuntime().addShutdownHook(Thread { pg.close() })
        }
    }

    val dataSource: DataSource by lazy {
        postgres.postgresDatabase.also { DatabaseFactory.migrate(it) }
    }

    val config = AppConfig(
        port = 0,
        isProduction = false,
        database = DatabaseConfig(url = "embedded", user = "postgres", password = ""),
        jwtSecret = "test-secret",
        tmdbToken = "",
        uploadDir = kotlin.io.path.createTempDirectory("movielist-avatars").toString(),
    )

    /** Runs a `SELECT count(*) …` straight on the database, for checking rows the API doesn't show. */
    fun count(sql: String): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) }
            }
        }
}

package com.example.movielist.backend.config

/**
 * All runtime configuration comes from environment variables, so the same Docker image
 * runs locally and in production. Development defaults match docker-compose.yml.
 */
data class AppConfig(
    val port: Int,
    val isProduction: Boolean,
    val database: DatabaseConfig,
    val jwtSecret: String,
    val tmdbToken: String,
    /** Where profile pictures are stored. */
    val uploadDir: String = "uploads",
) {
    companion object {
        private const val DEV_JWT_SECRET = "dev-only-jwt-secret-change-me"

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val isProduction = env["APP_ENV"].equals("production", ignoreCase = true)

            fun required(name: String, devDefault: String): String =
                env[name]?.takeIf { it.isNotBlank() }
                    ?: if (isProduction) error("Missing required environment variable $name") else devDefault

            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                isProduction = isProduction,
                database = DatabaseConfig(
                    url = required("DB_URL", "jdbc:postgresql://localhost:5432/movielist"),
                    user = required("DB_USER", "movielist"),
                    password = required("DB_PASSWORD", "movielist"),
                ),
                jwtSecret = required("JWT_SECRET", DEV_JWT_SECRET),
                tmdbToken = required("TMDB_TOKEN", ""),
                uploadDir = env["UPLOAD_DIR"]?.takeIf { it.isNotBlank() } ?: "uploads",
            )
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
)

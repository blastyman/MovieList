# MovieList backend

Ktor 3 + PostgreSQL server for the MovieList Android app.
Design: [../docs/IMPLEMENTATION_PLAN.md](../docs/IMPLEMENTATION_PLAN.md) · Hosting: [../docs/SERVER_SETUP.md](../docs/SERVER_SETUP.md)

This is a standalone Gradle project, separate from the Android build. Open the `backend/`
folder on its own in IntelliJ IDEA (Community is fine), or use the commands below.

## Run the tests (no Docker needed)

```bash
./gradlew test
```

The tests start a real embedded PostgreSQL 16 and run the Flyway migrations on it.

## Run the server locally

1. Start Postgres. Either:
   - **Docker Desktop:** `docker compose up -d` in this folder, or
   - a locally installed PostgreSQL 16 with a `movielist` database/user/password.
2. Optional: `cp .env.example .env` and fill in `TMDB_TOKEN` (the values are read from real
   environment variables; IntelliJ's run configuration or the EnvFile plugin can load `.env`).
3. Start the server:
   ```bash
   ./gradlew run
   ```
4. Check it: <http://localhost:8080/health> → `{"status":"ok","database":"up"}`

Migrations in `src/main/resources/db/migration` run automatically on startup.

## Build the production jar

```bash
./gradlew buildFatJar   # → build/libs/movielist-backend.jar
```

## Layout

```
src/main/kotlin/com/example/movielist/backend/
├── Application.kt      # main() + module(): wires plugins and routes
├── config/             # AppConfig (environment variables)
├── db/                 # Hikari + Flyway setup, Exposed table mappings
├── plugins/            # JSON, error responses, request logging
└── features/           # one package per feature (health now; auth, friends, … next)
```

## Environment variables

| Name | Default (dev) | Notes |
|---|---|---|
| `APP_ENV` | `development` | set to `production` to make all the variables below required |
| `PORT` | `8080` | |
| `DB_URL` | `jdbc:postgresql://localhost:5432/movielist` | |
| `DB_USER` / `DB_PASSWORD` | `movielist` / `movielist` | |
| `JWT_SECRET` | dev placeholder | `openssl rand -base64 48` in production |
| `TMDB_TOKEN` | empty | TMDB v4 read access token |

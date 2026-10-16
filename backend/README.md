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
2. `cp .env.example .env` and fill in `TMDB_TOKEN` — without it `/movies` answers
   `502 TMDB_UNAVAILABLE`. `./gradlew run` loads `.env` for you; real environment
   variables take precedence.
3. Start the server:
   ```bash
   ./gradlew run
   ```
4. Check it: <http://localhost:8080/health> → `{"status":"ok","database":"up"}`

## Endpoints so far

| Method | Path | Auth | |
|---|---|---|---|
| GET | `/health` | – | database check |
| POST | `/auth/register`, `/auth/login` | – | returns `{token, user}` |
| GET / PATCH | `/me` | JWT | own profile |
| GET | `/users/search?q=&page=` | JWT | people by username or display name (2+ characters, ignoring case), exact matches first; 20 per page with `hasMore`; each has `friendship` |
| GET | `/users/{userId}` | JWT | public profile: name, member since, counts (likes, ratings, comments, friends), 10 newest likes, `friendship`, `isMe`; never the email |
| GET | `/friends` | JWT | your friends, by name |
| GET | `/friends/requests` | JWT | `{incoming, outgoing}` pending requests, newest first |
| POST | `/friends/requests` | JWT | `{userId}`; accepts instead if they already asked you; asking again changes nothing |
| POST | `/friends/requests/{userId}/accept` / `/decline` | JWT | answer a request they sent you |
| DELETE | `/friends/requests/{userId}` | JWT | cancel a request you sent |
| DELETE | `/friends/{userId}` | JWT | unfriend; all of these return `{userId, friendship}` |
| GET | `/movies/discover?page=&genre=` | JWT | popular movies, cached 10 min |
| GET | `/movies/search?q=&page=` | JWT | cached 10 min |
| GET | `/movies/{id}` | JWT | details, cached 24 h in `movies_cache` (stale copy served if TMDB is down), plus `avgRating`, `ratingCount`, `myRating`, `myReaction`, `commentCount` |
| GET | `/movies/{id}/trailers` | JWT | YouTube trailers and teasers, best first (`youtubeKey`, `name`); cached 6 h; empty if none |
| PUT / DELETE | `/movies/{id}/reaction` | JWT | `{reaction: LIKE\|DISLIKE}`; replaces any earlier one; a LIKE answers with `matchedPeople` (up to 10 others who like it, each with `user` and `sharedLikes`, most likes in common first) and `matchedCount` |
| GET | `/matches` | JWT | movies you like that anyone else (friend or stranger) likes too, newest like first, with `people` (best 10) and `peopleCount` |
| GET | `/me/reactions/ids` | JWT | `{liked: [ids], disliked: [ids]}` |
| GET | `/me/reactions?type=&limit=&before=` | JWT | movies with title/poster, newest first; `nextCursor` → `before` |
| PUT / DELETE | `/movies/{id}/rating` | JWT | `{score: 1..10}`; both return the updated stats |
| GET | `/movies/{id}/comments?limit=&before=` | JWT | top-level comments, newest first, 20 per page; each has `author`, `isMine`, `replyCount` and `authorRating` (the author's 1–10 score for the movie) |
| POST | `/movies/{id}/comments` | JWT | `{body}`, 1–1000 characters after trimming |
| GET | `/comments/{commentId}/replies?limit=&after=` | JWT | a thread's replies, oldest first |
| POST | `/comments/{commentId}/replies` | JWT | `{body}`; replying to a reply joins the same thread |
| PATCH / DELETE | `/comments/{commentId}` | JWT | comments and replies; author only (403 otherwise); soft delete, which hides a comment's whole thread |

TMDB errors become `502 TMDB_UNAVAILABLE`; TMDB's own error text is only logged.

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
├── plugins/            # JSON, error responses, request logging, JWT auth
├── tmdb/               # TmdbClient: the only code that talks to TMDB
├── util/               # TtlCache
└── features/           # one package per feature: health, auth, users, movies (friends, … next)
```

## Environment variables

| Name | Default (dev) | Notes |
|---|---|---|
| `APP_ENV` | `development` | set to `production` to make all the variables below required |
| `PORT` | `8080` | |
| `DB_URL` | `jdbc:postgresql://localhost:5432/movielist` | |
| `DB_USER` / `DB_PASSWORD` | `movielist` / `movielist` | |
| `JWT_SECRET` | dev placeholder | `openssl rand -base64 48` in production |
| `TMDB_TOKEN` | empty | TMDB "API Read Access Token"; a `Bearer ` prefix is accepted |

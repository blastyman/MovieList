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
| GET / PATCH | `/me` | JWT | own profile with privacy settings / `{displayName, username, avatarEmoji, avatarColor}` (blank clears; 409 `USERNAME_TAKEN`) |
| DELETE | `/me` | JWT | `{password}`: deletes the account and everything in it; owned groups go to the next member (204) |
| PATCH | `/me/privacy` | JWT | `{showInMatches, privateLikes, friendRequestsFrom, messagesFrom}`, each optional; `friendRequestsFrom` is EVERYONE, MATCHES or NOBODY, `messagesFrom` FRIENDS or NOBODY. Enforced on matches, profiles, friend requests and direct chats, each optional |
| PUT | `/me/email` / `/me/password` | JWT | `{email, password}` / `{currentPassword, newPassword}`; a wrong password is 403 `WRONG_PASSWORD` (not 401, so the app stays logged in) |
| POST / DELETE | `/me/avatar` | JWT | body = the picture (JPEG, PNG or WebP, ≤ 2 MB, checked by its bytes) / remove it |
| GET | `/avatars/{name}` | public | a profile picture; new name per upload, cached for a year |
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
| PUT / DELETE | `/movies/{id}/reaction` | JWT | `{reaction: LIKE\|DISLIKE}`; replaces any earlier one; a LIKE answers with `matchedPeople` (up to 10 others who like it, each with `user` and `sharedLikes`, most likes in common first), `matchedCount`, and `completedGroups` (your groups in which everyone now likes it) |
| GET | `/matches` | JWT | movies you like that anyone else (friend or stranger) likes too, newest like first, with `people` (best 10) and `peopleCount` |
| GET / POST | `/groups` | JWT | your groups, newest first / `{name, memberIds}` creates one (201); members must be your friends; 409 `GROUP_FULL` over 10 people, you included |
| GET / PATCH / DELETE | `/groups/{id}` | JWT | the group with every member, owner first / `{name}` renames it / disbands it for everyone (204); renaming and disbanding are owner only; 404 if you aren't in it |
| POST | `/groups/{id}/members` | JWT | `{userId}`: add one of your friends; 409 `GROUP_FULL` at 10 (checked under a row lock, so racing adds can't make 11) |
| DELETE | `/groups/{id}/members/{userId}` | JWT | owner removes someone (200 + group), or you leave (204); an owner who leaves hands over to the earliest member, the last one out deletes the group |
| GET | `/groups/{id}/matches` | JWT | movies every member likes, newest match first |
| GET | `/conversations` | JWT | your chats, most recently active first, with `lastMessage` and `unreadCount` (group chats have `groupId`; groups also report their `conversationId`) |
| POST | `/conversations/direct` | JWT | `{userId}`: the one-to-one chat with a friend, created on first use; 403 `NOT_FRIENDS` otherwise |
| GET | `/conversations/{id}` | JWT | one chat; 404 if you aren't in it |
| GET | `/conversations/{id}/messages?before=&limit=` | JWT | history, newest first; `nextCursor` → `before` |
| POST | `/conversations/{id}/messages` | JWT | `{body}` (1–2000 chars): sends without the WebSocket; still pushed live to the others. After unfriending, a direct chat is read-only |
| POST | `/conversations/{id}/read` | JWT | marks it read (204) |
| WS | `/ws` | JWT (header) | live chat. App sends `{type: send, conversationId, body, clientId}`, `{type: typing, …}`, `{type: read, …}`; server pushes `{type: message, message, clientId}`, `{type: typing, conversationId, userId}`, `{type: error, error, message, clientId}` |
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
| `UPLOAD_DIR` | `uploads` | where profile pictures are stored; a Docker volume in production |

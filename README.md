# MovieList

A social movie app for Android: swipe through movies, and meet people who like the same
ones. Built with Kotlin and Jetpack Compose on top of our own Kotlin (Ktor) + PostgreSQL server.

- **Discover:** recommendations and genres from TMDB; like or dislike (swipe left to like,
  right to dislike), search movies, watch trailers in the app.
- **Talk about movies:** star ratings in half steps (stored as 1–10) with an average, comments with one level of
  replies, and each commenter's own rating next to their name.
- **Meet people:** liking a movie someone else liked is a **match**, with an "It's a Match!"
  popup. Matches include strangers, ranked by how many likes you share, and every person
  links to their profile so you can add them.
- **Friends:** search people, send, accept, decline and cancel requests, and view profiles.
- **Groups:** up to 10 friends per group, owner included. Each group shows the movies everyone
  in it likes; the owner can add, remove, rename and disband.
- **Chat:** one-to-one with friends and one chat per group, live over a WebSocket, with
  unread badges, a typing indicator and Android's emoji picker.

Everything social lives on the server; the app holds no API keys.

## Architecture

```
Android app (Compose)                         Ktor server                         PostgreSQL
 ui/ screens ─▶ ViewModels (StateFlow)         routes ─▶ services ─▶ Exposed ─────▶ users, friendships,
        │                                         ▲                                 friend_groups, conversations,
        ▼                                         │ JWT on every call               messages, reactions, ratings,
 domain/ models + repository interfaces           │                                 comments, movies_cache
        ▲                                         │
 data/  Retrofit + OkHttp ── HTTPS (REST) ────────┤
        ChatSocket ───────── WebSocket /ws ───────┘    TmdbClient ─▶ TMDB (token on the server only)
```

**App (`app/`)**
- Three layers. `domain` holds plain models and repository interfaces. `data` implements
  them over Retrofit/OkHttp and maps the DTOs. `ui` has one ViewModel per screen exposing a
  `StateFlow`, and composables that only render state and call back.
- Manual dependency injection: `AppContainer` (owned by `MovieListApp`) builds the
  repositories and ViewModel factories. One OkHttp client adds the login token to every
  request, the WebSocket handshake included, and ends the session on a 401.
- Navigation Compose: drawer sections, plus routes for profiles, groups and chats.
- Likes, ratings and chat messages show at once and are confirmed by the server. A failed
  save is undone, or marked "tap to retry" for a message.

**Server (`backend/`)**
- Ktor 3 with kotlinx.serialization, Exposed over HikariCP, Flyway migrations (V1–V4), JWT
  auth and BCrypt password hashing. One feature package per area (auth, movies, reactions,
  ratings, comments, users, friends, matches, groups, chat), each with routes, a service and
  DTOs.
- Business rules are enforced in the database where they can be: unique and CHECK
  constraints, plus one row per user and movie for likes and ratings. The rest live in
  services, e.g. the 10-member group limit is checked under a row lock.
- Chat saves each message first, then pushes it to the members who are online.

The endpoint list is in [backend/README.md](backend/README.md); design decisions and the
roadmap are in [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

## Run it locally

You need Android Studio, JDK 21, and Docker Desktop for the database.

1. **Server.** Copy `backend/.env.example` to `backend/.env` (gitignored) and put your TMDB
   read access token in it. Then:

   ```powershell
   cd backend
   docker compose up -d   # PostgreSQL on localhost:5432
   .\gradlew.bat run      # server on http://localhost:8080, reads .env
   ```

   Check it with `curl http://localhost:8080/health`.
2. **App.** Open the project in Android Studio, let Gradle sync, and set your SDK path in
   the ignored `local.properties` (`sdk.dir=C:/Users/you/AppData/Local/Android/Sdk`). Run it
   on an emulator: debug builds talk to the server at `http://10.0.2.2:8080/`
   (`API_BASE_URL` in `app/build.gradle.kts`). Sign up, and add a second account on another
   emulator to try friends, matches, groups and chat.

## Tests

```powershell
# Server: integration tests against a real embedded PostgreSQL (no Docker needed)
cd backend; .\gradlew.bat test

# App: ViewModel unit tests with fake repositories, lint and a debug build
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# App: Compose UI tests (login, sending a chat message) on a running emulator
.\gradlew.bat :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` uninstalls the app afterwards, which logs you out. To keep your
session, install both APKs with `adb install -r` and run
`adb shell am instrument -w com.example.movielist.test/androidx.test.runner.AndroidJUnitRunner`.

## Deploy

The server ships as one jar (`cd backend; .\gradlew.bat buildFatJar` →
`build/libs/movielist-backend.jar`). It runs on a small VPS with PostgreSQL, behind Caddy for
HTTPS. The step-by-step guide is [docs/SERVER_SETUP.md](docs/SERVER_SETUP.md). A release
build of the app then points `API_BASE_URL` at the server's `https://` address. Chat works
through the same proxy, since Caddy passes WebSockets through.

Configuration comes only from environment variables: `APP_ENV=production` (which makes the rest
required), `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `TMDB_TOKEN` and `PORT` (see
[backend/README.md](backend/README.md)).

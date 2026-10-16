# MovieList

An Android movie discovery app built with Kotlin and Jetpack Compose. Browse
recommendations and genres, like or dislike movies, watch trailers, rate them with stars and
comment on them. Accounts, likes, ratings and comments live on our own server;
friends, chat and groups follow in the thesis plan.

## Setup

Open the project in Android Studio and let Gradle sync. The wrapper uses JDK 21;
the Android module targets Java 11 bytecode.

Set your Android SDK path in the ignored `local.properties` file:

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

The app holds no API keys. It talks only to our backend, which calls TMDB with its
own token, so nothing secret can be extracted from the APK.

## Backend

Accounts and movies (and, as the thesis plan progresses, friends, chat, groups,
comments and ratings) come from our own server in [`backend/`](backend/README.md):
Ktor + PostgreSQL. The app needs it running. Put your TMDB read access token in
`backend/.env` (copy `backend/.env.example`; the file is gitignored), then, with
Docker Desktop running:

```powershell
cd backend
docker compose up -d   # local PostgreSQL
.\gradlew.bat run      # server on http://localhost:8080, reads .env
```

Debug builds reach it from the emulator at `http://10.0.2.2:8080/` (`API_BASE_URL` in
`app/build.gradle.kts`). The roadmap is in [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md)
and hosting in [docs/SERVER_SETUP.md](docs/SERVER_SETUP.md).

## Structure

- `data/model`: backend DTOs and conversion to domain models.
- `data/remote`: Retrofit endpoints and HTTP client/authentication configuration.
- `data/repository`: API-backed loading, partial failure handling, and deduplication;
  `BackendAuthRepository` for login/session.
- `data/preferences`: the login token (DataStore).
- `domain/model`: immutable movie, account, reaction, rating, and category models.
- `domain/repository`: repository contracts for movies, reactions, ratings and auth.
- `domain/service`: category grouping.
- `ui/home`: ViewModel, observable screen state, and home rendering.
- `ui/auth`, `ui/comments`, `ui/details`, `ui/lists`, `ui/matches`, `ui/profile`, `ui/search`: feature screens.
- `ui/navigation`: drawer sections as Navigation Compose routes.
- `ui/components`, `ui/theme`, `ui/utils`: shared presentation code.

Classes use PascalCase, properties and methods use camelCase, and constants use
UPPER_SNAKE_CASE. Compose functions retain their conventional PascalCase names.
The backend already speaks camelCase; TMDB's snake_case names are mapped on the server.

The ViewModel owns loading, selections, and immutable user updates. Composables
render state and invoke callbacks; Navigation Compose owns which section is shown.
State is exposed through `StateFlow`. Likes and ratings are saved to the server
right away and shown before it answers; a failed save is undone with a message.
Movie pages load concurrently from the backend and are cached in memory for five
minutes. Genre sections assign a movie to the first matching genre.

## Verification

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS/Linux, use `./gradlew` instead. Unit tests use fake repositories/APIs
and do not need a TMDB token or a network connection.

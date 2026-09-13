# MovieList

An Android movie discovery app built with Kotlin and Jetpack Compose. Browse
recommendations and genres, like or dislike movies, switch between two demo
users, and see movies both users like.

## Setup

Open the project in Android Studio and let Gradle sync. The wrapper uses JDK 21;
the Android module targets Java 11 bytecode.

Set your Android SDK path and TMDB read access token in the ignored
`local.properties` file:

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
tmdb.token=your-tmdb-read-access-token
```

Alternatively, set the `TMDB_TOKEN` environment variable before building. It takes
precedence over the local property. The token may include the `Bearer ` prefix.
The build supplies it through `BuildConfig`; UI code never handles credentials.
This keeps credentials out of source control, but a token packaged in a client
application is still recoverable from its APK.

## Structure

- `data/model`: TMDB response DTOs and conversion to domain models.
- `data/remote`: Retrofit endpoints and HTTP client/authentication configuration.
- `data/repository`: API-backed loading, partial failure handling, and deduplication.
- `data/preferences`: DataStore-backed user rating persistence.
- `domain/model`: immutable movie, user, match, and category models.
- `domain/repository`: repository contract for loading movies.
- `domain/service`: category grouping and mutual-match calculation.
- `ui/home`: ViewModel, observable screen state, and home rendering.
- `ui/details`, `ui/lists`, `ui/matches`, `ui/user`: feature screens.
- `ui/components`, `ui/navigation`, `ui/theme`, `ui/utils`: shared presentation code.

Classes use PascalCase, properties and methods use camelCase, and constants use
UPPER_SNAKE_CASE. Compose functions retain their conventional PascalCase names.
API snake_case names are mapped with Gson annotations at the data boundary.

The ViewModel owns loading, selections, and immutable user updates. Composables
render state and invoke callbacks. State is exposed through `StateFlow`, while
user ratings are persisted with DataStore and survive process restarts. Movie
pages load concurrently and are cached in memory for five minutes. Genre
sections assign a movie to the first matching genre, preserving the original
behavior.

## Verification

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS/Linux, use `./gradlew` instead. Unit tests use fake repositories/APIs
and do not need a TMDB token or a network connection.

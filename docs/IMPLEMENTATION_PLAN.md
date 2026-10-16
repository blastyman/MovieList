# MovieList Social — Implementation Plan

Thesis project: turn the current MovieList Android app (Jetpack Compose + TMDB) into a
social movie app backed by a cloud server.

## 0. Where we start from

The app before this plan (commit `3a78e4d`):

| Area | State |
|---|---|
| Architecture | `domain/` (models, repository interfaces, `MovieCatalog`) + `data/` (TMDB, DataStore) + `ui/` per feature; manual DI in `AppContainer` |
| UI | Jetpack Compose; `HomeScreen` + `HomeViewModel` (`StateFlow<HomeUiState>`) switch between `AppSection`s (home, lists, matches, user) with a drawer |
| Data | `TmdbMovieRepository` → Retrofit `TmdbApi` directly from the phone |
| Users | Two local demo `AppUser`s; the user screen switches between them |
| Likes / matches | Liked/disliked ids per demo user, saved in DataStore; `MovieCatalog` finds movies both demo users like and shows a match popup |
| Security | TMDB token comes from `local.properties` via `BuildConfig` — out of git, but still inside the APK |

Everything social (users, friends, chat, groups, comments, ratings, likes) must move to the
server. The phone becomes a client of **our** API; it should no longer talk to TMDB directly.

**Progress:** Phase 1 ✅ · Phase 2 ✅ (backend auth + login/register screens, logout in the user screen) ·
Phase 3 ✅ (movies via `/movies/*` on the backend, TMDB token out of the app, Navigation Compose) ·
Phase 4 ✅ (likes/dislikes and 1–10 ratings per account on the server; demo users and local matches removed —
matches return with friend groups in Phase 9) ·
Phase 5 ✅ (comments under movies: newest first with "load older", author-only edit and soft delete,
one level of replies with @mentions — migration V2 adds `comments.parent_id`).

## 1. Required features

1. Accounts (register / login) — prerequisite for everything else
2. Friends system (send / accept / decline / remove requests)
3. Chat (1-to-1 with friends, plus a chat per friend group)
4. Friend groups, **max 10 members**, with group "matches" (movies every member liked)
5. Search: users by username, movies by title
6. Comments under movies
7. Movie trailers
8. Movie ratings
9. Persist existing likes / dislikes on the server

## 2. Architecture

```
┌──────────────────────┐        HTTPS (REST, JSON)         ┌───────────────────────────┐
│  Android app         │ ────────────────────────────────▶ │  Ktor backend (Docker)    │
│  Compose + ViewModel │                                   │  - Auth (JWT)             │
│  Retrofit / OkHttp   │ ◀──────── WebSocket (chat) ─────▶ │  - Friends / Groups       │
│  DataStore (token)   │                                   │  - Chat                   │
└──────────────────────┘                                   │  - Comments / Ratings     │
                                                           │  - TMDB proxy + cache     │
                                                           └──────┬──────────────┬─────┘
                                                                  │              │
                                                           ┌──────▼─────┐  ┌─────▼──────┐
                                                           │ PostgreSQL │  │  TMDB API  │
                                                           │ (managed)  │  │            │
                                                           └────────────┘  └────────────┘
```

### Recommended stack

| Layer | Choice | Why |
|---|---|---|
| Backend language | **Kotlin + Ktor 3** | Same language as the app; lightweight; native WebSocket support |
| Database | **PostgreSQL** | Relational data (friendships, memberships) fits SQL; constraints enforce rules like "one rating per user per movie" |
| DB access | **Exposed** (DSL) + **Flyway** migrations | Type-safe SQL in Kotlin; versioned schema you can show in the thesis |
| Auth | Username/email + password (**BCrypt**), **JWT** access tokens | Standard, easy to explain, no third-party dependency |
| Real-time | Ktor **WebSockets** | Chat delivery without polling |
| Hosting | **VPS** (e.g. OVH VPS-1) running Docker Compose: Caddy (HTTPS) + backend + Postgres — see [SERVER_SETUP.md](SERVER_SETUP.md). Managed alternative: Render / Railway / Fly.io | Cheap, full control, good material for the thesis |
| Android networking | Keep **Retrofit**, add **OkHttp** WebSocket + auth interceptor | Builds on what's already there |
| Android architecture | **ViewModel + StateFlow**, **Navigation Compose**, **DataStore** for the JWT | Survives rotation, testable, standard MVVM for the thesis write-up |
| Trailers | TMDB `/movie/{id}/videos` → YouTube key → `android-youtube-player` library | Plays in-app; no YouTube API key needed |

> **Alternative:** Firebase (Auth + Firestore + Cloud Functions). Faster to build, but most of the
> logic lives in security rules instead of a server you wrote, which gives you less to discuss
> in the thesis. Choose it only if time is very short. The rest of this plan assumes Ktor.

### Repository layout (monorepo)

```
MovieList/
├── app/                      # Android client (existing)
├── backend/                  # NEW Ktor server (separate Gradle project)
│   ├── src/main/kotlin/…/
│   │   ├── Application.kt
│   │   ├── plugins/          # Serialization, Auth, WebSockets, StatusPages, CORS
│   │   ├── db/               # Exposed tables, DatabaseFactory
│   │   ├── features/
│   │   │   ├── auth/         # routes + service + DTOs
│   │   │   ├── users/
│   │   │   ├── friends/
│   │   │   ├── groups/
│   │   │   ├── chat/
│   │   │   ├── movies/       # TMDB proxy, likes, ratings, comments
│   │   └── tmdb/             # TMDB client + cache
│   ├── src/main/resources/db/migration/  # Flyway V1__init.sql, …
│   ├── src/test/kotlin/…     # testApplication {} route tests
│   ├── Dockerfile
│   └── docker-compose.yml    # local Postgres + server
└── docs/
```

## 3. Data model (PostgreSQL)

```sql
users (
  id            UUID PK,
  username      VARCHAR(30) UNIQUE NOT NULL,   -- case-insensitive unique index on lower(username)
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  display_name  VARCHAR(50),
  avatar_url    TEXT,
  created_at    TIMESTAMPTZ DEFAULT now()
)

friendships (                                  -- one row per pair of users
  requester_id  UUID FK users,
  addressee_id  UUID FK users,
  status        VARCHAR(10) CHECK (status IN ('PENDING','ACCEPTED')),
  created_at    TIMESTAMPTZ,
  responded_at  TIMESTAMPTZ,
  PRIMARY KEY (requester_id, addressee_id),
  CHECK (requester_id <> addressee_id)
)
-- plus a unique index on (LEAST(a,b), GREATEST(a,b)) so A→B and B→A can't both exist

friend_groups (
  id          UUID PK,
  name        VARCHAR(50) NOT NULL,
  owner_id    UUID FK users,
  created_at  TIMESTAMPTZ
)

group_members (
  group_id   UUID FK friend_groups ON DELETE CASCADE,
  user_id    UUID FK users,
  joined_at  TIMESTAMPTZ,
  PRIMARY KEY (group_id, user_id)
)
-- max 10 members: enforced in the service inside a transaction
-- (SELECT … FOR UPDATE on the group row, count members, reject if >= 10)

conversations (
  id        UUID PK,
  type      VARCHAR(10) CHECK (type IN ('DIRECT','GROUP')),
  group_id  UUID NULL FK friend_groups ON DELETE CASCADE,   -- set when type = GROUP
  created_at TIMESTAMPTZ
)

conversation_members (
  conversation_id UUID FK conversations,
  user_id         UUID FK users,
  last_read_at    TIMESTAMPTZ,                 -- for unread counts
  PRIMARY KEY (conversation_id, user_id)
)

messages (
  id              UUID PK,
  conversation_id UUID FK conversations,
  sender_id       UUID FK users,
  body            TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
  created_at      TIMESTAMPTZ DEFAULT now()
)
-- index on (conversation_id, created_at DESC)

movie_reactions (                              -- replaces local liked/disliked lists
  user_id   UUID FK users,
  movie_id  INT NOT NULL,                      -- TMDB id
  reaction  VARCHAR(8) CHECK (reaction IN ('LIKE','DISLIKE')),
  created_at TIMESTAMPTZ,
  PRIMARY KEY (user_id, movie_id)
)

ratings (
  user_id   UUID FK users,
  movie_id  INT NOT NULL,
  score     SMALLINT CHECK (score BETWEEN 1 AND 10),   -- show as 5 stars with halves
  updated_at TIMESTAMPTZ,
  PRIMARY KEY (user_id, movie_id)
)

comments (
  id         UUID PK,
  movie_id   INT NOT NULL,
  user_id    UUID FK users,
  body       TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 1000),
  created_at TIMESTAMPTZ,
  edited_at  TIMESTAMPTZ NULL,
  deleted    BOOLEAN DEFAULT false
)
-- index on (movie_id, created_at DESC)

movies_cache (                                 -- optional: avoid hammering TMDB
  movie_id   INT PK,
  json       JSONB,
  fetched_at TIMESTAMPTZ
)
```

Movies themselves are **not** stored as our own entities — TMDB is the source of truth; we only
store user data keyed by TMDB `movie_id`.

## 4. API specification

All endpoints except `/auth/*` require `Authorization: Bearer <jwt>`. JSON everywhere.
Errors: `{ "error": "CODE", "message": "…" }` with proper HTTP status.
Lists use cursor pagination: `?limit=20&before=<timestamp or id>`.

### Auth
| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | `{username, email, password}` → `{token, user}` |
| POST | `/auth/login` | `{login, password}` → `{token, user}` |
| GET | `/me` | current user profile |
| PATCH | `/me` | update display name / avatar |

### Users & search
| Method | Path | Notes |
|---|---|---|
| GET | `/users/search?q=` | username prefix/contains, excludes self, includes friendship status for each result |
| GET | `/users/{id}` | public profile + counts (friends, likes, ratings) |

### Friends
| Method | Path | Notes |
|---|---|---|
| GET | `/friends` | accepted friends |
| GET | `/friends/requests` | `{incoming:[], outgoing:[]}` |
| POST | `/friends/requests` | `{userId}` — send request (if the other user already requested you, auto-accept) |
| POST | `/friends/requests/{userId}/accept` | |
| POST | `/friends/requests/{userId}/decline` | deletes the row |
| DELETE | `/friends/{userId}` | unfriend |

### Friend groups
| Method | Path | Notes |
|---|---|---|
| GET | `/groups` | groups I'm in |
| POST | `/groups` | `{name, memberIds[]}` — creator + members ≤ 10; members must be my friends; also creates the GROUP conversation |
| GET | `/groups/{id}` | details + members |
| POST | `/groups/{id}/members` | `{userId}` — only friends of the inviter; 409 `GROUP_FULL` at 10 |
| DELETE | `/groups/{id}/members/{userId}` | owner removes, or self leaves; owner leaving transfers ownership or deletes the group |
| GET | `/groups/{id}/matches` | movies liked by **all** members (replaces the old local demo-user matches, removed in Phase 4) |

### Chat
| Method | Path | Notes |
|---|---|---|
| GET | `/conversations` | list with last message + unread count |
| POST | `/conversations/direct` | `{userId}` → get or create DIRECT conversation (friends only) |
| GET | `/conversations/{id}/messages?before=&limit=` | history, newest first |
| POST | `/conversations/{id}/messages` | fallback send over REST |
| POST | `/conversations/{id}/read` | update `last_read_at` |
| WS | `/ws?token=<jwt>` | real-time channel (below) |

WebSocket frames (JSON):
```jsonc
// client → server
{ "type": "send", "conversationId": "…", "clientId": "tmp-123", "body": "hello" }
{ "type": "typing", "conversationId": "…" }
// server → client
{ "type": "message", "message": { "id": "…", "conversationId": "…", "senderId": "…", "body": "…", "createdAt": "…" }, "clientId": "tmp-123" }
{ "type": "typing", "conversationId": "…", "userId": "…" }
```
Server keeps an in-memory `Map<UserId, Set<WebSocketSession>>`; on a new message it saves to DB
first, then pushes to every online member. Offline members see it on next history fetch
(optional stretch: Firebase Cloud Messaging push notification).

### Movies (TMDB proxy + social data)
| Method | Path | Notes |
|---|---|---|
| GET | `/movies/discover?page=&genre=` | proxies TMDB `discover/movie` (replaces the app's direct call) |
| GET | `/movies/search?q=&page=` | proxies TMDB `search/movie` |
| GET | `/movies/{id}` | TMDB details + `{avgRating, ratingCount, myRating, myReaction, commentCount}` |
| GET | `/movies/{id}/trailers` | TMDB `movie/{id}/videos` filtered to `site=YouTube`, `type=Trailer`, official first |
| PUT | `/movies/{id}/reaction` | `{reaction: LIKE|DISLIKE}` |
| DELETE | `/movies/{id}/reaction` | |
| GET | `/me/reactions?type=LIKE` | for the Lists screen |
| PUT | `/movies/{id}/rating` | `{score: 1..10}` (upsert) |
| DELETE | `/movies/{id}/rating` | |
| GET | `/movies/{id}/comments?before=&limit=` | |
| POST | `/movies/{id}/comments` | `{body}` |
| PATCH | `/comments/{id}` | own comments only |
| DELETE | `/comments/{id}` | own comments only (soft delete) |

The TMDB token lives only in the server's environment variables.

## 5. Android client changes

### 5.1 Foundation
Done (Phases 2–3):
- Domain/data/ui layering, `HomeViewModel` with `StateFlow` UI state, DataStore.
- `AppContainer` owned by the `MovieListApp` Application class (one per process).
- `BackendClient` builds one Retrofit for `BuildConfig.API_BASE_URL` (`http://10.0.2.2:8080/`
  in debug, the VPS domain in release); it attaches the JWT and ends the session on a 401.
  Each feature gets its own interface on it: `AuthApi`, `MovieApi`, … (add `FriendsApi`,
  `ChatApi` the same way).
- `BackendAuthRepository` (domain interface `AuthRepository`) exposes `SessionState`;
  `MainActivity` shows login, a spinner, or Home based on it. JWT stored by `DataStoreTokenStore`.
- `BackendMovieRepository` loads movies through `/movies/discover`; the app has no TMDB token
  and no TMDB API calls (posters still load from TMDB's public image CDN).
- Drawer sections are Navigation Compose routes (`AppSection.route`) in `HomeScreen`'s
  `NavHost`; Back returns to home. Movie details are an overlay driven by `HomeViewModel`
  (like/dislike steps through the category). New screens with arguments (`profile/{userId}`,
  `chat/{conversationId}`) become `composable` routes in the same `NavHost`.
- Debug-only cleartext to `10.0.2.2` (`src/debug/res/xml/network_security_config.xml`);
  the token file is excluded from Android backups.

### 5.2 New / changed screens
| Screen | Content |
|---|---|
| Login / Register | forms, validation, error messages |
| Home (existing) | data now from `/movies/discover`; like/dislike calls `/movies/{id}/reaction` |
| Movie details (existing) | + trailer player, star rating bar with average, comments section with input |
| Lists (existing) | liked / disliked from `/me/reactions` |
| Search | tabs: **Movies** / **People**; debounced (300 ms) query |
| Profile (USER section) | own profile, edit, logout; other users' profile with add-friend button |
| Friends | friends list, incoming/outgoing requests with accept/decline |
| Groups | list, create group (pick friends, live counter "3/10"), group details, members |
| Matches (existing) | becomes per-group: `/groups/{id}/matches` |
| Chats | conversation list with unread badges → chat screen (bubbles, send box, auto-scroll, typing indicator) |

### 5.3 Chat client
- `ChatSocket` class wraps an OkHttp `WebSocket`, exposes `SharedFlow<ServerEvent>`, reconnects
  with exponential backoff, connects when the user is logged in and the app is in the foreground.
- Optimistic send: show the message immediately with a `clientId`, replace it when the server
  echoes it back.

## 6. Implementation phases

Each phase ends with something runnable and a commit. Rough estimates assume part-time work.

| # | Phase | Deliverable | Est. |
|---|---|---|---|
| 1 | Backend skeleton | Ktor project, Docker Compose Postgres, Flyway V1, health endpoint, StatusPages, test setup | 3–4 d |
| 2 | Auth | register/login/me, BCrypt, JWT; Android login/register screens, TokenStore, interceptor | 4–5 d |
| 3 | TMDB proxy + navigation | `/movies/discover` etc. on the backend, app uses it; TMDB token out of the APK; Navigation Compose | 3–4 d |
| 4 | Reactions + ratings | likes/dislikes on the server per account (replaces DataStore likes); rating UI and averages | 3 d |
| 5 | Comments | list/post/edit/delete with pagination | 3 d |
| 6 | Trailers | trailer endpoint + in-app YouTube player | 1–2 d |
| 7 | Search + profiles | user search, movie search, profile screens | 3 d |
| 8 | Friends | request lifecycle, friends screens | 4 d |
| 9 | Groups (≤10) + matches | create/invite/leave, limit enforced, matches screen per group | 4 d |
| 10 | Chat | REST history + WebSocket, direct and group chats, unread counts | 6–8 d |
| 11 | Deployment | Dockerfile, deploy to the VPS per [SERVER_SETUP.md](SERVER_SETUP.md), HTTPS via Caddy, env-based config, backups | 2–3 d |
| 12 | Polish & testing | loading/empty/error states, tests, README, screenshots for the thesis | 4–5 d |

Optional extras if time allows: FCM push notifications, avatars (upload to object storage),
rate limiting, block/report users, offline cache with Room.

## 7. Business rules & edge cases (checklist)

- [ ] Usernames unique case-insensitively; password ≥ 8 chars
- [ ] Can't friend yourself; no duplicate requests in either direction
- [ ] Only friends can be invited to a group or start a direct chat
- [ ] Group size ≤ 10 including owner — enforced on the server, **inside a transaction** (two simultaneous invites must not produce 11)
- [ ] Removing a friend does not remove them from shared groups (document the choice)
- [ ] Only conversation members can read/send messages (check on every REST call and WS frame)
- [ ] Only the author can edit/delete a comment
- [ ] One rating and one reaction per user per movie (DB primary key + upsert)
- [ ] Input length limits on messages, comments, names; reject empty/whitespace
- [ ] Every list endpoint is paginated

## 8. Security

- Passwords hashed with BCrypt (cost ≥ 10); never logged or returned
- JWT signed with a secret from env vars; reasonable expiry (e.g. 7 days) — refresh tokens optional
- All secrets (JWT secret, DB URL, TMDB token) via environment variables; `.env` in `.gitignore`
- HTTPS only in production (the host's TLS); Android `network_security_config` allows cleartext only for `10.0.2.2` in debug
- Authorization checks on every resource (user may only touch their own comments, groups they're in, etc.)
- Parameterized queries (Exposed does this) — no string-built SQL
- The TMDB token lives on the server only (since Phase 3): a token built into an APK can be extracted

## 9. Testing

- **Backend:** Ktor `testApplication {}` integration tests against a Testcontainers Postgres
  (or an H2/embedded fallback) — at least: auth, friend request flow, group limit of 10,
  chat membership authorization, rating upsert/average.
- **Android:** unit tests for ViewModels with fake repositories; a couple of Compose UI tests
  (login, sending a message).
- Manual test script with two emulators logged in as different users for chat/friends.

## 10. Thesis write-up hooks

Material worth collecting as you go: architecture diagram (§2), ER diagram from §3, API table (§4),
sequence diagram of sending a chat message over WebSocket, the transaction used for the group limit,
screenshots of each screen, and a short evaluation (test results, response times, limitations).

---

## 11. Prompts for Claude Code (one per phase)

Paste these one at a time in a fresh session from the repo root. Each refers back to this file,
so keep it up to date if you change decisions.

### Prompt — Phase 1: backend skeleton
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 1: create a new Gradle project in
backend/ using Kotlin + Ktor 3 (Netty), kotlinx.serialization, Exposed, Flyway, HikariCP and
the PostgreSQL driver. Add a docker-compose.yml with Postgres 16, a Flyway migration V1 that
creates every table in §3 of the plan, config read from environment variables (DB_URL,
DB_USER, DB_PASSWORD, JWT_SECRET, TMDB_TOKEN, PORT), a StatusPages plugin that returns the
error JSON format from §4, a GET /health endpoint, and one passing testApplication test.
Follow the folder layout in §2. Don't touch the Android app yet. Tell me how to run it locally.
```

### Prompt — Phase 2: authentication
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 2 (auth) end to end.
Backend: /auth/register, /auth/login, GET/PATCH /me with BCrypt hashing and JWT auth (Ktor
Authentication plugin), validation per §7, and tests.
Android: add an Application class with a simple AppContainer for DI, an ApiClient using
BuildConfig.API_BASE_URL (10.0.2.2:8080 for debug), DataStore-backed TokenStore, an
AuthInterceptor, and Login/Register screens with ViewModels. The app should open on login
when there's no token and on Home when there is. Add a debug-only network security config.
```

### Prompt — Phase 3: TMDB proxy + navigation
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 3.
Backend: a TMDB client using TMDB_TOKEN and authenticated GET /movies/discover,
/movies/search, /movies/{id} proxies (optionally cached in movies_cache), with tests that
don't hit the real TMDB.
Android: add a backend-backed MovieRepository implementation behind the existing domain
MovieRepository interface and switch AppContainer to it, so the app no longer calls TMDB;
remove TMDB_TOKEN from app/build.gradle.kts and the README setup. Replace AppSection
switching with Navigation Compose, keeping the drawer and the current look of every screen.
Keep likes/dislikes and the demo-user matches working as they are — Phase 4 moves them.
```

### Prompt — Phase 4: reactions + ratings
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 4: movie reactions (like/dislike) and
ratings (1–10, shown as 5 stars with half stars) per §3/§4. GET /movies/{id} must include
avgRating, ratingCount, myRating and myReaction. On Android, store the signed-in account's
likes/dislikes via the API instead of DataStoreUserPreferencesRepository (optimistic update,
roll back on failure), load the Lists screen from /me/reactions, and add a rating bar plus
the average to MovieDetailsScreen. The local demo users and their matches can stay until
Phase 9. Add backend tests for the upsert and the average.
```

### Prompt — Phase 5: comments
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 5: comments under movies — backend
endpoints from §4 with cursor pagination and author-only edit/soft-delete; on Android a
comments section in MovieDetailsScreen with an input field, author name, relative time,
load-more on scroll, and edit/delete for your own comments.
```

### Prompt — Phase 6: trailers
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 6: GET /movies/{id}/trailers on the
backend (TMDB videos, YouTube only, official trailers first). On Android add the
pierfrancescosoffritti android-youtube-player library and show the first trailer in
MovieDetailsScreen with a "watch trailer" button; fall back to opening the YouTube app or
browser if the player fails, and hide the button when there's no trailer.
```

### Prompt — Phase 7: search + profiles
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 7: GET /users/search (case-insensitive,
returns friendship status per result) and GET /users/{id}. On Android add a Search screen
with Movies/People tabs and a 300 ms debounced query, a Profile screen for the USER section
(own profile, edit display name, logout) and a public profile screen for other users.
```

### Prompt — Phase 8: friends
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 8: the full friends system from §4 and the
rules in §7 (no self-requests, no duplicates in either direction, reverse request
auto-accepts). Android: Friends screen with friends list and incoming/outgoing requests
(accept/decline/cancel), and an add/remove-friend button on public profiles and search
results. Backend tests for the full request lifecycle.
```

### Prompt — Phase 9: friend groups
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 9: friend groups with at most 10 members
including the owner. Enforce the limit on the server inside a transaction that locks the
group row, return 409 GROUP_FULL, and only allow inviting friends. Creating a group also
creates its GROUP conversation. Implement GET /groups/{id}/matches (movies liked by every
member). Android: groups list, create-group screen with friend picker and an "n/10" counter,
group details (members, leave/remove), and fill the Matches screen per group. Bring back a
"MATCH!" popup when a like completes a group match (the old demo-user MatchPopup.kt, removed
in Phase 4, is in git history). Include a test that 11 members is rejected, including when
two invites race.
```

### Prompt — Phase 10: chat
```
Read docs/IMPLEMENTATION_PLAN.md. Implement Phase 10: chat. Backend: conversation and
message REST endpoints plus the /ws WebSocket with the frame format in §4, JWT auth on
connect, membership checks on every frame, save-then-broadcast to online members, and unread
counts via last_read_at. Android: a ChatSocket wrapper around OkHttp WebSocket with
reconnect/backoff exposing a Flow, a Chats list (last message, unread badge), and a Chat
screen with bubbles, optimistic sending using clientId, paginated history, auto-scroll and a
typing indicator. Direct chats can be started from a friend's profile; group chats open from
the group screen.
```

### Prompt — Phase 11: deployment
Use the prompt at the end of [SERVER_SETUP.md](SERVER_SETUP.md), then make the Android
release build use `https://api.<your-domain>/` as its API URL.

### Prompt — Phase 12: polish
```
Read docs/IMPLEMENTATION_PLAN.md. Go through every screen and add consistent loading, empty
and error states with retry. Fill gaps in backend tests for the §7 checklist and add
ViewModel unit tests with fake repositories. Update README.md with architecture overview,
how to run locally, and how to deploy.
```

-- MovieList Social: initial schema (see docs/IMPLEMENTATION_PLAN.md §3).
-- Movies are not stored as entities: TMDB is the source of truth and we key user data by TMDB id.

-- ───────────── Users ─────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(30)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash TEXT         NOT NULL,
    display_name  VARCHAR(50),
    avatar_url    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness: "Nikos" and "nikos" are the same user.
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

-- ───────────── Friends ─────────────
CREATE TABLE friendships (
    requester_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    PRIMARY KEY (requester_id, addressee_id),
    CHECK (requester_id <> addressee_id)
);

-- Only one row per pair, whichever direction the request went (A→B blocks B→A).
CREATE UNIQUE INDEX friendships_pair_key
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX friendships_addressee_idx ON friendships (addressee_id);

-- ───────────── Friend groups (max 10 members, enforced in the service layer) ─────────────
CREATE TABLE friend_groups (
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name       VARCHAR(50) NOT NULL,
    owner_id   UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    group_id  UUID        NOT NULL REFERENCES friend_groups (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX group_members_user_idx ON group_members (user_id);

-- ───────────── Chat ─────────────
CREATE TABLE conversations (
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    type       VARCHAR(10) NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    group_id   UUID UNIQUE REFERENCES friend_groups (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- a GROUP conversation always belongs to a group; a DIRECT one never does
    CHECK ((type = 'GROUP') = (group_id IS NOT NULL))
);

CREATE TABLE conversation_members (
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    last_read_at    TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX conversation_members_user_idx ON conversation_members (user_id);

CREATE TABLE messages (
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       UUID        NOT NULL REFERENCES users (id),
    body            TEXT        NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX messages_conversation_created_idx ON messages (conversation_id, created_at DESC);

-- ───────────── Movie social data ─────────────
CREATE TABLE movie_reactions (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    movie_id   INT         NOT NULL,
    reaction   VARCHAR(8)  NOT NULL CHECK (reaction IN ('LIKE', 'DISLIKE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, movie_id)
);

-- Group "matches" look up likes by movie.
CREATE INDEX movie_reactions_movie_idx ON movie_reactions (movie_id, reaction);

CREATE TABLE ratings (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    movie_id   INT         NOT NULL,
    score      SMALLINT    NOT NULL CHECK (score BETWEEN 1 AND 10),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, movie_id)
);

CREATE INDEX ratings_movie_idx ON ratings (movie_id);

CREATE TABLE comments (
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    movie_id   INT         NOT NULL,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL CHECK (length(body) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at  TIMESTAMPTZ,
    deleted    BOOLEAN     NOT NULL DEFAULT false
);

CREATE INDEX comments_movie_created_idx ON comments (movie_id, created_at DESC);

-- ───────────── TMDB response cache ─────────────
CREATE TABLE movies_cache (
    movie_id   INT PRIMARY KEY,
    json       JSONB       NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

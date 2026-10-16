-- People search matches anywhere inside a username or display name (ILIKE '%term%').
-- A normal B-tree index can't help with a leading wildcard; a trigram index can.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX users_username_trgm_idx ON users USING gin (lower(username) gin_trgm_ops);
CREATE INDEX users_display_name_trgm_idx ON users USING gin (lower(display_name) gin_trgm_ops);

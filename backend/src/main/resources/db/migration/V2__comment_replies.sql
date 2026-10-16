-- Replies to comments, one level deep: a reply's parent is always a top-level comment
-- (enforced in CommentService). Deleting a comment's row would remove its replies too, but
-- the app only soft-deletes, which hides the whole thread.

ALTER TABLE comments
    ADD COLUMN parent_id UUID REFERENCES comments (id) ON DELETE CASCADE;

-- Loading a thread's replies in order, and counting them.
CREATE INDEX comments_parent_created_idx ON comments (parent_id, created_at);

-- One DIRECT conversation per pair of users. The key is "<smaller id>:<larger id>", so both
-- people starting a chat with each other at once still end up in the same conversation:
-- the second insert hits the unique index and reads the first one instead.
ALTER TABLE conversations ADD COLUMN direct_key TEXT UNIQUE;
ALTER TABLE conversations
    ADD CONSTRAINT conversations_direct_key_check CHECK ((type = 'DIRECT') = (direct_key IS NOT NULL));

-- Deleting an account removes its messages and the groups it owns, instead of being blocked by them.
ALTER TABLE messages
    DROP CONSTRAINT messages_sender_id_fkey,
    ADD CONSTRAINT messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE friend_groups
    DROP CONSTRAINT friend_groups_owner_id_fkey,
    ADD CONSTRAINT friend_groups_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;

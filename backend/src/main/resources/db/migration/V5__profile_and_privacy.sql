-- Profile pictures: avatar_url (already there) points at an uploaded photo; without one, an
-- emoji on a coloured circle can stand in for the first letter of the name.
ALTER TABLE users ADD COLUMN avatar_emoji TEXT CHECK (char_length(avatar_emoji) BETWEEN 1 AND 16);
ALTER TABLE users ADD COLUMN avatar_color CHAR(7) CHECK (avatar_color ~ '^#[0-9A-Fa-f]{6}$');

-- Privacy. The defaults keep today's behaviour for existing accounts.
-- show_in_matches: strangers see you in their matches (friends always do).
-- private_likes:   only friends see your liked movies and like count.
-- friend_requests_from: who may send you a friend request.
-- messages_from:   who may start or continue a one-to-one chat with you (group chats aren't affected).
ALTER TABLE users ADD COLUMN show_in_matches BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN private_likes BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN friend_requests_from VARCHAR(10) NOT NULL DEFAULT 'EVERYONE'
    CHECK (friend_requests_from IN ('EVERYONE', 'MATCHES', 'NOBODY'));
ALTER TABLE users ADD COLUMN messages_from VARCHAR(10) NOT NULL DEFAULT 'FRIENDS'
    CHECK (messages_from IN ('FRIENDS', 'NOBODY'));

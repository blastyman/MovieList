-- Group icons, like profile pictures: an uploaded photo, or an emoji on a colour.
-- Without either, the app shows its group symbol.
ALTER TABLE friend_groups ADD COLUMN avatar_url TEXT;
ALTER TABLE friend_groups ADD COLUMN avatar_emoji TEXT CHECK (char_length(avatar_emoji) BETWEEN 1 AND 16);
ALTER TABLE friend_groups ADD COLUMN avatar_color CHAR(7) CHECK (avatar_color ~ '^#[0-9A-Fa-f]{6}$');

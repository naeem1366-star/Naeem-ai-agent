ALTER TABLE users ADD COLUMN chat_free_period TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN chat_free_used INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_usage_chat_user_created ON usage_events(user_id, feature, created_at DESC);

ALTER TABLE users ADD COLUMN chat_free_period TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN chat_free_used INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_usage_chat_user_created ON usage_events(user_id, feature, created_at DESC);
CREATE TABLE IF NOT EXISTS chat_global_usage (
  period TEXT PRIMARY KEY,
  request_count INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);

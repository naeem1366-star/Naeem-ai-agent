-- EMA billing foundation: separate subscriptions from prepaid AI credit.
-- Prices and FX are intentionally disabled until configured by an administrator.
ALTER TABLE pricing_rules ADD COLUMN input_price_per_million_usd TEXT NOT NULL DEFAULT '0';
ALTER TABLE pricing_rules ADD COLUMN output_price_per_million_usd TEXT NOT NULL DEFAULT '0';

UPDATE pricing_rules SET markup_bps = 3000;

CREATE TABLE IF NOT EXISTS billing_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  markup_bps INTEGER NOT NULL DEFAULT 3000 CHECK (markup_bps >= 0 AND markup_bps <= 100000),
  usd_to_toman INTEGER NOT NULL DEFAULT 0 CHECK (usd_to_toman >= 0),
  ai_billing_enabled INTEGER NOT NULL DEFAULT 0 CHECK (ai_billing_enabled IN (0,1)),
  updated_at INTEGER NOT NULL
);
INSERT OR IGNORE INTO billing_settings (id, markup_bps, usd_to_toman, ai_billing_enabled, updated_at)
VALUES (1, 3000, 0, 0, unixepoch());

CREATE TABLE IF NOT EXISTS subscription_plans (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  duration_days INTEGER NOT NULL CHECK (duration_days > 0),
  price_toman INTEGER NOT NULL DEFAULT 0 CHECK (price_toman >= 0),
  enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0,1)),
  updated_at INTEGER NOT NULL
);
INSERT OR IGNORE INTO subscription_plans (id, name, duration_days, price_toman, enabled, updated_at)
VALUES
  ('professional-monthly', 'EMA Professional Monthly', 30, 0, 0, unixepoch()),
  ('professional-yearly', 'EMA Professional Yearly', 365, 0, 0, unixepoch());

CREATE TABLE IF NOT EXISTS subscriptions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  plan_id TEXT NOT NULL REFERENCES subscription_plans(id),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','active','expired','cancelled')),
  starts_at INTEGER,
  expires_at INTEGER,
  payment_order_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_expiry
  ON subscriptions(user_id, status, expires_at DESC);

ALTER TABLE payment_orders ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'online';
ALTER TABLE payment_orders ADD COLUMN proof_note TEXT;
ALTER TABLE payment_orders ADD COLUMN reviewed_by TEXT;
ALTER TABLE payment_orders ADD COLUMN reviewed_at INTEGER;
ALTER TABLE payment_orders ADD COLUMN idempotency_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_idempotency
  ON payment_orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_events (
  id TEXT PRIMARY KEY,
  gateway TEXT NOT NULL,
  event_id TEXT NOT NULL,
  payment_order_id TEXT,
  payload_hash TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  UNIQUE(gateway, event_id)
);

CREATE TABLE IF NOT EXISTS usage_reservations (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  feature TEXT NOT NULL,
  model TEXT NOT NULL,
  reserved_toman INTEGER NOT NULL CHECK (reserved_toman > 0),
  settled_toman INTEGER NOT NULL DEFAULT 0 CHECK (settled_toman >= 0),
  status TEXT NOT NULL DEFAULT 'reserved' CHECK (status IN ('reserved','settled','released','failed')),
  provider_request_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_usage_reservations_user_status
  ON usage_reservations(user_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS manual_payment_reviews (
  id TEXT PRIMARY KEY,
  payment_order_id TEXT NOT NULL UNIQUE REFERENCES payment_orders(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  receipt_reference TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected')),
  admin_note TEXT,
  created_at INTEGER NOT NULL,
  reviewed_at INTEGER,
  reviewed_by TEXT
);
CREATE INDEX IF NOT EXISTS idx_manual_payment_reviews_status
  ON manual_payment_reviews(status, created_at DESC);

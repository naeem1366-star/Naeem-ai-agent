-- EMA subscription value split: default 50% of a paid subscription becomes AI credit.
-- This is accounting configuration only; credits are granted by the server after verified payment.
ALTER TABLE billing_settings
  ADD COLUMN subscription_credit_percent INTEGER NOT NULL DEFAULT 50
  CHECK (subscription_credit_percent >= 0 AND subscription_credit_percent <= 100);

ALTER TABLE subscription_plans
  ADD COLUMN ai_credit_percent INTEGER NOT NULL DEFAULT 50
  CHECK (ai_credit_percent >= 0 AND ai_credit_percent <= 100);

ALTER TABLE payment_orders
  ADD COLUMN purpose TEXT NOT NULL DEFAULT 'wallet_topup'
  CHECK (purpose IN ('wallet_topup','subscription'));
ALTER TABLE payment_orders
  ADD COLUMN subscription_plan_id TEXT REFERENCES subscription_plans(id);

ALTER TABLE subscriptions
  ADD COLUMN paid_amount_toman INTEGER NOT NULL DEFAULT 0 CHECK (paid_amount_toman >= 0);
ALTER TABLE subscriptions
  ADD COLUMN ai_credit_granted_toman INTEGER NOT NULL DEFAULT 0 CHECK (ai_credit_granted_toman >= 0);

CREATE TABLE IF NOT EXISTS subscription_credit_grants (
  id TEXT PRIMARY KEY,
  subscription_id TEXT NOT NULL UNIQUE REFERENCES subscriptions(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  gross_price_toman INTEGER NOT NULL CHECK (gross_price_toman >= 0),
  credit_percent INTEGER NOT NULL CHECK (credit_percent >= 0 AND credit_percent <= 100),
  credit_amount_toman INTEGER NOT NULL CHECK (credit_amount_toman >= 0),
  wallet_transaction_id TEXT NOT NULL UNIQUE REFERENCES wallet_transactions(id),
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_subscription_credit_user_created
  ON subscription_credit_grants(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS provider_cost_ledger (
  id TEXT PRIMARY KEY,
  usage_reservation_id TEXT NOT NULL UNIQUE REFERENCES usage_reservations(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  provider TEXT NOT NULL,
  provider_cost_microusd INTEGER NOT NULL CHECK (provider_cost_microusd >= 0),
  exchange_rate_toman INTEGER NOT NULL CHECK (exchange_rate_toman > 0),
  markup_bps INTEGER NOT NULL CHECK (markup_bps >= 0),
  user_charge_toman INTEGER NOT NULL CHECK (user_charge_toman >= 0),
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_provider_cost_user_created
  ON provider_cost_ledger(user_id, created_at DESC);

-- Plan amounts remain disabled until the operator sets real prices.
-- Per-plan credit percentage is configurable and defaults to the requested 50%.

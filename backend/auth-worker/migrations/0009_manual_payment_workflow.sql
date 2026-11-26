-- Manual payment workflow hardening and idempotent subscription settlement.
ALTER TABLE billing_settings
  ADD COLUMN payment_instructions TEXT NOT NULL DEFAULT '';
ALTER TABLE billing_settings
  ADD COLUMN min_wallet_topup_toman INTEGER NOT NULL DEFAULT 100000
  CHECK (min_wallet_topup_toman > 0);
ALTER TABLE billing_settings
  ADD COLUMN max_wallet_topup_toman INTEGER NOT NULL DEFAULT 50000000
  CHECK (max_wallet_topup_toman >= min_wallet_topup_toman);

ALTER TABLE payment_orders
  ADD COLUMN receipt_submitted_at INTEGER;
ALTER TABLE payment_orders
  ADD COLUMN rejection_reason TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_payment_order
  ON subscriptions(payment_order_id)
  WHERE payment_order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_orders_review
  ON payment_orders(status, purpose, created_at DESC);

-- Reserve a configurable share of subscription receipts for replenishing the upstream AI provider account.
-- This is NOT a user bonus credit. It is an EMA operating allocation tracked separately from user wallets.
ALTER TABLE billing_settings
  ADD COLUMN provider_replenishment_percent INTEGER NOT NULL DEFAULT 50
  CHECK (provider_replenishment_percent >= 0 AND provider_replenishment_percent <= 100);

CREATE TABLE IF NOT EXISTS provider_replenishment_allocations (
  id TEXT PRIMARY KEY,
  payment_order_id TEXT NOT NULL UNIQUE REFERENCES payment_orders(id),
  provider TEXT NOT NULL DEFAULT 'netarz',
  gross_subscription_toman INTEGER NOT NULL CHECK (gross_subscription_toman >= 0),
  allocation_percent INTEGER NOT NULL CHECK (allocation_percent >= 0 AND allocation_percent <= 100),
  allocated_toman INTEGER NOT NULL CHECK (allocated_toman >= 0),
  status TEXT NOT NULL DEFAULT 'reserved' CHECK (status IN ('reserved','transferred','cancelled')),
  provider_topup_reference TEXT,
  transferred_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_provider_replenishment_status
  ON provider_replenishment_allocations(status, created_at DESC);

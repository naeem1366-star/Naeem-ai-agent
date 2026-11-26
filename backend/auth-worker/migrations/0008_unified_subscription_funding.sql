-- Keep one 50% subscription allocation and link provider funding records
-- to the matching customer AI-credit grant. This avoids counting the same
-- subscription amount as two separate 50% deductions.
ALTER TABLE provider_replenishment_allocations
  ADD COLUMN subscription_credit_grant_id TEXT
  REFERENCES subscription_credit_grants(id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_provider_replenishment_credit_grant
  ON provider_replenishment_allocations(subscription_credit_grant_id)
  WHERE subscription_credit_grant_id IS NOT NULL;

-- The provider allocation percentage must mirror the customer AI-credit grant;
-- it is not a second independently configurable percentage.
ALTER TABLE billing_settings DROP COLUMN provider_replenishment_percent;

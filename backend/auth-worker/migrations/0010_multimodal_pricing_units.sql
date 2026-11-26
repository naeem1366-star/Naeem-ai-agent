-- Explicit units for multimodal pricing. Token prices remain in the existing
-- input/output per-million columns; other features use unit_price_usd.
ALTER TABLE pricing_rules
  ADD COLUMN billing_unit TEXT NOT NULL DEFAULT 'tokens'
  CHECK (billing_unit IN ('tokens','million_characters','second','request','minute'));
ALTER TABLE pricing_rules
  ADD COLUMN unit_price_usd TEXT NOT NULL DEFAULT '0';

# EMA Core — Proposed Data Model

PostgreSQL is the source of truth. All monetary amounts use integer toman; all credit amounts use integer units. Avoid floating-point arithmetic for balances.

## Tables

### customers
- id (UUID, primary key)
- external_auth_subject (nullable during identity migration; unique when present)
- status (active, suspended, closed)
- created_at, updated_at

### activation_codes
- id (UUID)
- code_hash (unique; never store plaintext code)
- credits_to_grant (integer > 0)
- expires_at (nullable)
- redeemed_by_customer_id (nullable FK)
- redeemed_at (nullable)
- created_by_admin_id
- created_at

### wallets
- customer_id (PK/FK)
- balance_credits (bigint, >= 0)
- updated_at

### wallet_ledger
- id (UUID)
- customer_id (FK)
- entry_type (grant, reserve, capture, release, refund, adjustment)
- amount_credits (signed bigint; immutable)
- operation_id (UUID; idempotency scope)
- feature (chat, image, video, admin)
- metadata (JSONB, sanitized; no secrets or prompts)
- created_at
- unique(customer_id, operation_id, entry_type)

### usage_records
- id (UUID)
- customer_id (FK)
- feature (chat, image, video)
- provider_key (internal provider identifier, not secret)
- model_id
- provider_cost_minor (integer; internal cost accounting)
- customer_charge_credits (integer)
- request_id (unique)
- status
- created_at, completed_at

### generation_jobs
- id (UUID)
- customer_id (FK)
- feature (image, video)
- status (queued, processing, succeeded, failed, cancelled)
- provider_task_id (nullable)
- idempotency_key
- reserved_credits
- charged_credits (nullable)
- input_metadata (sanitized JSONB; avoid storing raw prompts unless required and disclosed)
- result_object_key (nullable)
- error_code (nullable, sanitized)
- attempt_count
- lease_expires_at (nullable)
- created_at, updated_at, completed_at
- unique(customer_id, idempotency_key)

### pricing_rules
- id (UUID)
- feature (chat, image, video)
- model_id (nullable)
- pricing_config (JSONB)
- enabled
- updated_by_admin_id
- updated_at

### admin_audit_log
- id (UUID)
- admin_id
- action
- target_type, target_id
- safe_metadata (JSONB)
- created_at

## Required database transactions

1. Redeem activation code: lock code row, verify unused/not expired, mark redeemed, create or update wallet, append grant ledger entry — one transaction.
2. Reserve credits: lock wallet row, verify available balance, decrement balance, append reserve entry, create job/usage operation — one transaction.
3. Finalize job: lock operation, capture actual charge and release remainder, or refund reservation on failure — one transaction.
4. Admin adjustment: append ledger and update wallet in one transaction, always with an audit record.

## Data protection

- Never store plaintext activation codes or provider secrets.
- Do not store access tokens in ledger metadata.
- Restrict media objects by customer ownership and short-lived signed URLs.
- Retention and deletion policy must be defined before public launch.

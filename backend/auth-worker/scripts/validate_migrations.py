from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys = ON")

base = ROOT / "schema.sql"
db.executescript(base.read_text(encoding="utf-8"))
migration_dir = ROOT / "migrations"
for path in sorted(migration_dir.glob("*.sql")):
    db.executescript(path.read_text(encoding="utf-8"))
    print(f"OK {path.name}")

def columns(table):
    return {row[1] for row in db.execute(f"PRAGMA table_info({table})")}

assert "wallet_balance_toman" in columns("users")
assert "subscription_credit_percent" in columns("billing_settings")
assert "provider_replenishment_percent" not in columns("billing_settings")
assert "subscription_credit_grant_id" in columns("provider_replenishment_allocations")
assert "ai_credit_percent" in columns("subscription_plans")

tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
required = {
    "users", "wallet_transactions", "payment_orders", "subscriptions",
    "subscription_credit_grants", "provider_replenishment_allocations",
    "usage_reservations", "provider_cost_ledger",
}
missing = required - tables
assert not missing, f"Missing tables: {sorted(missing)}"
print("All EMA D1 migrations applied successfully in SQLite.")

# Exercise the same one-time subscription settlement pattern used by the Worker.
now = 1_800_000_000
db.execute(
    "INSERT INTO users (id, phone, email, created_at, last_login_at, wallet_balance_toman, status) VALUES (?, ?, ?, ?, ?, 0, 'active')",
    ("test-user", "supabase:test-user", "test@example.com", now, now),
)
db.execute(
    "UPDATE subscription_plans SET price_toman = 1_000_000, enabled = 1, ai_credit_percent = 50 WHERE id = 'professional-monthly'"
)
db.execute(
    "INSERT INTO payment_orders (id, user_id, amount_toman, gateway, status, created_at, payment_method, idempotency_key, purpose, subscription_plan_id, gateway_ref, receipt_submitted_at) VALUES ('test-order', 'test-user', 1000000, 'manual', 'pending', ?, 'manual', 'test-idempotency-1', 'subscription', 'professional-monthly', 'receipt-123', ?)",
    (now, now),
)
db.execute(
    "INSERT INTO manual_payment_reviews (id, payment_order_id, user_id, receipt_reference, status, created_at) VALUES ('test-review', 'test-order', 'test-user', 'receipt-123', 'pending', ?)",
    (now,),
)

credit = 500_000
admin = "test-admin"
wallet_tx = "test-wallet-tx"
subscription = "test-subscription"
grant = "test-grant"
allocation = "test-allocation"

db.execute(
    "UPDATE payment_orders SET status = 'paid', paid_at = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ? AND status = 'pending' AND EXISTS (SELECT 1 FROM users WHERE id = payment_orders.user_id AND status = 'active')",
    (now, admin, now, "test-order"),
)
assert db.execute("SELECT changes()").fetchone()[0] == 1

db.execute(
    "UPDATE users SET wallet_balance_toman = wallet_balance_toman + ? WHERE id = ? AND status = 'active' AND changes() = 1 AND EXISTS (SELECT 1 FROM payment_orders WHERE id = ? AND status = 'paid' AND reviewed_by = ? AND reviewed_at = ?)",
    (credit, "test-user", "test-order", admin, now),
)
assert db.execute("SELECT changes()").fetchone()[0] == 1

db.execute(
    "INSERT INTO wallet_transactions (id, user_id, type, amount_toman, balance_after_toman, description, reference, created_at) SELECT ?, id, 'subscription_credit', ?, wallet_balance_toman, 'EMA subscription credit', ?, ? FROM users WHERE id = ? AND changes() = 1",
    (wallet_tx, credit, "payment:test-order", now, "test-user"),
)
db.execute(
    """INSERT INTO subscriptions
    (id, user_id, plan_id, status, starts_at, expires_at, payment_order_id, created_at, updated_at, paid_amount_toman, ai_credit_granted_toman)
    SELECT ?, po.user_id, po.subscription_plan_id, 'active',
      CASE WHEN COALESCE((SELECT MAX(expires_at) FROM subscriptions WHERE user_id = po.user_id AND status = 'active' AND expires_at > ?), 0) > ?
        THEN (SELECT MAX(expires_at) FROM subscriptions WHERE user_id = po.user_id AND status = 'active' AND expires_at > ?) ELSE ? END,
      (CASE WHEN COALESCE((SELECT MAX(expires_at) FROM subscriptions WHERE user_id = po.user_id AND status = 'active' AND expires_at > ?), 0) > ?
        THEN (SELECT MAX(expires_at) FROM subscriptions WHERE user_id = po.user_id AND status = 'active' AND expires_at > ?) ELSE ? END) + sp.duration_days * 86400,
      po.id, ?, ?, po.amount_toman, ?
    FROM payment_orders po JOIN subscription_plans sp ON sp.id = po.subscription_plan_id
    WHERE po.id = ? AND po.status = 'paid' AND po.purpose = 'subscription'
      AND EXISTS (SELECT 1 FROM wallet_transactions WHERE reference = ?)""",
    (subscription, now, now, now, now, now, now, now, now, now, now, credit, "test-order", "payment:test-order"),
)
db.execute(
    """INSERT INTO subscription_credit_grants
    (id, subscription_id, user_id, gross_price_toman, credit_percent, credit_amount_toman, wallet_transaction_id, created_at)
    SELECT ?, s.id, s.user_id, s.paid_amount_toman, 50, s.ai_credit_granted_toman, ?, ?
    FROM subscriptions s WHERE s.payment_order_id = ? AND s.id = ?""",
    (grant, wallet_tx, now, "test-order", subscription),
)
db.execute(
    """INSERT INTO provider_replenishment_allocations
    (id, payment_order_id, provider, gross_subscription_toman, allocation_percent, allocated_toman, status, subscription_credit_grant_id, created_at, updated_at)
    SELECT ?, po.id, 'netarz', po.amount_toman, 50, ?, 'reserved', ?, ?, ?
    FROM payment_orders po JOIN subscriptions s ON s.payment_order_id = po.id
    WHERE po.id = ? AND po.purpose = 'subscription' AND po.status = 'paid' AND s.id = ?""",
    (allocation, credit, grant, now, now, "test-order", subscription),
)
db.execute(
    "UPDATE manual_payment_reviews SET status = 'approved', reviewed_by = ?, reviewed_at = ? WHERE payment_order_id = ?",
    (admin, now, "test-order"),
)

assert db.execute("SELECT wallet_balance_toman FROM users WHERE id='test-user'").fetchone()[0] == credit
assert db.execute("SELECT COUNT(*) FROM wallet_transactions WHERE reference='payment:test-order'").fetchone()[0] == 1
assert db.execute("SELECT COUNT(*) FROM subscriptions WHERE payment_order_id='test-order'").fetchone()[0] == 1
assert db.execute("SELECT COUNT(*) FROM subscription_credit_grants WHERE subscription_id='test-subscription'").fetchone()[0] == 1
allocation_row = db.execute("SELECT allocated_toman, subscription_credit_grant_id FROM provider_replenishment_allocations WHERE payment_order_id='test-order'").fetchone()
assert allocation_row == (credit, grant)
print("OK one-time 50% subscription credit + provider allocation settlement")



# Exercise prepaid AI reservation, partial refund, and insufficient-balance protection.
reserve_amount = 10_000
db.execute(
    "UPDATE users SET wallet_balance_toman = wallet_balance_toman - ? WHERE id='test-user' AND wallet_balance_toman >= ?",
    (reserve_amount, reserve_amount),
)
assert db.execute("SELECT changes()").fetchone()[0] == 1
db.execute(
    "INSERT INTO wallet_transactions (id,user_id,type,amount_toman,balance_after_toman,description,reference,created_at) SELECT 'chat-reserve-tx',id,'ai_reserve',-?,wallet_balance_toman,'reserve','ai-reserve:test-chat',? FROM users WHERE id='test-user' AND changes()=1",
    (reserve_amount, now),
)
db.execute(
    "INSERT INTO usage_reservations (id,user_id,feature,model,reserved_toman,settled_toman,status,created_at,updated_at) SELECT 'test-chat','test-user','chat','gpt-4o-mini',?,0,'reserved',?,? WHERE EXISTS (SELECT 1 FROM wallet_transactions WHERE reference='ai-reserve:test-chat')",
    (reserve_amount, now, now),
)
refund_amount = 4_000
db.execute(
    "UPDATE users SET wallet_balance_toman = wallet_balance_toman + ? WHERE id='test-user' AND EXISTS (SELECT 1 FROM usage_reservations WHERE id='test-chat' AND status='reserved')",
    (refund_amount,),
)
db.execute(
    "INSERT INTO wallet_transactions (id,user_id,type,amount_toman,balance_after_toman,description,reference,created_at) SELECT 'chat-settle-tx',id,'ai_settlement',?,wallet_balance_toman,'settlement','ai-settlement:test-chat',? FROM users WHERE id='test-user' AND changes()=1",
    (refund_amount, now),
)
db.execute(
    "UPDATE usage_reservations SET status='settled',settled_toman=?,updated_at=? WHERE id='test-chat' AND status='reserved' AND EXISTS (SELECT 1 FROM wallet_transactions WHERE reference='ai-settlement:test-chat')",
    (reserve_amount-refund_amount, now),
)
db.execute(
    "INSERT INTO provider_cost_ledger (id,usage_reservation_id,user_id,provider,provider_cost_microusd,exchange_rate_toman,markup_bps,user_charge_toman,created_at) VALUES ('test-cost','test-chat','test-user','netarz',100000,50000,3000,6000,?)",
    (now,),
)
assert db.execute("SELECT wallet_balance_toman FROM users WHERE id='test-user'").fetchone()[0] == credit-reserve_amount+refund_amount
assert db.execute("SELECT status,settled_toman FROM usage_reservations WHERE id='test-chat'").fetchone() == ("settled", 6000)

# Insufficient credit must never drive the balance negative.
db.execute(
    "UPDATE users SET wallet_balance_toman = wallet_balance_toman - 999999999 WHERE id='test-user' AND wallet_balance_toman >= 999999999"
)
assert db.execute("SELECT changes()").fetchone()[0] == 0
assert db.execute("SELECT wallet_balance_toman FROM users WHERE id='test-user'").fetchone()[0] == credit-reserve_amount+refund_amount
print("OK prepaid AI reserve, settlement refund, and insufficient-balance protection")

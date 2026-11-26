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

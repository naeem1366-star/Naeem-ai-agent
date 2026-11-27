# قرارداد API و امنیت EMA Market Intelligence

## 1. قواعد عمومی

- همه endpointها زیر `/v1/market` هستند.
- احراز هویت با Bearer token فعلی Supabase انجام می‌شود.
- همه پاسخ‌ها JSON و دارای `request_id` هستند.
- قیمت و تحلیل دارای `as_of`، `source_status` و `freshness` هستند.
- پارامترهای pagination و range سقف دارند.
- هر endpoint داده‌ای فقط از canonical instrument ID استفاده می‌کند؛ symbol مبهم پذیرفته نمی‌شود.
- Endpointهای read-only در نسخه اول؛ هیچ route اجرای معامله وجود ندارد.
- درخواست‌های سنگین async هستند و job ID برمی‌گردانند.
- کاربر فقط به watchlist، alert و گزارش‌های خودش دسترسی دارد.

## 2. Public-to-authenticated API map

### Catalog
- `GET /v1/market/overview`: خلاصه شش بازار و کیفیت تازگی هر بازار.
- `GET /v1/market/markets`: فهرست بازارها، ساعات و وضعیت provider.
- `GET /v1/market/instruments?market=&q=&page=`: جست‌وجو و فهرست دارایی‌ها.
- `GET /v1/market/instruments/{id}`: جزئیات دارایی و منبع.

### Market data
- `GET /v1/market/quotes?ids=`: quoteهای آخر.
- `GET /v1/market/candles/{instrumentId}?interval=&from=&to=`: تاریخچه کندلی.
- `GET /v1/market/metrics/{instrumentId}?window=`: شاخص‌های محاسبه‌شده.
- `GET /v1/market/heatmap?market=&universe=`: داده نقشه حرارتی.
- `GET /v1/market/correlations?ids=&window=`: ماتریس همبستگی با timestamp و روش.

### Analysis
- `POST /v1/market/analysis`: ایجاد گزارش تحلیل.
- `GET /v1/market/analysis/{analysisId}`: دریافت نتیجه.
- `POST /v1/market/scenarios`: ایجاد سناریوی What-if.
- `POST /v1/market/backtests`: ایجاد بک‌تست.
- `GET /v1/market/jobs/{jobId}`: وضعیت job و نتیجه/خطا.

### User tools
- `GET /v1/market/watchlists`
- `POST /v1/market/watchlists`
- `PATCH /v1/market/watchlists/{id}`
- `DELETE /v1/market/watchlists/{id}`
- `GET /v1/market/alerts`
- `POST /v1/market/alerts`
- `PATCH /v1/market/alerts/{id}`
- `DELETE /v1/market/alerts/{id}`
- `GET /v1/market/reports`

### Admin / operations
- `GET /v1/admin/market/providers`
- `PATCH /v1/admin/market/providers/{id}`
- `GET /v1/admin/market/data-quality`
- `GET /v1/admin/market/jobs`
- `GET /v1/admin/market/pricing`
- `PATCH /v1/admin/market/pricing/{id}`

Admin routes require existing admin authorization plus explicit feature-level permission. Provider secrets must be Cloudflare Secrets, never D1 plaintext or APK configuration.

## 3. Analysis request contract

Example request:

```json
{
  "scope": {"type": "instrument", "instrument_id": "canonical-id"},
  "horizon": "1d",
  "modules": ["technical", "flows", "regime", "risk"],
  "depth": "professional",
  "include_scenarios": true,
  "idempotency_key": "client-generated-uuid"
}
```

Server flow:
1. Validate identity, feature access, input schema and limits.
2. Resolve canonical instrument and market availability.
3. Verify minimum data quality and history length.
4. Calculate authoritative price estimate / maximum reservation.
5. If paid, reserve wallet funds atomically before queueing work.
6. Create immutable job with input snapshot refs and engine versions.
7. Return `202 Accepted` with job ID and estimate/reservation.
8. Worker runs analysis, stores structured output and usage metadata.
9. Settle measured usage; release unused reservation to original buckets.
10. If provider/model usage cannot be reconciled, retain pending reconciliation state and alert operations. Never silently treat unknown usage as free.

For a free quota operation, reserve quota atomically and do not debit wallet. If quota is exhausted, return a clear upgrade/top-up path without starting paid work.

## 4. Response envelope

```json
{
  "request_id": "opaque-id",
  "data": {},
  "meta": {
    "as_of": "2026-11-27T12:00:00Z",
    "source_status": "partial",
    "freshness": "delayed",
    "warnings": []
  }
}
```

## 5. Error codes

- `MARKET_PROVIDER_UNAVAILABLE`
- `MARKET_DATA_STALE`
- `MARKET_DATA_INSUFFICIENT`
- `INSTRUMENT_NOT_FOUND`
- `AMBIGUOUS_INSTRUMENT`
- `FEATURE_NOT_CONFIGURED`
- `PRICE_NOT_CONFIGURED`
- `INSUFFICIENT_CREDIT`
- `FREE_QUOTA_EXHAUSTED`
- `ANALYSIS_JOB_LIMIT`
- `IDEMPOTENCY_CONFLICT`
- `ANALYSIS_NOT_READY`
- `UNSUPPORTED_MARKET_DATA`

No paid job may start if price, unit, FX conversion or max charge is missing.

## 6. Billing integration

The existing codebase currently exposes a single `wallet_balance_toman` and legacy subscription-oriented UI/API. The approved EMA commercial direction requires purchased and bonus buckets plus source-aware reservations. Market billing must not introduce a parallel wallet.

Required integration contract before paid launch:
- separate purchased/bonus balances and free quotas;
- server-owned per-feature pricing rule and billing unit;
- preflight estimate and hard maximum reservation;
- source split on reservation;
- idempotent settle/release/refund;
- provider cost and customer charge kept distinct;
- no balance mutation from Android;
- metadata-only usage event;
- auditable admin adjustments.

Recommended market billing units:
- `quote_refresh`: free/operational cost, not individually billed by default.
- `analysis_report`: per report, with module complexity tiers.
- `cross_market_report`: per report, bounded by instrument count and history range.
- `scenario_simulation`: per simulation/run and bounded parameter count.
- `backtest_run`: per run with limits on universe, range and compute budget.
- `analyst_arena`: per run and model count.
- `historical_replay`: per replay range and data depth.

Exact prices must remain disabled until provider cost, compute cost, exchange rate and margin policy are approved. Never invent a price in client code.

## 7. Security and abuse controls

- Supabase identity validated server-side.
- User ID derived from token, never trusted from request body.
- Strict CORS allowlist and HTTPS only.
- Schema validation, payload limits, query-range limits and pagination caps.
- Per-user, per-IP and global rate limits.
- Queue concurrency caps and maximum job runtime.
- Provider timeouts, circuit breakers and retry budgets.
- Admin endpoints audited with actor, action, target and reason.
- No provider API keys sent to Android.
- No raw personal chat or media in market logs.
- Avoid exposing provider error payloads, internal URLs or secrets to clients.
- Cache keys include instrument, currency, timeframe, entitlement scope and data version where relevant.

## 8. Operational observability

Track metadata only:
- provider latency, error rate, last successful fetch, rate-limit responses;
- ingestion lag and stale-data ratio;
- job queue age, duration, failure and retry count;
- analysis cost, model usage, estimated margin;
- forecast evaluation status.

Alert on stale critical market, provider outage, queue backlog, pricing disabled, settlement mismatch and unexplained balance delta.

# EMA Core Server — Iran-first Architecture

Status: architecture and repository scaffold. Not production-ready; no production endpoint, Android code, provider secrets, or live Supabase settings are changed.

## Product scope — v1
EMA Core v1 supports all three billable capabilities from the same customer wallet:
- Chat completions (synchronous request/response)
- Image generation (asynchronous job; result stored and returned by job ID)
- Video generation (asynchronous job; provider webhook or polling updates job state)

Each capability has its own pricing rule and usage record. A customer receives an EMA activation code, never a provider API key.

## Current app and migration constraints
- Android package: com.naeem.ai; Kotlin + Jetpack Compose.
- Current chat client calls BuildConfig.CHAT_GATEWAY_URL + /v1/chat/completions and sends the signed-in Supabase access token plus the public Supabase key.
- Current gateway URL is also used by other existing features. Do not globally repoint CHAT_GATEWAY_URL.
- Current Supabase Edge Function includes chat, billing, speech and other routes. Its existing behaviour must remain available during migration.
- Existing source has a direct-provider API-key mode as well as gateway mode. The future customer-credit mode must not expose the central provider key in the APK.

## Target architecture
Android EMA
  -> dedicated EMA_CORE_API_URL (HTTPS)
  -> EMA Core API (Node.js 22 + TypeScript + Fastify)
  -> PostgreSQL (customers, activation codes, wallets, ledger, jobs, provider config)
  -> provider adapters (chat, image, video)
  -> Iranian-accessible AI providers

The API is stateless. PostgreSQL is the source of truth for balances, ledger entries, activation codes and asynchronous jobs. Provider credentials exist only in server environment secrets.

## Customer activation and access
1. Admin creates a one-time, high-entropy activation code and assigns a credit grant, expiry, and optional product limits.
2. Customer enters the code in EMA.
3. Server validates and redeems the code atomically, associates it with the authenticated customer/device policy, and grants credits once.
4. Server issues a revocable EMA access token (or uses an existing verified identity token during the first migration phase).
5. Android sends the EMA token to the dedicated EMA Core endpoint. It never receives upstream provider credentials.

Codes are not API keys and must not be accepted as bearer tokens. Store only a cryptographic hash of each activation code. Never log raw codes or access tokens.

## Wallet and metering
- Use integer credit units; never floating point for balances.
- Record every grant, reservation, capture, refund and manual adjustment in an append-only wallet ledger.
- Reserve estimated credits before expensive work.
- Capture actual cost after provider completion; refund unused reservation.
- Use idempotency keys to prevent duplicate charges on retries.
- Enforce balance checks and reservations in a single database transaction/atomic SQL function.
- Keep per-feature pricing in admin-controlled configuration: chat, image, video.
- Maintain provider cost records separately from customer retail prices.

## API surface
- GET /health — liveness only; must not expose secrets.
- POST /v1/activation/redeem — redeem a one-time activation code.
- GET /v1/account — current customer and wallet summary.
- GET /v1/usage — usage and ledger history.
- POST /v1/chat/completions — authenticated chat; OpenAI-compatible response shape where practical.
- POST /v1/images/generations — create image job; returns job ID.
- POST /v1/videos/generations — create video job; returns job ID.
- GET /v1/jobs/:id — poll image/video job status and result.
- Admin-only routes for codes, credit grants, payment review, pricing, provider health and customer suspension.

## Asynchronous media jobs
Image and video requests create durable jobs in PostgreSQL. A worker claims jobs with a lease, reserves credits, calls the selected provider adapter, stores provider task IDs and polls or receives signed callbacks. Job states: queued, processing, succeeded, failed, cancelled. A retry must be idempotent. Failed jobs refund their reserved credits according to policy. Store generated assets in object storage; do not keep large media blobs in PostgreSQL.

## Provider adapters
- Separate adapters for chat, image and video; never let the Android client select arbitrary provider URLs.
- Configure provider base URL, model, supported features, timeout and cost mapping on the server.
- Primary and fallback providers are configured independently per capability.
- Confirm provider API formats, model IDs, Iran reachability, prices, image limits, video duration/quality, and callback support before enabling a provider.
- Do not assume that a chat provider supports image or video generation just because it supports OpenAI-compatible chat.

## Security baseline
- HTTPS only; strict request-size and timeout limits.
- Verify customer identity on every protected request.
- Rate limit by customer and IP; add daily spend ceilings.
- Never trust customer_id, credit amount, price or provider URL supplied by Android.
- Keep admin authentication separate and require stronger protection.
- Hash activation codes and access tokens at rest where feasible.
- Do not log prompts, uploaded media, bearer tokens, activation codes or provider secrets.
- Validate image/video input type, size and ownership; use signed, expiring asset URLs.
- Add audit logs for all admin balance changes and payment approvals.

## Repository scaffold
- backend/ema-core — isolated Node.js API service scaffold (health endpoint only at this stage).
- docs/EMA_CORE_API.md — API contract draft.
- docs/EMA_CORE_DATA_MODEL.md — proposed persistence model.
- This service is intentionally not wired to the Android APK yet.

## Delivery sequence
1. Repository scaffold and architecture (current step).
2. Implement PostgreSQL schema, migrations and atomic wallet/ledger operations.
3. Implement customer identity, activation-code redemption and admin controls.
4. Implement chat provider adapter and end-to-end tests from Iranian networks.
5. Implement image provider adapter and asynchronous job worker.
6. Implement video provider adapter, durable polling/callback processing and asset storage.
7. Add Android EMA_CORE_API_URL as a separate setting; migrate chat, image and video calls one at a time.
8. Test on multiple Iranian networks, then release a test APK.
9. Keep old Supabase routes intact for rollback until all migrated features are verified.

## Acceptance criteria
- One customer cannot spend another customer's credits.
- Activation code can be redeemed only once.
- Parallel requests cannot overspend the wallet.
- Every job and charge is idempotent and auditable.
- Chat, image and video work through server-side provider secrets only.
- Provider failure does not silently charge a customer.
- Existing login, wallet, TTS, Product Lens and other non-migrated routes continue to work.
- Android release is not cut over until real-device tests pass on Iranian mobile and fixed networks.

## Explicit non-goals for this scaffold
- No production hosting purchase.
- No provider API keys or secrets committed to GitHub.
- No public customer endpoints except /health until authentication, database transactions, rate limiting and abuse controls are implemented.
- No changes to the production APK or existing Supabase configuration.

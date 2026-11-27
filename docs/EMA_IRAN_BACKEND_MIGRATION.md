# EMA Iran Backend Migration Architecture

Status: architecture-only first step. No production endpoint, Android code, secrets, or Supabase configuration is changed by this document.

## Goal

Provide reliable EMA chat for users inside Iran, with rial payment support, no VPN requirement for normal use, server-side provider keys, and a rollback path.

## Current implementation findings

- Android currently sends chat to BuildConfig.CHAT_GATEWAY_URL + /v1/chat/completions.
- The same CHAT_GATEWAY_URL is also used by text-to-speech, Product Lens, and wallet/billing screens. Repointing it globally would break those features unless their routes are migrated too.
- The current Supabase Edge Function verifies the Supabase access token, consumes the existing daily quota through ema_consume_chat_quota, then calls the primary and fallback AI providers.
- ChatClient sends the Supabase publishable key and user access token; provider secrets are not in the Android client.
- Existing auth and wallet/billing depend on Supabase. They should not be silently removed as part of the first chat migration.

## Target architecture — Phase 1 (chat only)

Android EMA
  -> dedicated EMA_CHAT_API_URL
  -> Node.js 22 API service hosted on an Iranian PaaS (initial candidate: Liara)
  -> verify user access token against the existing Supabase Auth user endpoint
  -> consume existing ema_consume_chat_quota RPC
  -> call Iranian AI provider gateway
  -> return OpenAI-compatible Chat Completions response

The existing CHAT_GATEWAY_URL remains unchanged for billing, TTS, and Product Lens until each route is migrated and tested independently.

### Hosting

Initial candidate: Liara PaaS with Node.js runtime. Start with a small test instance; select the paid resource size only after a load and latency test. Keep the deployment stateless so it can be moved to another Iranian provider if needed.

### AI providers

Primary:
- Base URL: https://ir.gisoo.pro/api/v1
- Provider: NetArz / Gisoo Iran endpoint
- Route: POST /chat/completions
- API key: server environment secret only

Fallback candidate:
- Base URL: https://api.hooshi.ai/v1
- Route: POST /chat/completions
- API key: server environment secret only

Provider model IDs must be checked against each account's live model catalog before deployment. Do not assume that the current gpt-4o-mini identifier is enabled on every account.

### Backend routes

- GET /health — liveness only; no secrets or internal configuration in response.
- POST /v1/chat/completions — authenticated chat endpoint, compatible with the Android response parser.
- Internal provider adapter — strict allowlist; the client cannot choose arbitrary provider URLs or inject API keys.

### Security and abuse controls

- Never put AI provider keys or Supabase service-role keys in the APK.
- Verify the bearer token server-side; do not trust a user ID supplied in the request body.
- Keep quota enforcement atomic. Initially reuse the existing Supabase quota RPC; later move quota storage to the domestic database if Supabase connectivity is not reliable from Iranian networks.
- Enforce request-size, message-count, prompt-length, per-user rate, and daily spending limits.
- Do not log prompts, images, bearer tokens, or provider keys.
- Use HTTPS only, rotate secrets, and return generic provider errors to clients.

## Phase 2 — domesticize identity and data

If tests show Supabase Auth/REST is unreliable for users in Iran, migrate identity, profiles, quota, wallet, subscriptions, and payment records to a domestic database and auth service. This is a separate migration with explicit schema mapping and user-account migration; do not combine it with the first chat cutover.

## Migration sequence

1. Create and test the Iranian Node.js gateway without touching the live APK.
2. Test health and chat from at least two Iranian mobile/fixed networks.
3. Test valid/expired tokens, quota exhaustion, primary failure, fallback success, and timeout handling.
4. Add a separate Android BuildConfig value, EMA_CHAT_API_URL, and change only the chat call to use it.
5. Keep CHAT_GATEWAY_URL for billing, TTS, and Product Lens.
6. Build a test APK, verify chat and all existing non-chat modules, then release.
7. Keep the old Supabase chat route available for rollback until the new route has passed production observation.

## Acceptance criteria

- Chat works from Iran without VPN on at least two independent networks.
- Typical text response starts within an agreed latency target.
- Primary-to-fallback routing works.
- No provider secret appears in APK, logs, or client responses.
- Daily quota remains atomic and cannot be bypassed by parallel requests.
- Existing login, wallet, billing, TTS, and Product Lens continue to work.
- Rollback can be performed by restoring the previous chat endpoint configuration.

## Open operational checks

- Confirm Liara account access and the actual deployed region/runtime plan.
- Confirm outbound HTTPS connectivity from Liara to ir.gisoo.pro and api.hooshi.ai.
- Confirm whether the existing Supabase Auth and REST endpoints are reachable from Liara and from target Iranian user networks.
- Confirm provider model availability and current prices before enabling fallback.

# Supabase deployment — EMA secure gateway

## Project

Supabase project reference: `ifloyzpvgjgmneqgcggu`

The Android app's secure chat, speech, product-vision, and billing requests use:

`https://ifloyzpvgjgmneqgcggu.supabase.co/functions/v1/smooth-service`

Chat endpoint: `POST /v1/chat/completions`

The older `ema-api` function URL is obsolete for the current Android client.

## Provider routing

- **Free text chat:** NetArz primary; GapGPT is used only as a transient-outage fallback.
- **Paid chat:** NetArz primary; GapGPT fallback is attempted only when the fallback model has an enabled price rule, so the reservation can safely cover either provider.
- Provider usage and actual provider costs are recorded against the provider that answered.
- **No provider API key is stored in the Android APK.**

## Edge Function secrets

Supabase injects the project environment variables into Edge Functions:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY` or `SUPABASE_PUBLISHABLE_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`

Do not create custom secrets with the reserved `SUPABASE_` prefix. Never expose the service-role key in the APK or GitHub.

Create these provider secrets in Supabase Dashboard → Edge Functions → Secrets:

- `CHAT_SECONDARY_API_KEY` — GapGPT key; required for free chat.
- `CHAT_PRIMARY_API_KEY` — NetArz key; used by provider-backed routes other than free chat.

Compatibility with the names already used in the dashboard is retained:
- `CHAT_SECONDARY_APLIKEY` (legacy misspelling) is accepted as a fallback for the GapGPT key.
- `Netarz` is accepted as a fallback for the NetArz key.

The corrected names above are preferred. The function never returns or logs secret values.

Optional settings:

- `CHAT_SECONDARY_BASE_URL` — default `https://api.gapgpt.app/v1`
- `CHAT_FREE_MODEL` — default `gpt-4o-mini`
- `CHAT_SECONDARY_MODEL` — fallback model setting if `CHAT_FREE_MODEL` is not set
- `CHAT_PRIMARY_BASE_URL` — default `https://netarz.ir/api/ai/v1`
- `NETARZ_API_KEY` — dedicated NetArz media API key (recommended; falls back to legacy `Netarz` / `CHAT_PRIMARY_API_KEY`).
- `NETARZ_BASE_URL` — dedicated NetArz media base URL; default `https://netarz.ir/api/ai/v1`. Keep this separate from GapGPT/chat provider URLs.
- `CHAT_PRIMARY_MODEL` — default `gpt-4o-mini`

## Free chat behavior

- A signed-in user is required by the current alpha34 client and gateway. Guest chat is not implemented yet.
- The function validates the Supabase user session before calling the provider.
- The current free quota is 50 successful-or-attempted requests per UTC day; a provider failure triggers a best-effort quota refund.
- Migration `0002_ema_chat_quota.sql` creates the atomic quota counter.
- Migration `0004_ema_chat_quota_refund.sql` creates the refund RPC used after provider/network errors.
- Free chat calls GapGPT only. HTTP/provider failures return a controlled error and do not fall back to NetArz.
- Image payloads are rejected by the chat route until image billing and entitlement checks are implemented.

## Apply the quota refund SQL

The repository currently contains two migration files with the same `0003` version prefix. Until the existing remote migration history is reconciled, do not blindly run the full `supabase db push` sequence.

For this focused chat fix, open Supabase Dashboard → SQL Editor and run the contents of:

`supabase/migrations/0004_ema_chat_quota_refund.sql`

The function is safe to create with `CREATE OR REPLACE`. Do not change or delete existing remote migration history as part of this chat fix.

## Deploy the Edge Function

The function source is `supabase/functions/smooth-service/index.ts`.

Option A — from a machine with Supabase CLI authenticated:

```sh
supabase functions deploy smooth-service --project-ref ifloyzpvgjgmneqgcggu
```

Option B — GitHub Actions:
1. Add a GitHub repository Actions secret named `SUPABASE_ACCESS_TOKEN`.
2. Open Actions → `Deploy EMA Supabase gateway` → Run workflow.
3. The workflow deploys only the Edge Function. It does not change database migrations or provider secrets.

After deployment, send a test message from a signed-in alpha34 app and inspect Supabase Edge Function logs. A successful Android APK build does not prove that the Edge Function is deployed.

## Known boundaries — do not treat as finished

- The existing Supabase Edge Function is the active gateway. The separate Cloudflare Worker in `backend/auth-worker` is not deployed; its D1 database ID is still a placeholder. Do not route the app to that Worker yet.
- The Cloudflare Worker includes a separate billing/wallet implementation, but it is not the live backend.
- Paid image/video/music operations are not yet fully proxied through this Supabase gateway with server-side credit reservation and settlement. Do not expose provider keys in the client or claim paid media billing is production-ready until those routes are implemented and tested.
- Guest access is specified in the product plan but is not yet supported by the current authenticated chat path.

## Credit wallet rollout

The Android wallet displays and consumes **credits**, not toman. Toman is shown only as the payment currency when the user creates a manual top-up order.

- Migration `0009_ema_credit_wallet.sql` introduces the credit wallet, ledger, reservation/settlement RPCs, and the one-time 10,000-credit signup gift.
- Migration `0010_ema_credit_wallet_reconcile.sql` is an idempotent reconciliation migration. Use it when the original credit migration was skipped on the target database; it also reattaches the Auth signup trigger.
- New accounts receive exactly 10,000 gift credits through a unique ledger reference. Existing accounts are not automatically gifted by the migration.
- Paid AI requests reserve credits before calling the provider, settle against the actual responding provider's price rule, and release unused reservations or failed requests.
- The minimum manual top-up payment is 500,000 toman. Credit purchases remain disabled until an administrator configures a positive `credits_per_toman` rate in `ema_billing_settings`. Do not guess or silently set this conversion rate.
- The Android screen explains when purchases are not yet enabled and prevents users from creating an order that cannot be credited.

### Apply the credit wallet migration

Do not run the migration against production until the owner explicitly approves the production database change. First review and test `supabase/migrations/0010_ema_credit_wallet_reconcile.sql` in a non-production database. The GitHub Actions `Supabase Edge Function checks` workflow applies it to an ephemeral PostgreSQL database and verifies signup grant, reserve, settle, and release behavior.

After approval, apply `0010_ema_credit_wallet_reconcile.sql` to the intended Supabase project using the SQL Editor. Then verify that `ema_credit_wallets`, `ema_credit_ledger`, `ema_credit_reservations` exist and that `ema_billing_settings.credits_per_toman` is present. Configure the conversion rate only after pricing is reviewed. Do not expose paid top-ups until that rate and model prices are approved.

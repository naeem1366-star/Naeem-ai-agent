# Supabase deployment — EMA secure gateway

## Project

Supabase project reference: `ifloyzpvgjgmneqgcggu`

The Android app's secure chat, speech, product-vision, and billing requests use:

`https://ifloyzpvgjgmneqgcggu.supabase.co/functions/v1/smooth-service`

Chat endpoint: `POST /v1/chat/completions`

The older `ema-api` function URL is obsolete for the current Android client.

## Provider routing

- **Free text chat:** GapGPT only. The free-chat route never falls back to NetArz, so free usage cannot unexpectedly spend the paid-provider balance.
- **Paid/provider-backed operations:** NetArz remains configured server-side for the premium routes as those routes are connected to the secure gateway and credit ledger.
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

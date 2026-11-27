# Supabase deployment — EMA ASI secure gateway

## Project

Supabase project reference: `ifloyzpvgjgmneqgcggu`

The Android app's secure chat, speech, product-vision, and billing requests use this gateway:

`https://ifloyzpvgjgmneqgcggu.supabase.co/functions/v1/smooth-service`

Chat endpoint:

`POST /v1/chat/completions`

The older `ema-api` function URL is obsolete for the current Android client.

## Edge Function environment and secrets

Supabase injects the project environment variables into Edge Functions. The function reads:

- `SUPABASE_URL` — project URL (Supabase-provided)
- `SUPABASE_ANON_KEY` or `SUPABASE_PUBLISHABLE_KEY` — public client key used alongside the user's access token
- `SUPABASE_SERVICE_ROLE_KEY` — privileged server key used for quota and database operations

Do **not** try to create custom secrets with the reserved `SUPABASE_` prefix. Never expose the service-role key in the APK or GitHub.

Create only the provider secrets in Supabase Dashboard → Edge Functions → Secrets:

- `CHAT_PRIMARY_API_KEY` — NetArz API key (required for text chat)
- `CHAT_SECONDARY_API_KEY` — GapGPT API key (optional fallback)

Optional provider settings:

- `CHAT_PRIMARY_BASE_URL` (default: `https://netarz.ir/api/ai/v1`)
- `CHAT_SECONDARY_BASE_URL` (default: `https://api.gapgpt.app/v1`)
- `CHAT_PRIMARY_MODEL` (default: `gpt-4o-mini`)
- `CHAT_SECONDARY_MODEL` (defaults to the primary model)

Never commit provider keys or the service-role key to GitHub or include them in the APK.

## Deploy

From the repository root, with Supabase CLI authenticated and linked to this project:

```sh
supabase link --project-ref ifloyzpvgjgmneqgcggu
supabase db push
supabase functions deploy smooth-service
```

Deploying the Edge Function and applying migrations are separate from building the Android APK. A successful GitHub Actions APK build does not prove that the Supabase function has been deployed.

## Chat requirements and behavior

- The user must sign in; guest mode has no Supabase access token and cannot use secure gateway chat.
- The function validates the Supabase user session before processing requests.
- The chat quota RPC `ema_consume_chat_quota` is created by migration `0002_ema_chat_quota.sql`.
- The current function uses a daily limit of 50 text requests per user.
- The function tries NetArz first and GapGPT second when configured.
- Image payloads are currently rejected by the chat route until image pricing and entitlements are implemented.

After deployment, test with a signed-in account and inspect Supabase Edge Function logs if the client receives an HTTP error. Do not disable authentication to work around a gateway failure.

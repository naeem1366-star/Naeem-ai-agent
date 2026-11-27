# Supabase deployment — EMA ASI secure gateway

## Project

Supabase project reference: `ifloyzpvgjgmneqgcggu`

The Android app's secure chat, speech, product-vision, and billing requests use this gateway:

`https://ifloyzpvgjgmneqgcggu.supabase.co/functions/v1/smooth-service`

Chat endpoint:

`POST /v1/chat/completions`

The older `ema-api` function URL is obsolete for the current Android client.

## Required Edge Function secrets

Set these in Supabase Dashboard → Edge Functions → Secrets:

- `SUPABASE_URL` — project URL (normally provided by Supabase automatically)
- `SUPABASE_ANON_KEY` or `SUPABASE_PUBLISHABLE_KEY` — used to validate the user's access token
- `SUPABASE_SERVICE_ROLE_KEY` — required for quota and privileged database operations; never expose it in the app
- `CHAT_PRIMARY_API_KEY` — NetArz API key
- `CHAT_SECONDARY_API_KEY` — GapGPT API key (fallback)

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

# Supabase deployment — EMA chat gateway

The Android client calls:
`https://ifloyzpvgjgmneqgcggu.supabase.co/functions/v1/ema-api`

## Required Function Secrets

Set these in Supabase Dashboard → Edge Functions → Secrets:

- `CHAT_PRIMARY_API_KEY` — NetArz API key
- `CHAT_SECONDARY_API_KEY` — GapGPT API key
- Optional `CHAT_PRIMARY_BASE_URL` (defaults to `https://netarz.ir/api/ai/v1`)
- Optional `CHAT_SECONDARY_BASE_URL` (defaults to `https://api.gapgpt.app/v1`)
- Optional `CHAT_PRIMARY_MODEL` (defaults to `gpt-4o-mini`)
- Optional `CHAT_SECONDARY_MODEL` (defaults to the primary model)

Never commit provider keys to GitHub or include them in the APK.

## Deploy once

From the repository root, with Supabase CLI authenticated and linked to project `ifloyzpvgjgmneqgcggu`:

```sh
supabase db push
supabase functions deploy ema-api
```

The migration creates an atomic per-user free-chat quota (50 text requests per UTC day). The Edge Function validates the Supabase user session, tries NetArz first, and falls back to GapGPT when the primary provider is unavailable. Image requests are intentionally rejected until image pricing and entitlements are implemented.

Guest mode currently has no Supabase access token, so secure gateway chat requires signing in. Do not disable authentication to work around this.

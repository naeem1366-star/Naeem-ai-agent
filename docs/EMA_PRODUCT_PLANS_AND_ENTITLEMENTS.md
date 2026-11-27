# EMA Product Plans and Entitlements — Iran-first v1

Status: product requirements for EMA Core. This document supersedes any assumption that all AI capabilities are paid or that every customer must buy credits.

## Product principle

EMA is a free-first application. The ordinary app experience remains free. Payment is required only for premium image generation and professional video generation. Do not put a paywall in front of ordinary app modules.

## Entry and identity

- Users may enter as guests or sign in.
- Guest users receive a temporary guest identity/session and can use free entitlements without entering an activation code or provider API key.
- Signed-in users receive the same free entitlements, plus account-based sync and continuity across supported devices.
- When a guest signs in, preserve and safely associate eligible guest data and free usage history according to the account merge policy.
- Do not require payment details for guest entry or ordinary free features.
- Apply reasonable anti-abuse limits to guest sessions; limits must not turn the free plan into a paid-only experience.

## Free plan

Available to both guests and signed-in users:
- Ordinary EMA application modules and non-AI features.
- AI chat with a defined free quota/rate limit.
- Basic/free video creation using explicitly designated low-cost models, templates, duration, resolution, queue priority, and daily limits.
- Account, settings, and ordinary data-management features where login is required for sync.

Image generation is not included in the free plan.

## Paid capabilities

Credits or a paid entitlement are required only for:
- AI image generation.
- Professional/premium video generation, including premium models, higher quality, longer duration, advanced controls, or priority processing.

Do not charge credits for ordinary app modules or free chat. Basic video usage must not silently consume the customer's paid wallet. If a free quota is exhausted, explain the limit and its reset time; offer a premium upgrade only for premium video features, not as a condition to continue using the rest of EMA.

## Entitlement and billing model

Use feature entitlements, not a single global paywall:
- `chat.free`: available on Free; enforce configured quota/rate limits.
- `video.basic`: available on Free; enforce separate free quota and supported settings.
- `image.generate`: paid entitlement/credits only.
- `video.pro`: paid entitlement/credits only.
- Other ordinary app features: free unless a future, explicit product decision changes them.

Maintain separate accounting counters for free chat and free basic-video quotas. Maintain a paid credit wallet for image and professional video. The backend is authoritative for plan, quota, prices, and balances; Android must never decide or submit the authoritative price.

For premium operations:
1. Authenticate guest/account session.
2. Check feature entitlement and paid balance.
3. Atomically reserve credits before calling a provider.
4. Create a durable image/video job with an idempotency key.
5. Capture actual charge on success and release/refund reserved credits on provider failure or unused reservation.
6. Show job status and resulting asset only to its owner.

For free operations:
- Enforce the free quota on the server, independently from paid credits.
- Never debit the paid wallet for free chat or basic video.
- When the free quota is exhausted, return a clear machine-readable quota response with reset metadata.

## Suggested product configuration

All values are examples and must be configured by the EMA operator after measuring provider costs:
- Free chat: daily request/token budget.
- Basic video: daily generation count, short duration, standard resolution, allowed free model(s).
- Image generation: paid credits based on model, resolution, and output count.
- Professional video: paid credits based on model, duration, resolution, and options.

Do not hard-code example quotas or prices in the Android app.

## API and backend implications

Add server-side endpoints or equivalent capabilities for:
- Guest session creation and refresh.
- Account sign-in/token verification and guest-to-account migration.
- `GET /v1/me/entitlements` — effective features, quotas, and reset metadata.
- Free chat endpoint with quota enforcement but no paid-wallet debit.
- Basic video endpoint with free-quota enforcement and asynchronous job tracking.
- Paid image generation endpoint with paid-credit reservation.
- Paid professional-video endpoint with paid-credit reservation.
- Account/wallet/usage endpoints showing free quota separately from paid credits.
- Admin controls to configure free quotas, free-video capabilities, paid feature prices, and emergency feature disablement.

The Android app should ask the server for effective entitlements and render feature availability accordingly. Hiding a button in Android is not security; every request must be authorized and metered on the server.

## Security and operational rules

- Never embed provider API keys in APK, resources, BuildConfig, activation codes, or client-visible configuration.
- A customer activation code is for account/credit redemption only; it is not a provider API key or a bearer token.
- Guest sessions need abuse controls, rate limits, and a safe migration path to registered accounts.
- Keep free quota accounting separate from the paid wallet ledger.
- Do not make all of Supabase or all of EMA dependent on the new paid-credit gateway. Migrate feature routes independently and retain existing working modules during rollout.
- Test guest, signed-in, free-quota-exhausted, paid-image, free-basic-video, and paid-pro-video flows before public release.

## Acceptance criteria

1. A guest can enter EMA and use ordinary free features without payment or a provider key.
2. A signed-in user can use the same free features.
3. Free chat works within its server-enforced free quota and does not reduce paid credits.
4. Basic video works within its separate free quota and does not reduce paid credits.
5. Image generation is blocked until the user has the required paid entitlement/credits.
6. Professional video is blocked until the user has the required paid entitlement/credits.
7. Failed premium jobs do not leave an incorrect debit.
8. A user cannot bypass server-side entitlements by modifying the Android client.

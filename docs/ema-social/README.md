# EMA Social & Collaboration

Product module: **EMA Social** — Connecting Minds. Creating Futures.

This is an additive Android + Supabase Edge Function module. It does not replace EMA chat, memory, wallet, billing, or media features.

## Implemented in this branch

- Android entry point in the navigation drawer.
- Android client for the dedicated `ema-social` Edge Function.
- Profile editor with explicit discoverability, visibility, and matching controls.
- Discover public profiles and an initial explainable rule-based Match endpoint.
- Connection request creation, inbox listing, accept/decline/cancel API.
- Social schema foundation for teams, projects, tasks, opportunities, blocks, reports, audit, and consent.
- Owner/admin-only global social kill switch in the EMA Social screen.
- Server checks the global switch on every social operation. The status and admin-control routes remain available while disabled.
- The switch defaults to OFF when the migration first creates the control row; this prevents an incomplete module from becoming public accidentally.

## Important boundaries

- No private chat history is read or ingested into social profiles.
- User profile data is entered/edited by the user. Discoverability and matching are opt-in.
- Match scores are heuristic estimates, not predictions or guarantees.
- The initial API does not yet implement messaging, voice/video calls, team/project/task CRUD, opportunity publishing, report review, or S3 uploads. The schema prepares the core data entities; those endpoints and user flows remain follow-on implementation work.
- The current Match algorithm is a first-pass deterministic overlap scorer, not an embedding/pgvector model.
- The admin dashboard currently exposes the global switch, not a complete moderation console.

## Architecture

- Android: existing Kotlin + Jetpack Compose application.
- Auth: existing Supabase Auth access token.
- API: dedicated Supabase Edge Function `ema-social`.
- Data: PostgreSQL tables in `supabase/migrations/0008_ema_social_platform.sql`.
- All social table access is mediated by the Edge Function using the service role; direct anon/authenticated PostgREST table access is revoked and RLS remains enabled.
- Admin authorization is read from `public.ema_admin_users`, never from user-editable metadata.
- Feature state is stored in `public.ema_feature_controls` and checked server-side on each social API operation.

## Global kill switch semantics

1. Only a user present in `ema_admin_users` can read/change the admin control endpoint.
2. Turning it OFF updates `ema_feature_controls.social_network.enabled=false`.
3. Every non-admin social route checks the flag before doing work and returns HTTP 503 with the configured maintenance message while OFF.
4. `/v1/social/status` and `/v1/social/admin/control` remain available so the app can render the disabled state and an administrator can turn the module back ON.
5. Existing social data is retained. The switch is reversible and does not delete accounts, profiles, requests, messages, or projects.
6. It is scoped to EMA Social only; it does not stop the rest of EMA.

## Deployment gates

This branch intentionally does **not** apply the migration or deploy the Edge Function to production. The owner must approve those production changes after reviewing the diff. Until both are deployed, the Android module will report a backend connection/configuration error rather than pretend that the service is live.

Before enabling the switch:
- Apply migration 0008.
- Deploy `supabase/functions/ema-social/index.ts` as function slug `ema-social`.
- Verify function secrets include the same Supabase URL, publishable/anon key, and service-role key used by `smooth-service`.
- Ensure the owner account is present in `ema_admin_users`.
- Run API smoke tests with a normal user and admin, including the OFF/ON cycle.
- Run Security Advisor and verify the social tables have RLS enabled and no client grants.
- Build and install the APK on a physical Android device.

## API

See [api-contract.md](./api-contract.md).

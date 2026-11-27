# EMA Core Server

Isolated Node.js 22 + TypeScript + Fastify service for the Iran-first EMA customer-credit platform.

## Current status

This is a deliberately small, safe scaffold. It exposes only `GET /health`. Activation, wallet, chat, image and video endpoints are documented but are not implemented yet. Do not expose this scaffold as a production API.

## Local run

1. Install Node.js 22 or newer.
2. Copy `.env.example` to `.env`.
3. Run `npm install`.
4. Run `npm run dev`.
5. Open `http://127.0.0.1:8787/health`.

## Planned modules

- Identity and activation-code redemption
- Wallet, append-only ledger and idempotent credit reservation/capture/refund
- Chat provider adapter
- Image generation jobs
- Video generation jobs and worker
- Admin API and audit log
- Object storage for generated media

## Security rule

Provider API keys, database credentials and signing secrets belong only in deployment environment secrets. Never put them in Android resources, BuildConfig, source control, logs or customer activation codes.

See:
- `docs/EMA_IRAN_BACKEND_MIGRATION.md`
- `docs/EMA_CORE_API.md`
- `docs/EMA_CORE_DATA_MODEL.md`

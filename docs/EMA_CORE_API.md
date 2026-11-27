# EMA Core API — Draft Contract

All endpoints except `GET /health` require a verified EMA customer access token. The current repository scaffold does not implement these routes yet.

## Activation

### POST /v1/activation/redeem
Request:
```json
{ "activationCode": "EMA-XXXX-XXXX" }
```
Response:
```json
{ "ok": true, "creditsGranted": 5000, "walletBalance": 5000, "accessToken": "<issued-once>" }
```
The code is single-use, high entropy, stored as a hash, and redeemed atomically. Do not use the activation code itself as a bearer token.

## Account and wallet

- `GET /v1/account` — customer profile and wallet summary.
- `GET /v1/usage?cursor=...` — paginated ledger and usage history.

## Chat

### POST /v1/chat/completions
Accepts a constrained OpenAI-compatible chat payload. Server selects the provider and model from trusted configuration; the client cannot supply provider URLs or API keys.

## Image generation

### POST /v1/images/generations
Creates a durable job and returns `202 Accepted`:
```json
{ "jobId": "uuid", "status": "queued" }
```

## Video generation

### POST /v1/videos/generations
Creates a durable video job and returns `202 Accepted`. Request limits and supported duration/resolution are controlled by server-side product configuration.

## Job status

### GET /v1/jobs/:id
Returns `queued | processing | succeeded | failed | cancelled`, progress when available, and an expiring result URL only when the requesting customer owns the job.

## Admin (separate authorization)

- Create/revoke activation codes and assign credit grants.
- Review manual payments.
- Adjust wallet only through audited ledger operations.
- Configure per-feature retail pricing and provider cost mappings.
- Suspend customers and inspect aggregate usage.

## Billing invariants

- Client never supplies authoritative customer ID, price, credit amount or provider URL.
- Reserve credits atomically before work starts.
- Use idempotency keys for every billable operation.
- Capture actual charge or refund reservation after completion/failure.
- Never debit a customer for a provider failure unless a clearly disclosed provider-specific policy says otherwise.

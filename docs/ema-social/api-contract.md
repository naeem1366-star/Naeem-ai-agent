# EMA Social API contract v1

Base URL: `https://<project>.supabase.co/functions/v1/ema-social`

All routes require `Authorization: Bearer <Supabase access token>` and the project's publishable key in `apikey`. No service-role key is ever shipped in Android.

## Global status and owner control

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/v1/social/status` | Authenticated | Read global enabled state and maintenance message |
| GET | `/v1/social/admin/control` | EMA admin | Read owner control state |
| PATCH | `/v1/social/admin/control` | EMA admin | Set `enabled` and maintenance message |

PATCH body:

```json
{ "enabled": false, "maintenanceMessage": "این بخش موقتاً توسط مدیریت غیرفعال شده است." }
```

The feature flag is enforced server-side for every other social route. OFF returns HTTP 503 and does not delete data.

## Profile

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/social/profile` | Read current user's profile and consent |
| PUT | `/v1/social/profile` | Create/update current user's profile |

Supported fields include displayName, headline, bio, city, languages, interests, skills, goals, experienceSummary, collaborationModes, availability, visibility, discoverable, aiMatchingConsent, profileProcessingConsent, semanticMatchingConsent.

Visibility: `private`, `connections`, `public`.
Availability: `not_set`, `open`, `limited`, `unavailable`.

## Discovery and matching

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/social/discover` | List discoverable public profiles, excluding own and blocked users |
| GET | `/v1/social/matches` | Return consent-gated explainable heuristic matches |

The first match implementation uses shared interests/goals and collaboration tags. It is not a vector search and must not be presented as a guarantee.

## Connections

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/social/connections/requests` | List incoming/outgoing requests |
| POST | `/v1/social/connections/requests` | Send a request |
| PATCH | `/v1/social/connections/requests/{uuid}` | Accept, decline, or cancel |

POST body: `{ "recipientUserId": "<uuid>", "message": "..." }`
PATCH body: `{ "decision": "accepted|declined|cancelled" }`

## Error semantics

- 401: missing/invalid Supabase session.
- 403: admin-only action or forbidden request transition.
- 404: unknown route, request, or non-discoverable profile.
- 503: social module globally disabled or server configuration unavailable.
- 400: invalid body/field.

All user-facing error messages are Persian-first. API responses must not reveal service keys, SQL, stack traces, or private profile data.

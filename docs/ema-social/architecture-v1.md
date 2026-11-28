# EMA v1 architecture and Phase 1 implementation specification
Status: proposed implementation specification based on owner-approved direction
Date: 2026-11-28

## 1. Architecture decisions

### Keep now
- Android client: existing Kotlin + Jetpack Compose application.
- Identity: existing Supabase Auth; Android sends the current user access token.
- API boundary: Supabase Edge Functions, with domain-oriented modules/routes. Social database access remains server-mediated.
- Persistence: existing PostgreSQL project and migration discipline.
- Admin: existing EMA admin identity table, with explicit role checks in trusted server code.
- Feature safety: server-side global kill switch remains default-OFF. Client visibility is never the security boundary.

### Defer
- Do not replace Supabase with FastAPI in this phase.
- Do not introduce a second production database or move production data to Iran in this phase.
- Do not add pgvector until the profile/consent contract and baseline matching tests are stable.
- Do not add Redis until measured rate-limit, queue, or cache requirements justify operating it.
- Do not select OpenIM Android SDK until AGPL/commercial licensing is resolved.
- Do not deploy LiveKit until a separate proof of concept validates Iranian network paths, TURN, cost, and device behavior.

### Hybrid hosting direction
1. Keep the current EU Supabase project as the existing alpha/development backend.
2. Separately evaluate Iranian VPS/cloud providers using written quotes and real mobile-network tests.
3. Before migration, decide data residency and which data is authoritative; define backup encryption, off-site backup, restore drills, DNS/TLS, observability, and rollback.
4. Move only after a staged non-production deployment and verified export/import/restore. Do not silently dual-write user data.

## 2. Logical modules
- Identity & Consent: authentication context, profile ownership, privacy preferences, consent history.
- Profile: user-managed professional/social profile and visibility.
- Discover: public and connection-visible profile retrieval with block filtering.
- Intelligence: deterministic explainable matching first; embeddings later behind a replaceable interface.
- Connections: request lifecycle and accepted connection graph.
- Safety & Moderation: blocks, reports, moderation status, audit.
- Collaboration (later): teams, members, projects, tasks.
- Opportunities (later): jobs, cofounder, project, mentor, investment, consulting.
- Communication (later): messaging and RTC behind adapters; vendor tokens are issued only by trusted backend.

Keep these as logical modules in the existing Edge Function/Postgres architecture initially. Split into separate deployable services only when operational scale or ownership requires it.

## 3. Phase 1 scope — profile, consent, privacy foundation

### Profile contract
Preserve current fields and complete their Android support:
- displayName, headline, bio, city
- languages[]
- interests[], skills[], goals[]
- experienceSummary
- collaborationModes[]
- availability: not_set | open | limited | unavailable
- visibility: private | connections | public
- discoverable
- aiMatchingConsent

Recommended additive profile fields for a later approved migration:
- collaborationNeeds[]: explicitly declared skills/resources the user is seeking
- collaborationStyle[]: declared working preferences (for example async, structured, exploratory)
Do not infer these from private chats.

### Consent semantics
Keep consent independent and explicit:
- profileProcessingConsent: permission for the specific profile-processing features described in the consent screen.
- semanticMatchingConsent: permission to include the profile in semantic/AI matching.
- aiMatchingConsent: existing user-facing matching toggle; reconcile this with semanticMatchingConsent so the UI does not present two ambiguous switches. The server must require both the relevant feature toggle and semantic consent for AI matching until the contract is simplified.
- discoverable: a separate visibility/discovery setting, not a substitute for consent.
Store consent version and timestamps. Revocation must take effect on the next request; do not rely on cached permission state.

Private conversations, voice calls, contact lists, and uploaded private files are never a source for public profile or matching by default.

### Atomic persistence
Replace the two independent profile/consent upserts with one trusted Postgres RPC or equivalent transaction. It must:
- bind all writes to the authenticated user ID supplied by the Edge Function, never a client-provided owner ID;
- validate allowed enum values and length/cardinality bounds;
- update profile and consent atomically;
- return the persisted profile and consent state;
- write a minimal audit event without copying sensitive profile text into logs.

### Visibility rules
- private: only the owner and explicitly authorized administrators can view.
- connections: owner and accepted connections can view; pending requests do not grant access.
- public: visible to authenticated EMA users when discoverable is true.
- discoverable=false: excluded from Discover and Matches even if visibility=public.
- Blocks override all visibility and connection relationships in both directions.
- Profile detail must apply the same rules as list endpoints; never trust a client-side filter.

### Phase 1 API delta
Existing routes remain compatible where practical:
- GET /v1/social/profile
- PUT /v1/social/profile (atomic profile + consent transaction)
- GET /v1/social/profiles/{userId} (visibility-checked detail)
- DELETE /v1/social/profile (remove the user's social profile and related social state according to documented retention rules; does not delete the EMA auth account)
- GET /v1/social/profile/export (portable JSON export of the user's social data)
- GET /v1/social/blocks
- POST /v1/social/blocks
- DELETE /v1/social/blocks/{userId}
- POST /v1/social/reports
- GET /v1/social/admin/reports (moderator/admin only)
- PATCH /v1/social/admin/reports/{reportId} (moderator/admin only)

List endpoints should use cursor pagination, bounded page sizes, stable ordering, and a documented cursor format. Return only fields needed by the screen.

### Safety and API hardening
- Set a request body limit for every social write route.
- Add per-user rate limits for profile writes, connection requests, reports, and discovery/matching reads.
- Add bounded timeouts to all upstream Supabase/PostgREST calls.
- Use explicit allowed origins for browser surfaces; document native Android behavior. Do not treat CORS as authentication.
- Normalize and validate arrays (max count, max item length, trim, case-insensitive duplicate handling).
- Avoid exposing database error text or SQL details to clients.
- Keep service-role key exclusively in Edge Function secrets.
- Keep RLS enabled and revoke anon/authenticated table grants; add a security test that verifies this for every new table.
- Review Android auth token storage fallback: do not silently downgrade to unencrypted preferences when secure storage is unavailable without an explicit threat-model decision.

## 4. Phase 2 — matching (after Phase 1)
First deliver a deterministic, tested scorer using explicit user-declared data:
- Similar Minds: interests, goals, and declared professional domains.
- Complementary Minds: candidate skills intersect current user's collaborationNeeds, and current user's skills intersect candidate's collaborationNeeds.
- New Perspectives: deliberately different but relevant declared domains/goals; require evidence and a clear reason.
- Consider availability and collaboration style as eligibility/fit modifiers, not hidden personal profiling.
- No arbitrary baseline score. No evidence means no match.
- Explain each score with a bounded list of concrete reasons and show an experimental-estimate disclaimer.
- Require the user's matching consent and candidate's matching consent; re-check blocks and visibility at response time.
- Paginate candidates before scoring with bounded queries and deterministic ordering.
- Add fixtures for no-overlap, mutual complementarity, one-sided needs, revoked consent, blocks, and empty profiles.
- Add offline evaluation metrics before introducing embeddings. If embeddings are later approved, store vectors only for opted-in profiles, support deletion/rebuild, and compare quality against the deterministic baseline.

## 5. Later module boundaries
- Connections must be a durable relationship, not merely an accepted request row. Prevent duplicate/reverse pending requests and reject new requests for existing connections.
- Messaging should be implemented only after block/report and connection permissions are enforced server-side. Message history, delivery state, attachment access, and notification behavior need a separate contract.
- Voice/video: issue short-lived room tokens from backend only; emergency OFF must stop new tokens and terminate/revoke active sessions.
- Teams/projects/tasks/opportunities need role-based authorization, membership lifecycle, invitations, audit, pagination, and notifications before UI work.
- Admin moderation needs least-privilege moderator roles distinct from owner superadmin; all actions audited.

## 6. Required automated and manual verification
- Unit tests for profile validation, visibility matrix, consent gates, match reasons, request lifecycle, and rate-limit policy.
- Edge Function integration tests against ephemeral PostgreSQL/Supabase-compatible services.
- Migration tests for clean install and upgrade from the current schema.
- RLS/grant tests for every table and RPC.
- Android build plus UI/state tests for Persian and English, loading, empty, offline, timeout, and revoked-session states.
- Physical-device checks on at least one Android device and Iranian mobile-network paths before any Iran rollout claim.
- Backup and restore drill before moving real user data.
- Production deployment and feature activation remain separate owner approvals.

## 7. Acceptance gates
Phase 1 is complete only when:
1. Profile fields are editable and round-trip correctly after app restart/re-login.
2. Consent is explicit, versioned, atomic, and revocation takes effect immediately.
3. Visibility and blocks are enforced on the server for list and detail endpoints.
4. Export/delete behavior is documented and tested.
5. Abuse controls, timeouts, and safe error handling are covered by tests.
6. No service-role key or private chat content is shipped to or processed by the Android client.
7. CI passes and physical-device tests are recorded honestly.
8. Feature remains OFF until a separately approved pilot.

## 8. Immediate implementation order
1. Add automated regression tests around the current API behavior and migration.
2. Implement atomic profile/consent save and complete profile field support.
3. Implement profile detail visibility and export/delete semantics.
4. Implement blocks and reports with moderation authorization.
5. Add rate limits, body limits, and bounded timeouts.
6. Re-run Deno, ephemeral PostgreSQL, Android build, and security tests.
7. Review diff and obtain separate production approval before deploy/enable.

# EMA Autonomous Core & Personal Memory

## Product direction

EMA is an execution-first assistant. A single model is not the product's brain. The core coordinates interchangeable language-model providers, retrieval/search, account-scoped memory, specialist tools, and mission state.

## Provider independence

- Android calls only the authenticated EMA gateway; provider credentials never ship in the APK.
- The gateway owns provider selection and capability checks.
- Existing GapGPT remains a configured provider, not a hard-coded product dependency.
- An optional free-tier primary can be configured with `EMA_CHAT_FREE_PRIMARY_API_KEY`, `EMA_CHAT_FREE_PRIMARY_BASE_URL`, and `EMA_CHAT_FREE_PRIMARY_MODEL`.
- A separate optional fallback can use `EMA_CHAT_FREE_FALLBACK_API_KEY`, `EMA_CHAT_FREE_FALLBACK_BASE_URL`, and `EMA_CHAT_FREE_FALLBACK_MODEL`.
- GapGPT remains the final compatibility fallback while its credentials are configured.
- Only providers explicitly approved as free/no-charge for this route may be added to the free chain. Paid NetArz credentials are intentionally not reused by the free route.
- Fallback is allowed only for network failures, HTTP 408, 429, or 5xx. Authentication, permission, invalid-request, and quota errors do not silently switch providers.
- Responses remain OpenAI-compatible. Provider/model attribution belongs in server-side logs and must not expose secrets.

## Web knowledge

The app already has a DuckDuckGo HTML search client. It is a fallback, not a guaranteed search API. The target architecture is a server-side search adapter (Brave/Tavily or another provider with an approved key and terms), source extraction, timestamps, and evidence-aware synthesis. No search vendor is activated by this change; until a key is configured, the existing client search path remains available.

## Personal memory model

- Local device memory remains available offline.
- Cloud memory is account-scoped by Supabase Auth UUID, not by device ID.
- Devices sync only explicitly saved or user-approved memories.
- Memory records carry type, confidence, source, sensitivity, pinned status, and timestamps.
- Corrections and task outcomes can become learning events; proposed memories must be reviewable before they become durable facts.
- User-facing controls must eventually support inspect, edit, pin, delete, export, and sync pause.
- Sensitive memories are excluded from automatic extraction by default.
- Server tables use RLS for own-row reads; mutations go through the authenticated Edge Function and service role.
- The migration is not applied to the live Supabase project by this branch.

## Learning loop

A daily review may summarize corrections, preferences, and task outcomes. It must not retrain the base model automatically. The practical first implementation is retrieval-augmented personalization plus reviewed memory updates and regression evaluation.

## Mission execution

A mission has a goal, plan, step states, required permissions, evidence/results, and completion summary. External side effects (payments, bookings, sending messages, account changes) require explicit confirmation. Tools must return structured outcomes; the model cannot claim completion without a successful tool result.

## Delivery sequence

1. Stabilize microphone service state and composer interactions.
2. Add account-scoped memory schema and authenticated API.
3. Add provider routing without exposing keys or bypassing billing.
4. Connect memory retrieval/sync to chat and build user memory controls.
5. Connect server-side web search with source freshness and citations.
6. Build mission planner/executor and specialist tool registry.
7. Upgrade markets, video, cinema, music/paywall, and payment-review UI as separately testable modules.

## Deployment gates

- Run Android and Supabase CI.
- Review migration and apply it manually only after approval.
- Configure provider/search secrets in Supabase; do not put them in Gradle or Android resources.
- Test on a physical Android device, including permission denial, app backgrounding, notification stop, network loss, and Persian speech support.
- Do not merge or deploy from this branch automatically.

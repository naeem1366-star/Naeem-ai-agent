# EMA delivery backlog — 27 Nov 2026

This checklist tracks the full scope discussed for the next EMA build. Status is intentionally explicit; a documented design is not marked as implemented.

## Foundation — current branch

- [x] Create an isolated development branch stacked on the current voice/media/market branch.
- [x] Add account-scoped memory schema draft and authenticated API routes.
- [x] Add Android client for explicit cloud-memory sync and profile sync.
- [x] Preserve local memory for offline use and respect cloud-sync opt-out.
- [x] Record user corrections and preference signals as reviewable learning events.
- [x] Add configurable free-tier model-provider chain with transient-error fallback.
- [x] Keep paid NetArz credentials out of the free provider chain.
- [x] Add optional server-side Brave Search adapter; retain DuckDuckGo as client fallback.
- [x] Connect recent conversation history to model requests.
- [x] Add text-file attachment support (TXT/CSV/JSON/XML and other text MIME types; 120 KB character cap).
- [x] Simplify composer controls and move secondary tools into a compact menu.
- [x] Fix voice service duplicate-start and recognition-retry state handling.
- [ ] Verify all Android and Supabase CI checks on this branch.
- [ ] Test voice, memory sync, search and file attachment on a physical Android device.
- [ ] Apply migration 0007 only after review and explicit deployment approval.
- [ ] Configure optional EMA_CHAT_FREE_PRIMARY_*, EMA_CHAT_FREE_FALLBACK_* and EMA_SEARCH_API_KEY secrets. Never place secrets in the APK.

## Autonomous execution core

- [ ] Define mission/task/step/result/permission contracts.
- [ ] Build intent classifier and planner independent of the selected model.
- [ ] Build specialist tool registry and structured tool-result handling.
- [ ] Add mission status, retries, cancellation, progress and completion evidence.
- [ ] Require explicit confirmation for purchases, bookings, external messages, data sharing and account changes.
- [ ] Add proactive opportunity radar and scheduled task follow-ups.
- [ ] Add regression tests for natural Persian phrasing, ambiguity, corrections and unsafe side effects.

## Personal intelligence and continuous improvement

- [ ] Add a memory management screen: inspect, search, edit, pin, delete and export.
- [ ] Add per-user profile settings and cloud/local sync controls to the UI.
- [ ] Add daily learning review of corrections and task outcomes.
- [ ] Keep suggested facts pending until user confirmation; do not silently learn sensitive data.
- [ ] Add conflict resolution, duplicate detection, retention limits and memory export/delete.
- [ ] Add encrypted local storage for sensitive local-only memories.
- [ ] Add memory quality and personalization evaluation tests.

## Chat, voice and attachments

- [ ] Complete provider capability discovery and model selection UI.
- [ ] Add web-search freshness, domain controls, source citations and page extraction hardening.
- [ ] Add PDF/DOCX/XLSX attachment extraction with explicit size limits and privacy warnings.
- [ ] Add robust streaming, stop-generation and long-response playback controls.
- [ ] Expand voice commands from local health/workout logging to permissioned mission routing.
- [ ] Add real-time service state and device-specific microphone troubleshooting.
- [ ] Keep wake-word listening optional, visible, user-started and battery-aware.

## Professional modules

### Market Pulse
- [ ] Replace sparse market summary with professional multi-market dashboard and rich charts.
- [ ] Add instrument detail pages, time ranges, volume, heatmaps and cross-market comparisons.
- [ ] Add alerts and source/freshness labels; never fabricate or imply unavailable data is live.
- [ ] Verify all data providers, licensing, runtime availability and Android integration.

### Cinema / Iranian film and series
- [ ] Verify official APIs and licensing for Iranian catalog providers.
- [ ] Distinguish metadata, trailers and licensed full playback.
- [ ] Build an in-app catalog experience only where provider terms permit; do not bypass DRM or subscriptions.

### Music
- [ ] Show paid lock and exact price/credit requirement before generation.
- [ ] Audit failed-generation handling and ensure failed jobs do not charge users.
- [ ] Upgrade music creation workflow and generation history.

### Video Studio
- [ ] Upgrade to a professional scene/timeline workflow with storyboard, clips, transitions and camera controls.
- [ ] Add aspect ratio, duration, reference assets, audio, project history and export controls.
- [ ] Keep free and paid model availability, cost estimates and billing gates explicit.

### Payments and owner console
- [ ] Redesign manual bank-transfer payment flow with clear instructions and receipt fields.
- [ ] Capture amount, date/time, bank, tracking reference, receipt image and optional notes.
- [ ] Add searchable admin review queue, approve/reject reasons, audit history and duplicate detection.
- [ ] Credit wallet only after verified payment; never approve based only on an uploaded receipt.

### Property Intelligence
- [ ] Verify Divar official API/terms and permitted access before integration.
- [ ] Build neighborhood map, asking-price/m² comparisons and filters where lawful data is available.
- [ ] Label listing asking prices separately from verified completed transaction prices.

## Release gates

- No merge to main or production deployment without explicit owner approval.
- No live database migration or provider secret change without explicit approval.
- A feature is complete only after automated checks and a physical-device acceptance test.
- Report partial implementations honestly; never describe planned features as finished.

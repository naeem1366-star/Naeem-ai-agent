# Naeem AI 2.0 — Architecture

## Product direction
A local-first, multi-profile personal life platform with a central AI orchestrator and independent feature areas.

## Confirmed decisions
- Core data stays on-device and core features work offline.
- Multiple local profiles are supported with profile-scoped data.
- Trip participants can be added manually or invited.
- Backups support both encrypted local export and optional cloud storage.
- Live collaboration between separate devices is an optional future sync service; invitation links alone do not provide live sync.

## Architecture layers
1. App shell and navigation
2. Central AI orchestrator and explicit tool registry
3. Shared services: profiles, permissions, memory, files, reminders and backup
4. Feature areas: finance, travel, health, productivity, projects, documents, family, vehicles, creativity, learning, games, cinema and music
5. Local persistence and optional remote backup/collaboration

## Android technology
- Kotlin and Jetpack Compose
- Room for structured local data
- DataStore for preferences
- Android Keystore-backed encryption for sensitive exports and secrets
- WorkManager for deferred backup and sync jobs
- Provider abstraction for AI services

## Data safety
- Every personal record must be scoped to a profile or workspace.
- Shared trip records must be explicitly placed in a trip workspace.
- Financial amounts use integer minor units (no floating-point money calculations).
- Destructive and external actions require confirmation.
- Database migrations must preserve existing v1.4 data.
- Never overwrite the v1.4 main branch during v2 development.

## Initial implementation milestone
- Dedicated development branch: feature/v2-foundation
- Add Room and profile/workspace persistence foundation.
- Add a consistent brain mark as the app launcher identity.
- Next: migration adapters for legacy stores, encrypted backup format, navigation shell and tests.

## Important distinction
Local-first storage is not live multi-device synchronization. Real-time collaboration requires an optional authenticated sync service. Offline/manual trip participation remains available without it.

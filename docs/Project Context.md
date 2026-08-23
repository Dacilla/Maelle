# Maelle Project Context

## Product Goal

`Maelle` is a reliability-first Android application for downloading media from Plex Media Server for offline playback.

The active `Maelle` repository is expected to live at `C:\Users\alex\Desktop\Code\Maelle`.
The old `PlexDownloader` reference repository is located at `C:\Users\alex\Desktop\Code\PlexDownloader`.

The app exists to solve persistent problems in the official Plex download experience, especially:

- unstable downloads
- poor recovery after interruption
- opaque failure states
- losing track of completed files

## Core Principles

- Reliability over feature count
- Local database as the source of truth
- Explicit job states and failure reasons
- Direct and transcoded downloads treated as distinct workflows
- Sensitive tokens always redacted in logs
- Recovery and reconciliation treated as core features, not polish

## Initial Scope

- Android only
- Kotlin
- Jetpack Compose UI
- Plex PIN authentication
- Server discovery and connection selection
- Movie and TV episode browsing
- Original-quality direct downloads
- Queue-based transcoded downloads
- Offline playback of completed files

## Non-Goals For The First Version

- iOS support
- full parity with the official Plex app
- broad media type support beyond the main video use cases
- large settings surface area
- extensive visual polish ahead of reliability work

## Key Design Assumptions

The following assumptions are inherited from the original project design work:

- Original-quality downloads should prefer direct file transfer.
- Transcoded downloads should use the server-managed download queue flow.
- The app should persist enough metadata locally to keep completed downloads visible and playable even if network state changes.
- The system should make failure causes more legible than the official client.

## Suggested Technical Stack

- Kotlin
- Jetpack Compose
- Navigation Compose
- Room
- WorkManager
- Media3
- Hilt
- Retrofit with OkHttp and serialization library of choice

## High-Level Architecture

### Presentation

- Compose screens
- ViewModels
- UI state models

### Domain

- use cases
- domain models
- download and auth state logic

### Data

- Plex API clients
- local database repositories
- file storage adapters
- background work adapters

## Core Data Concepts

The implementation should revolve around a few stable concepts:

- `Server`
- `MediaItem`
- `DownloadJob`
- `DownloadArtifact`
- `AppSession`

The exact schema can evolve, but download jobs and downloaded file artifacts should not be collapsed into one ambiguous record if that makes reconciliation harder.

## Important Reference Documents

These should be available in `docs/`:

- `Design Proposal.md`
- `Refactor Proposal.md`
- `Android Native Implementation Proposal.md`
- `Plex_API_CheatSheet.md`
- `Plex_API_openapi.json`

The old `PlexDownloader` repo should be used for historical context and reference material only, not as the implementation base.

## Implementation Priorities

1. Set up the Android project foundation and tooling.
2. Implement authentication and server discovery.
3. Persist session and server connection state.
4. Implement direct downloads with durable job tracking.
5. Implement queue-based transcoded downloads.
6. Add startup reconciliation and local playback.
7. Harden edge cases with device testing.

## Guidance For Future Work

When making decisions, prefer the option that improves:

- recovery after interruption
- correctness of persisted state
- clarity of job lifecycle
- testability of failure handling

Do not optimize early for cross-platform reuse if it weakens the Android download model.

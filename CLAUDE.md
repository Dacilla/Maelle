# CLAUDE.md

This file provides baseline guidance for working in the Android-native PlexDownloader rewrite repository.

## Project Purpose

This project is a reliability-first Android application for downloading Plex media for offline playback.

The primary goal is not to recreate the entire Plex mobile app. The goal is to build a stable download client that is better at:

- explicit download states
- recoverability
- resumability
- local persistence
- transparency when failures occur

## Source Of Truth

The local database is the source of truth for download state.

UI must reflect persisted state rather than trying to own download truth in memory.

## Core Product Rules

- Reliability is more important than feature count.
- Direct downloads and queue-based transcoded downloads are separate workflows.
- All sensitive information such as Plex tokens must be redacted in logs.
- Errors should be categorized and surfaced clearly.
- Startup should reconcile persisted job state, filesystem state, and any remote queue state before trusting in-progress jobs.

## Platform Scope

- Android native only
- Kotlin
- Jetpack Compose
- Room
- WorkManager
- Media3

iOS and cross-platform concerns are out of scope unless explicitly added later.

## Architecture Expectations

Prefer a feature-oriented architecture with clear boundaries:

- presentation
- domain
- data

Use cases should contain business logic. UI should render state and send actions. Network and persistence code should live behind repositories or adapters rather than being called directly from UI code.

Avoid god objects. Avoid a single giant Plex client, a single giant download manager, or a single giant app state holder if the responsibilities can be split cleanly.

## Download Model Expectations

Downloads are first-class jobs.

Persist enough information to recover and reconcile:

- job state
- requested quality
- strategy type
- queue identifiers where applicable
- byte counts
- failure details
- local file artifact information

Do not treat downloads as only a UI concern or only an in-memory coroutine.

## Logging Rules

- Never log Plex tokens in raw form.
- Never log raw URLs containing tokens unless redacted first.
- Include stable identifiers such as job id or server id where useful.
- Make logs useful for debugging real-device failures.

## Documentation To Read First

When starting work in this repo, read these first if present:

- `docs/Project Context.md`
- `docs/Design Proposal.md`
- `docs/Refactor Proposal.md`
- `docs/Android Native Implementation Proposal.md`
- `docs/Plex_API_CheatSheet.md`

Treat these as guidance, but prefer the current codebase if documentation and implementation diverge.

## Working Style

Before making architectural changes, check whether they support the main project goal: stable offline downloads.

Prefer practical solutions over abstract patterns. Use abstraction when it improves recovery, testing, or maintainability, not just because it is theoretically cleaner.

## Initial Priorities

1. Build a correct project foundation.
2. Implement auth and server discovery.
3. Implement durable download job persistence.
4. Implement direct downloads.
5. Implement queue-based transcoded downloads.
6. Add reconciliation and hardening.

## Testing Expectations

This project should have stronger testing than the old prototype.

At minimum, add coverage for:

- state transition logic
- repository behavior
- API mapping
- token redaction
- download recovery flows

Real-device testing is required for meaningful validation.

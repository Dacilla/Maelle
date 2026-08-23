# Maelle

Maelle is an Android-native Plex download client focused on reliable offline playback.

This project is the clean rewrite of the older `PlexDownloader` Expo/React Native prototype. The rewrite exists because the core problem is durable downloading and recovery, and Android-native tooling is a better fit for that than the original stack.

Expected repository locations:

- `Maelle`: `C:\Users\alex\Desktop\Code\Maelle`
- old reference repo: `C:\Users\alex\Desktop\Code\PlexDownloader`

## Goals

- Reliable direct downloads
- Reliable queue-based transcoded downloads
- Clear, explicit download states
- Strong recovery after interruption
- Local database as the source of truth
- Offline playback of completed files

## Non-Goals For The First Version

- Full parity with the official Plex mobile app
- iOS support
- Cross-platform UI sharing
- Broad feature scope ahead of reliability work

## Planned Stack

- Kotlin
- Jetpack Compose
- Navigation Compose
- Room
- WorkManager
- Media3
- Hilt
- Retrofit with OkHttp

## Repository Setup

Recommended top-level structure:

```text
app/
docs/
gradle/
build.gradle.kts
settings.gradle.kts
README.md
CLAUDE.md
```

## Read First

When working in this repository, read these first:

- `CLAUDE.md`
- `docs/Project Context.md`
- `docs/Design Proposal.md`
- `docs/Refactor Proposal.md`
- `docs/Android Native Implementation Proposal.md`
- `docs/Plex_API_CheatSheet.md`

## Initial Milestones

1. Set up the Android project foundation.
2. Implement Plex PIN authentication.
3. Implement server discovery and connection selection.
4. Implement direct downloads with durable job tracking.
5. Implement queue-based transcoded downloads.
6. Implement reconciliation and local playback.

## Reference

The old `PlexDownloader` repository should be treated as reference material only. Carry forward the product goals, API knowledge, and architecture lessons, but not the old runtime or project structure.

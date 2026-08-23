# Maelle Android Native Implementation Proposal

## Purpose

This document follows the refactor proposal and assumes a fresh Android-native implementation is the preferred path. The goal is to build `Maelle`, a reliable offline Plex download client that matches the original problem analysis from [Design Proposal.md](/c:/Users/alex/Desktop/Code/PlexDownloader/docs/Design%20Proposal.md), while using Android-native tools that are better suited to durable downloads, recovery, and background work.

This is not a feature-maximization plan. It is a reliability-first implementation plan.

## Recommendation Summary

I recommend:

- a new Android-native project called `Maelle`
- Kotlin as the implementation language
- Jetpack Compose for UI
- Room for local persistence
- WorkManager for durable background jobs
- Media3 for local playback
- Retrofit or Ktor for Plex API access

I also recommend creating this as a new repository rather than placing it inside the current Expo repository.

## Why A Native Android Rewrite Fits The Problem Better

The core problem is stable downloading, not cross-platform UI delivery. The original design proposal already points toward Android-first delivery and a focus on recovery, explicit download states, and local persistence. Android-native tooling is a better fit for that than Expo-managed JavaScript because it gives you stronger support for:

- background and deferred work
- app process death recovery
- foreground service behavior when needed
- storage and file access handling
- progress notifications
- durable download orchestration

This directly aligns with the goals in [Design Proposal.md](/c:/Users/alex/Desktop/Code/PlexDownloader/docs/Design%20Proposal.md), especially the sections on instability, resumability, forgotten downloads, and server-managed download queues.

## Repository Recommendation

### Recommended: new repository

I recommend a new repository for the Android-native implementation.

Reasoning:

- The current `PlexDownloader` repo is an Expo/React Native prototype with different architectural assumptions.
- A rewrite will have a different toolchain, project layout, build system, and testing model.
- Keeping both implementations in one repo will increase noise and make the new project harder to reason about.
- A new repo makes it easier to define clean conventions, issue tracking, CI, and documentation from day one.

### When keeping the same repo would make sense

Keeping the same repo only makes sense if you explicitly want this repository to become a long-lived research or archive repo containing:

- the old Expo prototype
- API reference material
- design notes
- the new Android app in a separate subdirectory

That can work, but it is not the cleaner engineering choice.

### Practical recommendation

Use:

- this repo as the archival/reference repo
- a new `Maelle` repo as the active implementation repo

## What Should Be Carried Forward

The following materials from this repo are worth carrying into the new project:

- [docs/Design Proposal.md](/c:/Users/alex/Desktop/Code/PlexDownloader/docs/Design%20Proposal.md)
- [docs/Refactor Proposal.md](/c:/Users/alex/Desktop/Code/PlexDownloader/docs/Refactor%20Proposal.md)
- [docs/Plex_API_CheatSheet.md](/c:/Users/alex/Desktop/Code/PlexDownloader/docs/Plex_API_CheatSheet.md)
- [docs/Plex_API_openapi.json](/c:/Users/alex/Desktop/Code/PlexDownloader/docs/Plex_API_openapi.json)

The following implementation ideas are also worth carrying forward, even if the code is not:

- SQLite as the source of truth for downloaded media state
- server connection testing and choosing the best reachable connection
- separating direct file download from transcoded queue-based download
- making download states explicit and user-visible
- redacting sensitive tokens in logs

## Proposed Technology Stack

### Language and platform

- Kotlin
- Android SDK
- Minimum SDK chosen based on your device support needs

### UI

- Jetpack Compose
- Navigation Compose
- Material 3, but keep the design restrained and utility-focused

### Dependency injection

- Hilt

This is optional, but I recommend it. It reduces friction as the app grows and keeps service wiring predictable.

### Persistence

- Room

Use Room as the strongly typed wrapper around the local SQLite database. This keeps the "local database as source of truth" idea from the current project, but with better migration and query ergonomics.

### Networking

- Retrofit with OkHttp and Moshi or Kotlinx Serialization

This is the most straightforward stack for stable Android API integration. The main requirement is that request/response logging must redact Plex tokens.

### Background work

- WorkManager

Use WorkManager for:

- polling and reconciliation
- retryable download preparation tasks
- download continuation or verification work

Whether actual file transfer should be done through WorkManager workers alone or a dedicated foreground service depends on how aggressive the background download requirements are. A practical first version can begin with WorkManager orchestration and only introduce a foreground service when required by real-device testing.

### Playback

- Media3 ExoPlayer

Use Media3 for local offline playback rather than building a custom playback stack.

## Architectural Direction

I recommend a feature-oriented clean architecture with explicit boundaries.

### Layers

#### Presentation

- Compose screens
- ViewModels
- UI state models

#### Domain

- use cases
- domain models
- state machines for auth and downloads

#### Data

- Plex API clients
- Room DAOs and repositories
- file storage adapter
- download job adapter

## Core Features For Version 1

Version 1 should be deliberately small. The app should do a few things well instead of trying to mirror the full Plex app.

### V1 scope

- authenticate with Plex PIN flow
- fetch available servers
- choose the best connection for a server
- browse movie libraries
- browse TV show libraries to the episode level
- enqueue direct original-quality downloads
- enqueue transcoded downloads through the server download queue
- show explicit job states and error reasons
- survive app restarts and reconcile local state
- play completed local files

### Explicitly out of scope for V1

- advanced settings screens beyond essentials
- large-scale metadata caching for everything
- subtitle management beyond what is required for stable download
- tablet-specific polish beyond responsive layout basics
- iOS support

## Download Model

The most important part of the new app is the download model. Treat downloads as first-class jobs, not as a side effect of UI actions.

### Recommended entities

- `Server`
- `MediaItem`
- `DownloadJob`
- `DownloadArtifact`
- `AppSession`

### Recommended job states

- `Queued`
- `Preparing`
- `WaitingForServer`
- `Downloading`
- `Paused`
- `Completed`
- `Failed`
- `NeedsReconciliation`

### Core rules

- The database is the source of truth.
- UI renders database-backed state.
- Workers update job state, not UI directly.
- Startup runs reconciliation before the app trusts in-progress jobs.
- Every failure stores a machine-readable category and a user-visible message.

## Suggested Database Shape

This does not need to be exact, but it should be close to this model.

### `servers`

- server identifier
- display name
- access token
- last selected connection
- cached connection list
- ownership flag
- last successful contact timestamp

### `media_items`

- rating key
- server identifier
- media type
- title
- cached metadata blob
- last metadata refresh timestamp

### `download_jobs`

- job id
- media key
- server identifier
- requested quality
- strategy type: direct or queue-based
- current state
- retry count
- last error category
- last error message
- queue id if applicable
- queue item id if applicable
- byte counts
- created and updated timestamps

### `download_artifacts`

- artifact id
- job id
- local file path
- expected size
- actual size
- checksum if you later want verification
- thumbnail path if stored separately
- verified exists flag

## Networking Design

Split network access into distinct clients instead of one large Plex client:

- auth client
- Plex account/resources client
- server metadata client
- download queue client
- image client

This avoids the "god client" problem that the current prototype started drifting toward.

## Logging Design

Logging should be structured and consistent from the start.

Rules:

- every log event has a component tag
- every download log includes job id where possible
- all Plex tokens are redacted
- raw URLs with embedded tokens are never logged unredacted
- API failures should distinguish network failure, server rejection, and parsing failure

## Testing Strategy

This project needs stronger testing than the current prototype.

### Unit tests

- use case logic
- state transition logic
- connection selection logic
- error classification

### Integration tests

- Room repository behavior
- API client mapping and token redaction
- download job reconciliation

### Device testing

Real-device testing matters for this app more than most apps. At minimum, test:

- app restart during direct download
- app restart during transcoded preparation
- connectivity loss during download
- server unavailable during queue polling
- storage pressure and missing file recovery

## Suggested Project Structure

At a high level:

```text
app/
  src/main/java/.../app
  src/main/java/.../core
  src/main/java/.../feature/auth
  src/main/java/.../feature/servers
  src/main/java/.../feature/library
  src/main/java/.../feature/downloads
  src/main/java/.../feature/player
  src/main/java/.../data/local
  src/main/java/.../data/remote
  src/main/java/.../workers
  src/test
  src/androidTest
docs/
```

The exact package layout can be adjusted, but avoid a flat package tree and avoid one giant `network` or `service` package.

## Phased Implementation Plan

### Phase 0: project setup

- create the new Android repo
- configure Gradle, Kotlin, Compose, Hilt, Room, networking, testing
- add baseline docs and coding conventions
- add CI for build, unit tests, and lint

### Phase 1: authentication and server discovery

- implement Plex PIN auth flow
- persist session token securely enough for your use case
- fetch Plex resources
- list and test server connections
- persist selected server connection

Success criteria:

- sign in works
- server list loads
- server connection selection is stable across app restarts

### Phase 2: library browsing

- fetch library sections
- list movie and show libraries
- browse media items
- load media details for download planning

Success criteria:

- you can browse to a specific downloadable movie or episode

### Phase 3: direct downloads

- create `DownloadJob` and `DownloadArtifact` persistence
- implement original-quality direct download
- show explicit progress and failure states
- restore job state after app restart

Success criteria:

- a direct download survives interruption and can be reconciled correctly

### Phase 4: queue-based transcoded downloads

- create queue get/create flow
- add item to queue
- poll queue state
- start final file transfer when ready
- store queue identifiers locally

Success criteria:

- transcoded downloads move cleanly through queued, preparing, downloading, and completed states

### Phase 5: playback and reconciliation

- implement local playback
- build startup reconciliation workflow
- verify local file existence for completed items
- recover or mark stale jobs correctly

Success criteria:

- completed downloads remain discoverable and playable
- broken or missing files are surfaced clearly

### Phase 6: hardening

- improve retry strategy
- improve error messages
- add notification UX
- test edge cases on target devices

## New Repo Setup Recommendation

Use a new repository named `Maelle`.

## What To Copy Into The New Repo

At minimum, copy or recreate these documents in the new repo:

- `docs/Design Proposal.md`
- `docs/Refactor Proposal.md`
- `docs/Android Native Implementation Proposal.md`
- `docs/Plex_API_CheatSheet.md`

I would also add a new `docs/Project Context.md` in the new repo with a condensed version of the essential context.

## Suggested `Project Context.md` Contents

If you create a new repo, add a document like this near the start:

### Product goal

- Reliable Android app for downloading Plex media for offline playback.

### Core principles

- Reliability over feature count.
- Local database is the source of truth.
- Explicit download states and failure reasons.
- Direct downloads and queued transcoded downloads are separate workflows.
- Tokens must always be redacted in logs.

### Initial scope

- Android only.
- Movies and TV episodes.
- Plex PIN authentication.
- Direct and queue-based downloads.
- Offline playback.

### Existing reference material

- link or copy the old design proposal
- link or copy the refactor proposal
- link or copy the Plex API references

## How To Bring The Context Forward For Working With Me

If you start the new repo and want to continue working with me there, I recommend this process:

1. Copy the important docs listed above into the new repo.
2. Add a concise `CLAUDE.md` or `AGENTS.md` in the new repo describing:
   - architecture goals
   - coding conventions
   - Android-first scope
   - reliability-first priorities
3. In the first prompt in the new repo, tell me:
   - this is the greenfield Android-native rewrite of PlexDownloader
   - the old Expo repo is reference-only
   - I should treat the design and refactor docs as baseline context
   - the immediate task you want me to handle first

### Example kickoff prompt for the new repo

You can use something close to this:

```text
This repository is Maelle, the fresh Android-native rewrite of PlexDownloader. The old React Native/Expo repo is reference-only. Read CLAUDE.md plus the docs folder first, especially Design Proposal.md, Refactor Proposal.md, and Android Native Implementation Proposal.md. The project goal is a reliability-first Android app for Plex offline downloads. The local database must be the source of truth, direct downloads and queue-based transcoded downloads are separate workflows, and tokens must always be redacted in logs. Start by setting up the baseline project structure and dependencies for Kotlin, Compose, Room, WorkManager, Hilt, and Retrofit.
```

## Bottom Line

`Maelle` should be a clean Android-native build in a new repository. The old repo is still valuable, but as reference material, not as the base for the new implementation. Carry forward the design thinking, API references, and product constraints, but do not carry forward the current code structure.

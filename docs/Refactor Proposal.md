# Maelle Refactor Proposal

## Purpose

This proposal is based on the current repository, not just the original design notes. The existing code shows strong intent and a sensible product focus, but the implementation is still closer to a prototype than a stable offline media client. The largest decision is not visual or organizational. It is whether the project should continue to rely on Expo-managed JavaScript for the download engine, or move the critical download path to native Android infrastructure.

## Relationship To The Design Proposal

This document does build directly on [Design Proposal.md](/c:/Users/alex/Desktop/Code/PlexDownloader/docs/Design%20Proposal.md). I used that file primarily for two things:

- the original problem framing
- the high-level behavioral model of a more reliable Plex download client

The parts of the design proposal I carried forward as sound assumptions are:

- the project exists to solve reliability, transparency, and recovery problems in Plex downloads
- direct file download should be treated differently from transcoded download workflows
- a persistent local database should be the source of truth for downloaded media state
- the app should expose clearer states and failure reasons than the official client
- Android-first development is a valid initial target

The parts I treated as intentionally non-binding were:

- any implied assumption that the current Expo/React Native implementation is the best long-term technical vehicle
- any implementation detail that is contradicted by the current codebase
- any feature breakdown that is still aspirational rather than fully realized in code

So the proposal is not ignoring the design proposal. It accepts the design proposal's diagnosis of the problem, then evaluates whether the current code and current stack are the best way to implement that diagnosis.

## Current State Summary

The current `PlexDownloader` repository already contains the main pieces of the intended system:

- Plex authentication
- server discovery and selection
- library browsing
- SQLite persistence
- direct and queued download concepts
- a local player

The main issue is that these pieces are not yet assembled into a reliable production shape. Some of the gaps are architectural, and some are basic code health issues:

- the app does not currently typecheck because `src/api/plexClient.ts` is syntactically broken in the download queue section
- tests are minimal and are not currently runnable in this environment because Jest process spawning fails
- the app shell owns most navigation and app workflow state directly
- auth, API state, persistence, and UI concerns are coupled together
- the download engine is built around `expo-file-system/legacy`, which is not the strongest foundation for a download-first Android app

## Main Findings

### 1. The codebase is structurally ahead of the documentation, but behind production readiness

`CLAUDE.md` describes a layered architecture, but the implementation is only partially there. Some parts are cleanly separated, but the app still relies on a single top-level controller in [`App.tsx`](/c:/Users/alex/Desktop/Code/PlexDownloader/App.tsx#L79) for auth state, screen flow, selected server, selected library, selected media, playback state, toast state, and download entrypoints.

That approach is workable for an early prototype, but it becomes hard to reason about once failures, retries, background work, and restoration are important.

### 2. The API client is currently the most urgent technical problem

The download queue implementation in [`src/api/plexClient.ts`](/c:/Users/alex/Desktop/Code/PlexDownloader/src/api/plexClient.ts#L418) is cut off mid-method. `npx tsc --noEmit` fails on this section, so the repo is currently below the baseline required for safe refactoring.

This needs to be fixed before any larger improvements, because it blocks reliable iteration.

### 3. The app is trying to solve a native-background-download problem inside Expo-managed abstractions

The most important feature of this app is reliable downloading. Right now the core engine is built around `expo-file-system/legacy` and `DownloadResumable` in [`src/services/downloadService.ts`](/c:/Users/alex/Desktop/Code/PlexDownloader/src/services/downloadService.ts#L1) and [`src/database/operations.ts`](/c:/Users/alex/Desktop/Code/PlexDownloader/src/database/operations.ts#L6).

That is good enough for prototyping, but it is not the strongest choice for:

- long-running background transfers
- OS-managed retries
- surviving app termination
- Android-specific download behavior
- richer notification and foreground service behavior

This matters because the whole problem statement of the project is download reliability, not UI iteration speed.

### 4. State ownership is duplicated and not yet normalized

Authentication token persistence happens in both [`src/screens/AuthScreen.tsx`](/c:/Users/alex/Desktop/Code/PlexDownloader/src/screens/AuthScreen.tsx#L93) and [`App.tsx`](/c:/Users/alex/Desktop/Code/PlexDownloader/App.tsx#L135). That is a small example of a wider pattern: state and side effects are distributed across screens, singleton services, and the app shell without a single clear ownership model.

That increases the risk of subtle bugs around app restore, logout, active server changes, and interrupted downloads.

### 5. The database is useful, but the current schema is still too close to implementation detail

The database work is one of the better parts of the project. The `downloads` table in [`src/database/schema.ts`](/c:/Users/alex/Desktop/Code/PlexDownloader/src/database/schema.ts#L35) is a good start, and using SQLite as the source of truth is the right direction.

However, the schema is still centered on a single flat download record. It does not yet clearly separate:

- logical media item
- download job
- file artifact
- remote queue/transcode state
- playback/local availability state

As features grow, that will make recovery and reconciliation logic harder than it needs to be.

### 6. Test coverage is too thin for a download-focused app

The only current tests are narrow `DownloadService` unit tests in [`src/services/__tests__/downloadService.test.ts`](/c:/Users/alex/Desktop/Code/PlexDownloader/src/services/__tests__/downloadService.test.ts#L15). They cover filename sanitization, retry classification, and directory creation, but not:

- API contract handling
- queue polling behavior
- persistence and restore flows
- recovery after interruption
- screen-to-service integration

For a project whose core value is reliability, this is the biggest quality gap after the broken build.

## Recommendation

### Recommended direction: Android-first native rewrite for the download engine

If the goal is to build the most reliable version of this app for your own use, I would not keep the whole system inside Expo-managed React Native.

The strongest version of `Maelle` is:

- Android-first
- Kotlin for the core app or at minimum for the download subsystem
- Room for persistence
- WorkManager for durable queued work
- ExoPlayer or Media3 for local playback
- Retrofit/Ktor client for Plex communication

This fits the problem much better than Expo because Android gives you the exact primitives you need for stable downloads and recovery.

### Why I recommend that route

- Your design proposal explicitly prioritizes reliability over feature breadth.
- The original target platform is Android.
- The current code already leans heavily into Android assumptions.
- Download management is the app, not just one feature among many.
- Native Android tools are better aligned with resumable transfers, process death, storage handling, notifications, and queued background work.

### What I would keep from the current code if you rewrite

- the product scope
- the SQLite-as-source-of-truth idea
- the split between direct downloads and download queue based transcoded downloads
- the server selection and connection testing concept
- the emphasis on explicit states and user-visible failure reasons

### If you do not want a full rewrite

The second-best path is:

- keep React Native
- leave Expo managed workflow
- move to bare React Native or a custom dev client
- implement the download engine as a native Android module backed by WorkManager and the platform download stack
- keep the JS layer mostly for UI, Plex browsing, and orchestration

That gives you a hybrid path without throwing away everything.

## Proposed Target Architecture

Regardless of language/framework, I would aim for these boundaries:

### Domain layer

Pure business models and use-cases:

- authenticate user
- discover servers
- pick best connection
- browse library
- plan download
- enqueue download
- reconcile download state
- recover interrupted jobs

### Data layer

Adapters only:

- Plex API client
- database repositories
- file storage adapter
- background job adapter

### Presentation layer

UI should render state and dispatch actions. It should not decide recovery policy or manage download internals.

### Explicit state machines

The project will benefit from explicit state machines for:

- auth flow
- server connection selection
- download lifecycle
- playback availability

Right now these states exist implicitly in conditionals and DB flags. Making them explicit would simplify failure handling.

## Proposed Data Model Changes

I would move from one overloaded `downloads` table toward something closer to:

- `servers`
- `libraries` or lightweight cached sections
- `media_items`
- `download_jobs`
- `download_files`
- `app_session` or `app_settings`

Key ideas:

- `media_items` stores the logical Plex object and cached metadata
- `download_jobs` stores intent, quality, queue state, retry state, and error state
- `download_files` stores actual local file paths, sizes, hashes if needed, and verification state

This lets you track multiple attempts or formats for the same media item without overloading one record.

## Refactor Roadmap

### Option A: Stabilize current codebase first

This is the minimum path if you want to keep the current stack for now.

Phase 1:

- repair `src/api/plexClient.ts` until the project typechecks
- add `lint` and `typecheck` scripts
- make Jest runnable in a deterministic single-process mode
- document the actual architecture instead of the intended one

Phase 2:

- move app flow out of `App.tsx` into navigation plus feature-specific hooks/stores
- centralize auth/session ownership
- separate Plex API DTOs from internal domain models
- replace singleton-heavy coordination with explicit repositories/services

Phase 3:

- redesign download persistence around jobs and artifacts
- isolate download worker logic from UI concerns
- add integration tests for recovery, pause/resume, and queue polling

### Option B: Start a new Android-native implementation

This is the path I would choose if reliability is the main goal.

Phase 1:

- keep this repository as a reference implementation and API exploration sandbox
- write a small Android proof of concept:
  - sign in with Plex PIN flow
  - list servers
  - browse one library
  - direct-download one file through WorkManager

Phase 2:

- add local database and recovery
- add queue-backed transcoded downloads
- add notification/progress UX
- add offline playback

Phase 3:

- validate on the actual target devices
- optimize storage handling, retries, and edge cases
- optionally reintroduce a shared UI layer later if cross-platform becomes necessary

## Concrete Improvements Even If You Keep The Current Repo

- Replace the hand-managed screen switching in [`App.tsx`](/c:/Users/alex/Desktop/Code/PlexDownloader/App.tsx#L247) with real navigation and route params.
- Introduce a single session store for auth token, active server, and restore logic.
- Split `plexClient` into:
  - Plex auth client
  - Plex resource discovery client
  - Plex server client
  - Plex download queue client
- Add a repository layer between SQLite and services.
- Add structured logging with consistent event names and token-safe redaction.
- Add runtime validation for Plex API responses so malformed payloads fail explicitly.
- Add a recovery/reconciliation job that compares DB state, queue state, and filesystem state on startup.
- Move pagination, filtering, and browsing logic out of screens like [`src/screens/MediaListScreen.tsx`](/c:/Users/alex/Desktop/Code/PlexDownloader/src/screens/MediaListScreen.tsx#L95) into hooks or feature controllers.
- Add a small design system so styling stops being repeated ad hoc across every screen.

## Suggested Priorities

If you want the shortest path to a better project, I would prioritize in this order:

1. Restore build correctness.
2. Decide whether Expo remains acceptable for the reliability target.
3. Centralize state ownership.
4. Redesign download persistence and recovery.
5. Add integration-level testing around failure cases.

## Bottom Line

The current project is a promising prototype, not a dead end. The domain analysis is good, and several core ideas are correct. The biggest question is not whether the code can be cleaned up. It can. The real question is whether Expo-managed React Native is the right long-term home for a download-first Android app.

My recommendation is to treat this repository as a useful reference and either:

- migrate to an Android-native implementation for the real product, or
- keep the current UI stack but move the download engine into native Android code as soon as possible

If you want, the next step can be a more concrete follow-up document: either a phased salvage plan for this repo, or a greenfield Kotlin architecture proposal.

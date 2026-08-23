# Maelle

[![CI](https://github.com/Dacilla/Maelle/actions/workflows/ci.yml/badge.svg)](https://github.com/Dacilla/Maelle/actions/workflows/ci.yml)

**Maelle** is a reliability-first Android client for downloading media from a [Plex Media Server](https://www.plex.tv/) for offline playback.

It exists because downloading from Plex on mobile is fragile: transfers stall silently, interruptions lose progress, failures are opaque, and finished files are easy to lose track of. Maelle treats downloads as durable, observable jobs instead of a side effect of browsing.

## Status

Maelle is an early-stage, working app used on real devices. Core flows are functional:

- Plex PIN sign-in (short-code link flow)
- Server discovery, connection latency probing, best-connection selection
- Movie and TV library browsing with local caching
- **Direct downloads** (original quality) with byte-range resumability, sidecar subtitle fetch
- **Queue-based transcode downloads** through the server-managed download queue
- Persistent download jobs with explicit states and error categories
- Startup reconciliation and automatic resumption of interrupted downloads
- Progress notifications and in-app playback of completed files

## Product principles

- Reliability over feature count
- The local database is the source of truth for download state
- Explicit job states and machine-readable failure categories
- Direct downloads and queue-based transcoded downloads are distinct workflows
- Plex tokens are never written to logs in raw form
- Recovery after interruption is a core feature, not polish

## Download model

Every download is a persisted `DownloadJob` row plus, once transfer starts, an artifact record. Workers own the transitions; the UI renders whatever the database says.

| State | Meaning |
|---|---|
| `Queued` | Created, waiting for a worker |
| `Preparing` | Resolving media metadata / download URL |
| `WaitingForServer` | Queue item submitted, server is transcoding |
| `Downloading` | Bytes are being transferred |
| `Paused` | Deliberately held (reserved) |
| `Completed` | Artifact verified on disk |
| `Failed` | Terminal failure with categorized reason |
| `NeedsReconciliation` | Completed record disagrees with what is on disk |

Failures store both a machine-readable category (`missing_server`, `queue_failed`, `retries_exhausted`, `artifact_missing`, ...) and a human-readable message. Interrupted jobs are detected at startup and automatically re-enqueued; artifacts whose size no longer matches the database are surfaced as `NeedsReconciliation`.

## Architecture

Feature-oriented layering on Kotlin + Jetpack Compose:

```text
app/src/main/java/com/maelle/
  app/         Application, activity, root navigation state
  core/        Logging (token-redacting), DI modules, networking plumbing
  data/
    local/     Room database: entities + DAOs
    remote/    Retrofit services: auth, resources, library, download queue
    repository/Repositories coordinating network <-> database <-> disk
  domain/      Plain models: servers, session, library, download plans/states
  feature/     Compose screens + view models: auth, servers, home, player
  workers/     WorkManager workers: direct download, queue download, reconcile
```

Key decisions:

- **WorkManager** owns durability: constraints (network connected), retry backoff, process-death survival. Transfer work promotes itself to a foreground `dataSync` service while running.
- **Room** persists sessions, selected servers, cached library data, and all download job/artifact state.
- **Retrofit + OkHttp**, split into small clients (auth, plex.tv resources, per-server library/queue) rather than one god client. All traffic flows through a token-redacting logger.
- **Media3 / ExoPlayer** plays completed local files in-app.

## Building

Requirements: JDK 17, Android SDK 36.

```bash
./gradlew :app:assembleDebug   # debug APK
./gradlew :app:testDebugUnitTest  # unit tests
```

The repo ships a Gradle wrapper; no global Gradle install is needed. A debug build installs with:

```bash
./gradlew :app:installDebug
```

## Documentation

Deeper design context lives in [`docs/`](docs/):

- [Project Context](docs/Project%20Context.md) - goals, principles, priorities
- [Android Native Implementation Proposal](docs/Android%20Native%20Implementation%20Proposal.md) - phased implementation plan
- [Design Proposal](docs/Design%20Proposal.md) - problem analysis behind the rewrite
- [Refactor Proposal](docs/Refactor%20Proposal.md) - why the old prototype was retired
- [Plex API Cheat Sheet](docs/Plex_API_CheatSheet.md) - endpoint notes gathered during development

## Roadmap

- [x] PIN auth, persisted session
- [x] Server discovery + connection selection
- [x] Library browsing (movies, TV to episode level)
- [x] Direct original-quality downloads
- [x] Queue-based transcode downloads
- [x] Resumable direct downloads (byte ranges)
- [x] Automatic resume of interrupted jobs at startup
- [x] Retry budgets with explicit exhausted-retries failures
- [x] Progress/completion notifications, foreground transfers
- [x] In-app playback of completed downloads
- [x] Pause/resume controls in the UI
- [x] Sidecar subtitles downloaded with direct downloads and selectable in the player
- [x] Burned-in subtitles option for queued transcodes (verified against PMS)
- [x] Resumable queue downloads with real filenames and sizes from the server
- [x] Server switching from Home (downloads keep running per their own server)
- [x] Instrumented Room DAO tests (run with a connected device: `connectedDebugAndroidTest`)
- [x] Unit tests + CI (build, unit tests, lint, instrumented emulator job on every push)
- [ ] Live-device validation of a cross-server download switch

## Disclaimer

Maelle is an independent, personal project and is not affiliated with or endorsed by Plex. It is intended for use with media you own and are entitled to download from your own Plex server, including offline playback allowed by your Plex Pass.

## License

[MIT](LICENSE)

# Changelog

All notable changes to Maelle are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to semantic versioning.

## [0.2.0] - 2026-08-23

The reliability milestone: downloads survive process death, network drops, and server-side stalls, and the app recovers from expired credentials on its own.

### Added

- Byte-range resumable direct downloads: partial files seed an HTTP `Range` request, `206` responses append, ignored ranges restart cleanly; artifact filenames use full job ids.
- Automatic startup reconciliation that re-enqueues interrupted jobs instead of waiting for manual retries; `NeedsReconciliation` is now reserved for artifacts that disagree with disk (missing path, deleted file, size drift).
- Retry budgets: direct workers cap at 8 attempts, queue workers poll for roughly 30 minutes (60 attempts, linear backoff); exhaustion records a categorized `retries_exhausted` failure while preserving partial progress.
- Progress, completion, and failure notifications on a low-importance channel; transfer work promotes itself to a foreground dataSync service so OS background limits cannot silently starve it. POST_NOTIFICATIONS requested at launch on Android 13+.
- In-app playback of completed downloads via Media3/ExoPlayer (`PlayerActivity`) replacing external-app delegation.
- Pause and resume controls in the downloads pane, with a repository-level guard so a dying worker cannot overwrite paused state.
- Sidecar subtitles: direct downloads fetch external subtitle streams next to the video artifact, and the player attaches sibling `.srt/.vtt/.ssa/.ass` files as selectable tracks.
- Server switching from Home with a cancelable picker; library state reloads automatically and in-flight downloads keep running against their originating servers.
- Session recovery: persisted tokens are validated at startup and on 401s; rejected tokens clear the session and route back to sign-in, inconclusive network failures leave state untouched.
- Resilient PIN polling: transient poll failures tolerated up to five consecutive attempts, and received tokens are kept across ticks rather than discarded when validation hiccups.
- Unit test suite (32 tests) covering token redaction, connection selection, transcode profiles, subtitle-track building, and download-job reconciliation, plus instrumented Room DAO tests running on hardware and an API 34 emulator in CI.

### Fixed

- Token redactor double-masked query values through its header rule and left `Bearer` credentials unredacted; masking is now idempotent and scheme-aware (caught by new tests).
- Server refresh no longer wipes each cached server's selected connection - previously every refresh reset selection to null, stranding mid-flight downloads with `missing_server`; context resolution also self-heals from the authoritative session record.
- PIN polling no longer aborts sign-in on a single failed request.
- `URLDecoder.decode(String, Charset)` NewApi crash inside the redactor on API 28-32 devices; locale-sensitive byte formatting pinned to `Locale.US`.

### Changed

- Queue download artifacts now use the media title instead of `queue-{key}.mp4`.
- Download transfers use a dedicated OkHttp client with streaming-grade timeouts (30s connect / 120s read / 60s write) after default 10s read timeouts repeatedly killed large WAN transfers.

## [0.1.0] - 2026-04-04

First working build used on real hardware.

### Added

- Plex short-code PIN authentication with persisted session and logout.
- Server discovery from plex.tv resources with per-connection latency probing and best-connection selection.
- Movie and TV library browsing (sections -> items -> show/season/episode) cached in Room.
- Direct original-quality downloads and server-managed queue-based transcode downloads with explicit job states.
- Token-redacted logging across all networking.

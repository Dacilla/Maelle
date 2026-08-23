# Maelle Agent Notes

Practical context for working in this repository. `CLAUDE.md` defines the product rules; this file records how the project is actually built and where things stand. The codebase is the source of truth if anything drifts.

## What Maelle Is

A reliability-first Android app for downloading media from Plex for offline playback. Kotlin, Jetpack Compose, Room, WorkManager, Hilt, Retrofit/OkHttp, Media3. The old `PlexDownloader` repo at `C:\Users\alex\Desktop\Code\PlexDownloader` is reference-only.

Public repo: https://github.com/Dacilla/Maelle (branch `main`, CI via GitHub Actions).

## Local Tooling

- JDK 17: `C:\Users\alex\dev-tools\jdk17`
- Gradle wrapper is used; no global Gradle needed.
- PowerShell environment:

```powershell
$env:JAVA_HOME='C:\Users\alex\dev-tools\jdk17'
$env:GRADLE_USER_HOME='C:\Users\alex\Desktop\Code\Maelle\.gradle-user-home'
$env:ANDROID_USER_HOME='C:\Users\alex\Desktop\Code\Maelle\.android-user-home'
$env:Path='C:\Users\alex\dev-tools\jdk17\bin;' + $env:Path
```

- Commands that must pass before committing:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## Current Implementation State (updated 2026-08)

All of this is implemented and building:

- PIN auth (short-code `https://plex.tv/link/?pin=<code>` flow) with persisted session and logout.
- Server discovery from plex.tv resources, latency probing (`ServerConnectionTester`), best-connection selection (`domain/servers/ConnectionSelector.kt`), persisted selection.
- Library browsing: sections -> items -> show/season/episode paths, cached in Room with refresh fallbacks.
- Download planning UI (direct vs queue strategy) in the Home downloads pane.
- Direct downloads with byte-range resume: partial file on disk seeds the offset, HTTP 206 appends, 200 restarts cleanly. Artifact filenames use the full job id.
- Queue-based transcode downloads through the server download queue, profile presets in `PlexDownloadQueueRepository.profileForQuality`.
- Startup reconciliation in `MaelleAppViewModel`: interrupted jobs are re-enqueued automatically; `NeedsReconciliation` is reserved for artifacts that disagree with disk.
- Retry budgets: direct worker caps at 6 attempts, queue worker at 60 (~30 min LINEAR backoff), both fail explicitly with `retries_exhausted`.
- Progress/completion/failure notifications (`core/notifications/DownloadNotifier.kt`); workers promote to a foreground dataSync service while transferring. POST_NOTIFICATIONS requested in MainActivity.
- In-app playback of completed files via `feature/player/PlayerActivity` (Media3/ExoPlayer).
- Unit tests: `app/src/test/java/com/maelle/**` covering redaction, connection selection, queue profiles, and download job reconciliation.

Known gaps / next candidates:

- Subtitles: direct downloads fetch external sidecars and the player offers them; burned-in subs for transcoded downloads are not implemented.
- Server switching UI exists (Home -> Switch Server); live-device validation of a switch mid-download still pending.
- Instrumented DAO tests are written and compiling (`app/src/androidTest`); need `connectedDebugAndroidTest` on the Pixel 7a to execute.

## Auth Debugging History (resolved)

- The app uses the SHORT pin flow: `POST https://plex.tv/api/v2/pins` returns a 4-char code; browser handoff must use `https://plex.tv/link/?pin=<code>`. The long-code flow (`strong=true`) and `app.plex.tv/auth#?...` handoff were both rejected deliberately.
- The historical resources `HTTP 401` issue is RESOLVED; server discovery works against `clients.plex.tv/api/v2/resources`.
- `PlexAuthRepository.getAuthToken()` parses the PIN-status body defensively rather than relying on strict DTO deserialization.
- Non-server resources can omit `accessToken`; mapping filters to servers with non-blank tokens.

## App Data Inspection

- Package: `com.maelle`
- Useful `run-as com.maelle` paths: `shared_prefs/maelle.install.xml`, `databases/maelle.db*`

## Practical Warnings

- Token redaction is a project invariant. Never print raw Plex tokens in logs, patches, summaries, or debug output. `AuthTokenRedactor` masking is covered by unit tests, including idempotency.
- Do not commit device-pulled databases or `.gradle-user-home` / `.android-user-home`; they are gitignored because they can contain live tokens.
- Network access may be restricted in the coding environment; live Plex verification sometimes needs on-device logging instead.

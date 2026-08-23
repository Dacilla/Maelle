# Maelle Agent Notes

This file records practical context that is useful during active development but is not already captured well in `CLAUDE.md` or the docs folder. Treat the codebase and user instructions as the source of truth if anything here drifts.

## Local Tooling

- JDK 17 is installed user-locally at `C:\Users\alex\dev-tools\jdk17`.
- Gradle 8.7 is installed user-locally at `C:\Users\alex\dev-tools\gradle-8.7`.
- The repo has a Gradle wrapper, so global Gradle is not required.
- Typical environment for builds in PowerShell:

```powershell
$env:JAVA_HOME='C:\Users\alex\dev-tools\jdk17'
$env:GRADLE_USER_HOME='C:\Users\alex\Desktop\Code\Maelle\.gradle-user-home'
$env:ANDROID_USER_HOME='C:\Users\alex\Desktop\Code\Maelle\.android-user-home'
$env:Path='C:\Users\alex\dev-tools\jdk17\bin;' + $env:Path
```

- Common build/install command:

```powershell
.\gradlew.bat :app:installDebug
```

## Device Workflow

- A physical Pixel 7a has been used successfully for install/debug.
- `adb` device deployment is working.
- Useful commands:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
adb shell monkey -p com.maelle -c android.intent.category.LAUNCHER 1
adb logcat | Select-String "Maelle|AndroidRuntime"
```

## Current Implemented Baseline

- Android-native scaffold is in place with Kotlin, Compose, Room, WorkManager, Hilt, Retrofit, OkHttp, and token-redacted logging.
- Phase 1 scaffolding is partially implemented:
  - Plex PIN auth UI and polling
  - persisted app session
  - server discovery screen
  - Room-backed selected server state
- Important implementation files:
  - `app/src/main/java/com/maelle/app/ui/MaelleAppViewModel.kt`
  - `app/src/main/java/com/maelle/feature/auth/AuthViewModel.kt`
  - `app/src/main/java/com/maelle/feature/servers/ServerSelectionViewModel.kt`
  - `app/src/main/java/com/maelle/data/repository/PlexAuthRepository.kt`
  - `app/src/main/java/com/maelle/data/repository/PlexServerRepository.kt`
  - `app/src/main/java/com/maelle/data/remote/auth/PlexAuthService.kt`
  - `app/src/main/java/com/maelle/data/remote/resources/PlexResourcesService.kt`

## Auth And Resources Debugging History

- The app originally used `strong=true` PIN creation and showed a long alphanumeric code. This was changed back to the short PIN flow because the intended UX is the classic link-code flow.
- Verified behavior on 2026-04-04:
  - `POST https://plex.tv/api/v2/pins` returns a short 4-character code.
  - `POST https://plex.tv/api/v2/pins?strong=true` returns a long alphanumeric code.
- The short PIN browser handoff must use `https://plex.tv/link/?pin=<code>`.
- Attempting to send the user to `https://app.plex.tv/auth#?...` for the short PIN flow caused browser-side failure after authorization.
- `PlexAuthRepository.getAuthToken()` was made more defensive by parsing the raw PIN-status response body and extracting `authToken` manually instead of depending on strict DTO deserialization.

## Server Fetch Debugging History

- One earlier failure was caused by JSON serialization of `List<PlexConnection>` into Room:
  - `PlexConnection` needed `@Serializable`.
  - That bug is already fixed in code.
- Another earlier failure was caused by resource DTO strictness:
  - `accessToken` in `PlexResourceDto` had to be nullable because non-server resources can omit it.
  - Mapping should filter to actual server resources with non-blank access tokens.
- Current unresolved issue as of 2026-04-04:
  - auth can succeed
  - server discovery still fails with `HTTP 401` from `https://clients.plex.tv/api/v2/resources`
  - this happens on-device in Maelle even after the serializer fix
- The resources request has already been tried in both forms:
  - `X-Plex-Token` header
  - `X-Plex-Token` query parameter
- The request also includes `includeHttps=1`, `includeRelay=1`, and `includeIPv6=1`.
- The likely next debugging step is to validate the saved post-login token against `GET https://plex.tv/api/v2/user` before attempting `clients.plex.tv/api/v2/resources`.
- Do not assume the app is persisting a valid reusable Plex token until that validation is added and confirmed.

## App Data Inspection Notes

- App package name: `com.maelle`
- Useful `run-as` paths:
  - `shared_prefs/maelle.install.xml`
  - `databases/maelle.db`
  - `databases/maelle.db-wal`
  - `databases/maelle.db-shm`
- `maelle.install.xml` stores the persisted client/install identifier used for `X-Plex-Client-Identifier`.

## Practical Warnings

- Network access in the coding environment may be restricted, so some live Plex verification has required on-device logging instead of direct local requests.
- Token redaction is a project invariant. Never print raw Plex tokens in logs, patches, summaries, or debug output.
- The old `PlexDownloader` repo at `C:\Users\alex\Desktop\Code\PlexDownloader` is reference-only. Do not use it as the implementation base.

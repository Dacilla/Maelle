# Maelle Recommended Folder Structure

## Top Level

```text
Maelle/
  app/
  docs/
  gradle/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  README.md
  CLAUDE.md
```

## App Module

```text
app/
  src/
    main/
      AndroidManifest.xml
      java/com/yourname/maelle/
        app/
          MainActivity.kt
          MaelleApplication.kt
          navigation/
          ui/
          designsystem/
        core/
          common/
          logging/
          network/
          database/
          model/
          worker/
        data/
          local/
            dao/
            entity/
            database/
          remote/
            auth/
            resources/
            server/
            downloads/
            image/
          repository/
        domain/
          auth/
          servers/
          library/
          downloads/
          player/
        feature/
          auth/
            AuthScreen.kt
            AuthViewModel.kt
            AuthUiState.kt
          servers/
          library/
          downloads/
          player/
    test/
    androidTest/
```

## Notes

- `core/` should contain truly shared infrastructure, not feature-specific logic.
- `data/remote/` should stay split by concern so the Plex API layer does not collapse into one giant client.
- `domain/` should hold use cases and business logic, not framework-specific code.
- `feature/` should contain presentation code and per-feature ViewModels.
- `designsystem/` should keep common UI tokens and components from spreading ad hoc across the app.

## Minimum Docs To Copy Into The New Repo

Place these under `docs/`:

- `Project Context.md`
- `Design Proposal.md`
- `Refactor Proposal.md`
- `Android Native Implementation Proposal.md`
- `Plex_API_CheatSheet.md`
- `Plex_API_openapi.json`

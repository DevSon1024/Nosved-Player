# Project Structure - NosvedPlayer

A map of the codebase directory structure and file responsibilities.

## Root Directory
- `/app`: Main Android application module.
- `/gradle`: Gradle wrapper and version catalog.
- `/.agent`: GSD system and AI agent state.
- `/.planning`: Codebase map and project planning documents.

## Module: /app
### Source: /src/main/java/com/devson/nosvedplayer
- `/dao`: Room database Data Access Objects.
- `/database`: Room database definition and migrations.
- `/model`: Domain data models (Video, TrackInfo).
- `/player`: Core playback engine (`PlayerManager`).
- `/repository`: Data access abstraction layer.
- `/ui`: Compose-based UI.
  - `/components`: Reusable UI widgets.
  - `/screens`: Full-screen UI definitions (Home, Video, Settings).
  - `/theme`: Material Design 3 tokens and implementation.
- `/util`: Utility classes (Logger, Formatters, MediaMetadata).
- `/viewmodel`: ViewModel implementations for state management.
- `MainActivity.kt`: Entry point for the application.
- `NosvedApplication.kt`: Application class for global initialization.

### Resources: /src/main/res
- `/drawable`: UI icons and graphic assets.
- `/layout`: (Minimal) Legacy XML layouts if any.
- `/mipmap`: Application launcher icons.
- `/values`: Strings, colors, styles, and dimensions.
- `/xml`: Configuration for SAF, locales, and backups.

### Build & Config
- `build.gradle.kts`: Module-level build configuration.
- `proguard-rules.pro`: Code shrinking and obfuscation rules.

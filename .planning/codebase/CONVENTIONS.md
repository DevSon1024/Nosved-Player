# Conventions - NosvedPlayer

Coding standards, UI design patterns, and development best practices.

## Coding Standards

- **Language**: Kotlin-first codebase.
- **Asynchrony**: Coroutines and `StateFlow` are used exclusively for concurrency and reactive state management.
- **Logging**: Use `AppLogger.log()` for all internal system and playback-related logging.
- **Architecture**: Strict MVVM pattern with `ViewModel` and `Repository` separation.

## UI & UX Conventions

- **Design System**: Material Design 3 (M3).
- **Appearance**:
  - Supports Dynamic Colors (Android 12+).
  - Native Light/Dark mode switching.
  - Custom Color Palettes (managed via `AppThemePalette`).
- **Layouts**: Edge-to-edge layout is expected; status and navigation bars are typically transparent.
- **Typography**: Uses custom `Typography` scale defined in `ui/theme/Type.kt`.
- **Shapes**: Consistent use of `NosvedShapes` (Rounded corners ranging from 2dp to 12dp).
- **Player Controls**:
  - Two styles supported: Standard Material 3 and Modern-style inline controls.
  - "NEW" badges for unplayed videos.
  - Dimming/Greyscale for "Ended" videos.

## Resource Management

- **Strings**: All user-facing text must be in `res/values/strings.xml` to support localization.
- **Localization**: Primary support for English and Marathi (मराठी).
- **Icons**: Preference for `Material.Icons.Extended` for a rich set of vector icons.

## Data & Persistence

- **DAOs**: Use Room DAOs for structured data (history, metadata).
- **DataStore**: Use for small, key-value preferences (Theme, Player settings).
- **File Operations**: Preference for DocumentFile API to maintain SAF compatibility.

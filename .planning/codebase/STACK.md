# Tech Stack - NosvedPlayer

Detailed breakdown of the technology stack and core dependencies.

## Core Framework
- **Language**: Kotlin 2.3.0
- **UI Framework**: Jetpack Compose (BOM 2024.09.00)
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 15+)
- **Build System**: Gradle 8.x with Kotlin DSL

## Media & Playback
- **Media Engine**: Jetpack Media3 (ExoPlayer) 1.5.1
- **Extensions**: `nextlib-media3ext`, `nextlib-mediainfo` (Anil Beesetti)
- **Features**: Quality selection, Subtitles, Gesture-based controls.

## Data & Persistence
- **Local Database**: Room 2.7.0
- **Preferences**: Jetpack DataStore (Preferences) 1.0.0
- **File System**: `documentfile:1.0.1` for SAF/External storage management.

## Components & Utilities
- **Image Loading**: Coil 2.6.0 (including video frame decoding)
- **Background Tasks**: WorkManager 2.10.0
- **Dependency Injection**: None explicitly seen (manual dependency management/ViewModels).
- **Navigation**: Jetpack Navigation Compose 2.8.0

## Build Tools
- **Android Gradle Plugin (AGP)**: 8.13.0
- **KSP**: 2.3.6 (used for Room compiler)
- **JVMT**: JVM 17

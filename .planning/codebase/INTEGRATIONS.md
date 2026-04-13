# Integrations - NosvedPlayer

Analysis of external services, system integrations, and permissions.

## System Permissions
- **External Storage**:
  - `MANAGE_EXTERNAL_STORAGE`: Used for comprehensive file access on modern Android versions (Android 11+).
  - `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`: Legacy support for older Android versions (up to API 32).
  - `READ_MEDIA_VIDEO`: Targeted permission for video file access on Android 13+.
- **UX Features**:
  - `supportsPictureInPicture="true"`: Activity configured for PiP mode.

## Localization
- **Multi-language Support**:
  - **English (Default)**: Primary language.
  - **Marathi (मराठी)**: Full localization support.
- **Language Selection**: Integrated into Appearance Settings with dynamic switching.

## Files & Storage
- **MediaStore Integration**: Primary source for discovering video content on the device.
- **SAF (Storage Access Framework)**: Used for folder selection and advanced file operations.
- **Data Extraction**: Custom rules defined for data extraction (`data_extraction_rules.xml`) and backup (`backup_rules.xml`).

## Internal Integrations
- **AppLogger**: Centralized logging system (`util/AppLogger.kt`) for debugging and user-facing error logs.
- **WorkManager**: Used for background tasks like video scanning or maintenance if needed.

## External Services
- **None**: The application is designed as a standalone, offline-first local video player.
- **No Analytics/Ads**: No traces of Firebase, Google Analytics, or advertisement SDKs found.

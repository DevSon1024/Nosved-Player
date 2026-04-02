<div align="center">

# Nosved Player

<img src="Screenshots/logo.png" width="160" height="160" style="border-radius: 80px;"/>

[![GitHub release](https://img.shields.io/github/v/release/DevSon1024/Nosved-Player?label=Release&logo=github)](https://github.com/DevSon1024/Nosved-Player/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/DevSon1024/Nosved-Player/total.svg?logo=github)](https://github.com/DevSon1024/Nosved-Player/releases)
[![License: MIT](https://img.shields.io/github/license/DevSon1024/Nosved-Player)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-00DE7A.svg?logo=android)](https://developer.android.com)
[![Min SDK](<https://img.shields.io/badge/Min%20SDK-26%20(Oreo)-blue.svg>)](https://developer.android.com)

</div>

**Nosved Player** is a clean, modern, and high-performance local video player for Android. Built from the ground up using **Jetpack Compose** and **Media3 (ExoPlayer)**, it delivers a premium media experience with a focus on simplicity, fluidity, and Material You design.

---

## 📸 Screenshots

### Home & Navigation

<div align="center">
<img src="Screenshots/1-HomePage_With_HistoryCard.jpg" width="200" style="border-radius:16px; margin:6px;"/>
<img src="Screenshots/2-FolderView-ViewMode_AllFolders.jpg" width="200" style="border-radius:16px; margin:6px;"/>
<img src="Screenshots/3-VideoListView.jpg" width="200" style="border-radius:16px; margin:6px;"/>
</div>

### Sort, View Settings & Rotary Wheel

<div align="center">
<img src="Screenshots/4-View_Settings.jpg" width="200" style="border-radius:16px; margin:6px;"/>
<img src="Screenshots/5-RotterySortWheel.jpg" width="200" style="border-radius:16px; margin:6px;"/>
</div>

### Settings & About

<div align="center">
<img src="Screenshots/6-SettingsScreen.jpg" width="200" style="border-radius:16px; margin:6px;"/>
<img src="Screenshots/7-DisplayScreen.jpg" width="200" style="border-radius:16px; margin:6px;"/>
<img src="Screenshots/8-AboutScreen.jpg" width="200" style="border-radius:16px; margin:6px;"/>
</div>

### Player - Default & YouTube Style (Landscape)

<div align="center">
<img src="Screenshots/9-PlayerScreen-Default_Style.jpg" width="640" style="border-radius:16px; margin:6px; display:block;"/>
<img src="Screenshots/10-PlayerScreen-Youtube_Style.jpg" width="640" style="border-radius:16px; margin:6px; display:block;"/>
<img src="Screenshots/11-UpNext-YoutubeStylePlayer.jpg" width="640" style="border-radius:16px; margin:6px; display:block;"/>
</div>

---

## ✨ Key Features

### 🎬 Dual Player UI

- **Default Style** - Clean, minimal controls with gesture-based brightness & volume adjustment.
- **YouTube Style** - Modern immersive controls with smooth multi-tap seek gestures, a swipe-up settings panel, Replay / Forward buttons, and an **Up Next** queue overlay.
- Switch between both styles anytime from **Settings → Player** or **Playback Settings → Player Style**.

### 🎡 Rotary Sort Wheel

- A unique **radial wheel picker** for sorting videos - spin to select sort field, tap centre buttons to toggle Ascending / Descending.
- Smooth spring-physics animations, Material You colour theming, and a polished system-bar-aware overlay.

### 🎨 Material You Dynamic Theme

- Full **Material 3** colour system with light and dark schemes.
- Optional **Dynamic Colour** - adapts to your wallpaper on Android 12+ devices.
- Status bar and navigation bar colours blend seamlessly with the app background.

### 📁 Smart Library

- **Continue Watching** - quick-access history cards with progress bars; long-press to delete.
- **Folder view** with multiple layout modes (All Folders, Files, Explorer, List, Grid).
- **Grid Columns** customisation for the video list.

### ⚡ Performance & Compatibility

- Powered by **Google Media3 / ExoPlayer** with integrated **FFmpeg** decoders (via Nextlib) for broad format support.
- Fast thumbnails using a custom **MediaStore-optimised Coil** integration.
- **Subtitle support** - internal & external tracks (SRT, ASS, VTT, etc.).
- **Gesture controls** - swipe for brightness, volume, seek, and aspect-ratio switching.

---

## 🛠️ Technical Stack

| Layer               | Technology                                                                                                                                           |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| **UI Framework**    | [Jetpack Compose](https://developer.android.com/jetpack/compose)                                                                                     |
| **Design System**   | Material 3 (Material You)                                                                                                                            |
| **Playback Engine** | [Android Media3 / ExoPlayer](https://github.com/androidx/media)                                                                                      |
| **Native Decoders** | FFmpeg via [Nextlib](https://github.com/anilbeesetti/nextlib)                                                                                        |
| **Image Loading**   | [Coil](https://github.com/coil-kt/coil) (VideoFrame + MediaStore fetchers)                                                                           |
| **Persistence**     | [Room](https://developer.android.com/training/data-storage/room) + [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| **Architecture**    | MVVM + Kotlin Coroutines + StateFlow                                                                                                                 |
| **Language**        | Kotlin 100%                                                                                                                                          |

---

## 🚀 Getting Started

### Requirements

- Android **API 26+** (Android 8.0 Oreo or higher)
- [Android Studio Meerkat](https://developer.android.com/studio) or newer

### Building from Source

```bash
git clone https://github.com/DevSon1024/Nosved-Player.git
```

1. Open the project in **Android Studio**.
2. **Sync** Project with Gradle Files.
3. Run the `app` module on your device or emulator.

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Developed By

**Devendra Sonawane** (DevSon)

Made with ♥ and Kotlin.

[![Telegram](https://img.shields.io/badge/Telegram-Nosved__Player-2CA5E0?logo=telegram)](https://t.me/Nosved_Player)
[![GitHub](https://img.shields.io/badge/GitHub-DevSon1024-181717?logo=github)](https://github.com/DevSon1024)

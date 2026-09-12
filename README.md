<div align="center">

<br>

<img src="Screenshots/NosvedPlayer Logo.png" alt="Nosved Player" width="280" />

<br>

<p align="center">
  <strong>A high-performance, native Android video player  where desktop-class power meets mobile elegance.</strong>
</p>

<br>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  &nbsp;
  <img src="https://img.shields.io/badge/Kotlin-100%25-B125EA?style=for-the-badge&logo=kotlin&logoColor=white" />
  &nbsp;
  <img src="https://img.shields.io/badge/Engine-MPV-FF6B6B?style=for-the-badge&logoColor=white" />
  &nbsp;
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  &nbsp;
  <img src="https://img.shields.io/badge/License-MIT-00C7B7?style=for-the-badge" />
</p>

<p align="center">
  <a href="https://github.com/DevSon1024/Nosved-Player/releases">
    <img src="https://img.shields.io/github/v/release/DevSon1024/Nosved-Player?style=for-the-badge&color=B125EA&label=Latest%20Release" />
  </a>
  &nbsp;
  <a href="https://github.com/DevSon1024/Nosved-Player/releases">
    <img src="https://img.shields.io/github/downloads/DevSon1024/Nosved-Player/total?style=for-the-badge&color=3DDC84&label=Downloads&logo=github" />
  </a>
  &nbsp;
  <a href="https://github.com/DevSon1024/Nosved-Player/stargazers">
    <img src="https://img.shields.io/github/stars/DevSon1024/Nosved-Player?style=for-the-badge&color=FFD700&label=Stars&logo=github" />
  </a>
</p>

<br>

</div>

---

## ✦ What is Nosved Player?

Originally built on ExoPlayer, Nosved Player has been **completely re-engineered** to run on the **`mpv-android`** engine the same battle-tested media engine powering desktop MPV. This architectural leap merges a **minimalist Material Design 3 UI** with the raw decoding power of MPV, delivering unmatched format compatibility, hardware acceleration, and seamless video handling.

> **Result:** A video player that feels premium and native on Android, while handling every format, codec, and subtitle track your library can throw at it.

---

## Why MPV?

| Feature            | ExoPlayer | MPV Engine                   |
| ------------------ | --------- | ---------------------------- |
| Format Support     | Good      | **Exceptional (all codecs)** |
| HW Decoding        | Standard  | **mediacodec + HW+**         |
| Subtitle Rendering | Basic     | **Advanced ASS/SRT**         |
| Color Enhancement  | None      | **Real-time Gamma/Hue/Sat**  |
| Audio Boost        | Limited   | **Up to 200% amplification** |
| Config Flexibility | Fixed     | **mpv.conf in-app editor**   |

---

## Screenshots

### Library & Media Management

<p align="center">
  <a href="Screenshots/1_LibraryScreen.jpg"><img src="Screenshots/1_LibraryScreen.jpg" width="180" alt="Smart Library" /></a>
  &nbsp;
  <a href="Screenshots/2.VideoListScreen.jpg"><img src="Screenshots/2.VideoListScreen.jpg" width="180" alt="Video Explorer" /></a>
  &nbsp;
  <a href="Screenshots/3_VaultScreen.jpg"><img src="Screenshots/3_VaultScreen.jpg" width="180" alt="Encrypted Vault" /></a>
  &nbsp;
  <a href="Screenshots/4_SettingsScreen.jpg"><img src="Screenshots/4_SettingsScreen.jpg" width="180" alt="App Settings" /></a>
</p>

<p align="center">
  <sub><b> Smart Library &nbsp;&nbsp;|&nbsp;&nbsp; Video Explorer &nbsp;&nbsp;|&nbsp;&nbsp; Security Vault &nbsp;&nbsp;|&nbsp;&nbsp; Settings</b></sub>
</p>

<br>

<p align="center">
  <a href="Screenshots/5_FeedScreen.jpg"><img src="Screenshots/5_FeedScreen.jpg" width="180" alt="Online Feed" /></a>
  &nbsp;
  <a href="Screenshots/9_PlayerScreenCustomizerScreen.jpg"><img src="Screenshots/9_PlayerScreenCustomizerScreen.jpg" width="180" alt="Layout Customizer" /></a>
  &nbsp;
  <a href="Screenshots/10_StorageAnalyzer.jpg"><img src="Screenshots/10_StorageAnalyzer.jpg" width="180" alt="Storage Analyzer" /></a>
  &nbsp;
  <a href="Screenshots/11_MediaInformationBottomSheet.jpg"><img src="Screenshots/11_MediaInformationBottomSheet.jpg" width="180" alt="Media Information" /></a>
</p>

<p align="center">
  <sub><b> Feed Play &nbsp;&nbsp;|&nbsp;&nbsp;  Player Layout Customizer &nbsp;&nbsp;|&nbsp;&nbsp;  Storage Analyzer &nbsp;&nbsp;|&nbsp;&nbsp; Media Inspector</b></sub>
</p>

---

### Player & Playback Controls

<p align="center">
  <a href="Screenshots/6_PlayerScreen.jpg">
    <img src="Screenshots/6_PlayerScreen.jpg" width="760" alt="Player Interface & Controls" />
  </a>
</p>
<p align="center">
  <sub><b>⚡ High-Performance Playback Interface Gesture Controls · HW/SW Engine Switching · PIP · Enhance Mode</b></sub>
</p>

<br>

<p align="center">
  <a href="Screenshots/7_Queue.jpg"><img src="Screenshots/7_Queue.jpg" width="360" alt="Playback Queue" /></a>
  &nbsp;&nbsp;
  <a href="Screenshots/8_MoreOptions.jpg"><img src="Screenshots/8_MoreOptions.jpg" width="360" alt="Quick Options & Speed Controller" /></a>
</p>

<p align="center">
  <sub><b> Up Next Queue &nbsp;&nbsp;|&nbsp;&nbsp; Quick Controls & Playback Speed</b></sub>
</p>
<p align="center">
  <b> Screenshots from v1.3.0</b>
</p>

---

## Feature Highlights

<details>
<summary><b> Advanced Playback Engine</b></summary>
<br>

- **Dynamic Decoder Selection** Instantly switch between `Auto`, `Hardware (HW/HW+)`, and `Software (SW)` decoding on the fly
- **Smart Audio Boost** Safely amplify low-volume content up to **200%** without clipping
- **Rich Subtitle Support** Cycle tracks, adjust sync delays, customize fonts, and tweak scaling/offsets directly from the player
- **Smart Enhance Mode** Real-time hardware-level adjustments for **Brightness**, **Contrast**, **Saturation**, **Gamma**, and **Hue**

</details>

<details>
<summary><b> Clean, Native UI (Material Design 3)</b></summary>
<br>

- **Material You** Fully integrated with Android's Dynamic Color palette
- **AMOLED & Dark Themes** True black modes for battery saving and comfortable nighttime viewing
- **Unobtrusive Overlays** Transparent navigation bars, auto-hiding controls, and configurable quick-action buttons
- **Jetpack Compose UI** Fluid, jank-free browsing with smooth state-driven animations

</details>

<details>
<summary><b> Deep Customization & Gestures</b></summary>
<br>

- **Multi-finger Gestures** Configure 2-finger and 3-finger taps for rapid actions (Play/Pause, Fast Play, etc.)
- **Screen Edge Controls** Slide to adjust brightness and volume with customizable sensitivity
- **Layout Editor** Customize top and bottom control panels to fit your exact workflow
- **Configurable Seek & Speed** Adjustable seek durations and tap-to-speed parameters

</details>

<details>
<summary><b> Online Streaming & More</b></summary>
<br>

- **yt-dlp Integration** Seamless online video playback without leaving the app
- **In-App MPV Config Editor** Fine-tune the MPV engine via `mpv.conf` directly from the UI
- **Encrypted Vault** Protect sensitive videos with secure, hidden storage
- **Storage Analyzer** Visualize your media library's storage footprint

</details>

---

## Build It Yourself

### Prerequisites

| Requirement    | Version                 |
| -------------- | ----------------------- |
| Android Studio | Ladybug / Latest Stable |
| JDK            | 17+                     |
| Android SDK    | API 34+                 |

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/DevSon1024/Nosved-Player.git

# Navigate into the project
cd Nosved-Player

# Build a release APK
./gradlew assembleRelease
```

---

## 🙏 Acknowledgements

Special thanks to [**Ritesh Pandit (@Riteshp2001)**](https://github.com/Riteshp2001) and the [**mpvRx**](https://github.com/Riteshp2001/mpvRx) project for the inspiration and foundational work on:

| Contribution            | Description                                                                           |
| ----------------------- | ------------------------------------------------------------------------------------- |
| yt-dlp Online Streaming | Enabling seamless online video playback via yt-dlp within an MPV-based Android player |
| MPV Config Editor       | The in-app `mpv.conf` editor concept for tweaking the MPV engine directly from the UI |
| Thumbnail Generation    | Approach to generating and displaying video thumbnails within an MPV-backed player    |

---

## ⭐ Star History

<a href="https://www.star-history.com/?repos=Devson1024%2Fnosved-player&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=Devson1024/nosved-player&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=Devson1024/nosved-player&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=Devson1024/nosved-player&type=date&legend=top-left" />
 </picture>
</a>

---

<div align="center">

<br>

<p>
  <a href="https://github.com/DevSon1024/Nosved-Player/releases"><strong>Download Latest APK</strong></a>
  &nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="https://github.com/DevSon1024/Nosved-Player/issues"><strong>Report a Bug</strong></a>
  &nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="https://github.com/DevSon1024/Nosved-Player/discussions"><strong>Discussions</strong></a>
</p>

<br>

<sub>Built with ❤️ using Kotlin · Jetpack Compose · MPV Engine · Material Design 3</sub>

<br><br>

</div>

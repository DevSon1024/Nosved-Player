# Nosved Player (Nosved Player)

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-B125EA?logo=kotlin&logoColor=white)
[![Downloads](https://img.shields.io/github/downloads/DevSon1024/Nosved-Player/total?logo=github)](https://github.com/DevSon1024/Nosved-Player/releases)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

Nosved Player is a high-performance, native Android video player built with a focus on absolute playback smoothness, clean aesthetics, and extensive user customization.

Originally built as an ExoPlayer-based application, Nosved Player has been completely re-engineered under the hood to utilize the **mpv-android** engine. This architectural shift merges our minimalist Material Design UI with the raw decoding power of MPV, delivering unmatched format compatibility, hardware acceleration, and seamless video handling.

> ## Why MPV?

The transition from ExoPlayer to `is.xyz.mpv` allows Nosved Player to offer a truly desktop-class media experience on mobile. It brings native hardware decoding (`mediacodec`), superior subtitle rendering, and real-time color enhancement capabilities without sacrificing battery life or UI responsiveness.

> ## Key Features

> > ### Advanced Playback Engine

- **Dynamic Decoder Selection:** Instantly switch between Auto, Hardware (HW/HW+), and Software (SW) decoding on the fly.
- **Smart Audio Boost:** Amplify low-volume videos safely up to 200%.
- **Rich Subtitle Support:** Cycle tracks, adjust synchronization delays, customize fonts, and tweak scaling/offsets directly from the player.
- **Smart Enhance Mode:** Real-time hardware-level adjustments for Video Brightness, Contrast, Saturation, Gamma, and Hue.

> > ### Clean, Native UI

- **Material Design 3:** fully integrated with Android's Dynamic Color palette.
- **AMOLED & Dark Themes:** True black modes for battery saving and comfortable nighttime viewing.
- **Unobtrusive Overlays:** Transparent navigation bars, auto-hiding controls, and configurable quick-action buttons.
- **Smooth Navigation:** Jetpack Compose-driven UI for a fluid, jank-free browsing experience.

> > ### Deep Customization & Gestures

- **Multi-finger Gestures:** Configure 2-finger and 3-finger taps for rapid actions (Play/Pause, Fast Play, etc.).
- **Screen Edge Controls:** Slide to adjust brightness and volume, with customizable sensitivity.
- **Layout Editor:** Customize top and bottom control panels to fit your exact workflow.
- **Multiple Finger Gestures:** Configurable seek durations and tap-to-speed parameters.

> ## Building the Project

> > ### Prerequisites

- Android Studio (Latest Stable or Ladybug)
- JDK 17+
- Android SDK API 34+

> > ### Clone & Build

```bash
git clone https://github.com/DevSon1024/Nosved-Player.git
cd Nosved-Player
./gradlew assembleRelease
```

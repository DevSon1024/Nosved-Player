package com.devson.nvplayer.repository

enum class OrientationMode {
    SYSTEM_DEFAULT, LANDSCAPE, PORTRAIT, AUTO
}

enum class FullScreenMode {
    AUTO_SWITCH, STRETCH, CROP, FIT
}

enum class SoftButtonMode {
    AUTO_HIDE, SHOW, HIDE
}

enum class SubtitleFont {
    DEFAULT, SANS_SERIF, SERIF, MONOSPACE
}

enum class MultiFingerAction {
    PLAY_PAUSE, FAST_PLAY, MUTE, NONE, SCREENSHOT, PINCH_ZOOM
}

enum class DoubleTapAction {
    BOTH, PLAY_PAUSE, FAST_FORWARD, REWIND, NONE
}

data class PlaybackSettings(
    val seekDurationSeconds: Int = 10,
    val seekBarStyle: String = "line",
    val controlIconSize: String = "medium",
    val autoPlayEnabled: Boolean = false,
    val showSeekButtons: Boolean = true,
    val fastplaySpeed: Float = 2.0f,
    val orientationMode: OrientationMode = OrientationMode.SYSTEM_DEFAULT,
    val fullScreenMode: FullScreenMode = FullScreenMode.AUTO_SWITCH,
    val softButtonMode: SoftButtonMode = SoftButtonMode.AUTO_HIDE,
    val showElapsedTimeOverlay: Boolean = false,
    val showBatteryClockOverlay: Boolean = false,
    val showScreenRotationButton: Boolean = true,
    val pauseWhenObstructed: Boolean = true,
    val showRemainingTime: Boolean = false,
    val useSystemCaptionStyle: Boolean = false,
    val subtitleFont: SubtitleFont = SubtitleFont.DEFAULT,
    val isSubtitleBold: Boolean = false,
    val forceAssSubtitleOverride: Boolean = false,
    val seekGestureEnabled: Boolean = true,
    val seekSensitivity: Float = 0.5f,
    val brightnessGestureEnabled: Boolean = true,
    val brightnessSensitivity: Float = 0.5f,
    val volumeGestureEnabled: Boolean = true,
    val volumeSensitivity: Float = 0.5f,
    val twoFingerAction: MultiFingerAction = MultiFingerAction.PINCH_ZOOM,
    val threeFingerAction: MultiFingerAction = MultiFingerAction.FAST_PLAY,
    val longPressEnabled: Boolean = true,
    val longPressSpeed: Float = 2.0f,
    val doubleTapAction: DoubleTapAction = DoubleTapAction.BOTH,
    val subtitleTextSizeScale: Float = 1.0f,
    val subtitleBgStyle: Int = 1,
    val subtitleDelayMs: Long = 0L,
    val subtitleVerticalOffset: Float = 0f,
    val subtitleGesturesEnabled: Boolean = true,
    val customPlaybackSpeed: Float = 1.0f,
    val tapAndHoldSpeed: Float = 2.0f,
    val doubleTapSeekDuration: Long = 10000L
)

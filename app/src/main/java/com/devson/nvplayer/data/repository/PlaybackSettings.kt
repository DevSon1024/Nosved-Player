package com.devson.nvplayer.data.repository

import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.player.ytdlp.YtdlCodecPreference
import com.devson.nvplayer.player.ytdlp.YtdlContainerPreference
import com.devson.nvplayer.player.ytdlp.YtdlHdrPreference
import com.devson.nvplayer.player.ytdlp.YtdlPlaylistMode

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

enum class EnhanceMode {
    OFF, DEFAULT, CUSTOM
}

data class PlaybackSettings(
    val seekDurationSeconds: Int = 10,
    val seekBarStyle: String = "standard",
    val controlIconSize: String = "medium",
    val autoPlayEnabled: Boolean = false,
    val showNextPrevButtons: Boolean = true,
    val showSeekButtons: Boolean = true,
    val fastplaySpeed: Float = 2.0f,
    val orientationMode: OrientationMode = OrientationMode.SYSTEM_DEFAULT,
    val fullScreenMode: FullScreenMode = FullScreenMode.AUTO_SWITCH,
    val softButtonMode: SoftButtonMode = SoftButtonMode.AUTO_HIDE,
    val showBatteryClockOverlay: Boolean = false,
    val showScreenRotationButton: Boolean = true,
    val pauseWhenObstructed: Boolean = true,
    val showRemainingTime: Boolean = false,
    val useSystemCaptionStyle: Boolean = false,
    val subtitleFont: SubtitleFont = SubtitleFont.DEFAULT,
    val isSubtitleBold: Boolean = false,
    val forceAssSubtitleOverride: Boolean = false,
    val seekGestureEnabled: Boolean = true,
    val seekSpeedSecPerCm: Int = 10,
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
    val doubleTapSeekDuration: Long = 10000L,
    val screenshotLocation: String = "Pictures/Nosved Player/Screenshot",
    val blacklistedFolders: Set<String> = emptySet(),
    val keepAwakeAlways: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HW,
    val enhanceMode: EnhanceMode = EnhanceMode.OFF,
    val enhanceSaturation: Int = 0,
    val enhanceContrast: Int = 0,
    val enhanceBrightness: Int = 0,
    val enhanceGamma: Int = 0,
    val enhanceHue: Int = 0,
    // Landscape regions
    // TopLeft is always BACK_ARROW + VIDEO_TITLE (non-editable, enforced in PlayerScreen)
    val topLeftControls: String = "BACK_ARROW,VIDEO_TITLE",
    val topRightControls: String = "DECODER,SUBTITLES,AUDIO_TRACK,MORE_OPTIONS",
    val bottomLeftControls: String = "LOCK_CONTROLS,PICTURE_IN_PICTURE",
    val bottomRightControls: String = "ASPECT_RATIO,SCREEN_ROTATION",
    // Portrait regions 
    // Separate from landscape so each orientation is independently configurable.
    // Portrait TopLeft is always BACK_ARROW + VIDEO_TITLE (non-editable, enforced in PlayerScreen)
    val portraitTopLeftControls: String = "BACK_ARROW,VIDEO_TITLE",
    val portraitTopRightControls: String = "SUBTITLES,AUDIO_TRACK,MORE_OPTIONS",
    val portraitBottomControls: String = "DECODER,CHAPTERS,SMART_ENHANCE,ASPECT_RATIO,SCREEN_ROTATION",
    val aspectMode: AspectMode = AspectMode.FIT,
    val backgroundPlayEnabled: Boolean = false,
    // yt-dlp Settings
    val ytdlFormat: String = "",
    val ytdlQuality: Int = -1, // -1 for any
    val preferH264: Boolean = false,
    val codecPreference: YtdlCodecPreference = YtdlCodecPreference.AUTO,
    val maxFps: Int = 0,
    val hdrPreference: YtdlHdrPreference = YtdlHdrPreference.ANY,
    val containerPreference: YtdlContainerPreference = YtdlContainerPreference.ANY,
    val formatSort: String = "",
    val mergeOutputFormat: String = "",
    val writeSubs: Boolean = true,
    val writeAutoSubs: Boolean = false,
    val subtitleLanguages: String = "",
    val customUserAgent: String = "",
    val referer: String = "",
    val cookiesFile: String = "",
    val proxy: String = "",
    val extractorArgs: String = "",
    val geoBypass: Boolean = false,
    val playlistMode: YtdlPlaylistMode = YtdlPlaylistMode.DEFAULT,
    val liveFromStart: Boolean = false,
    val sponsorBlockMark: String = "",
    val sponsorBlockRemove: String = "",
    val customRawOptions: String = "",
    val isDataSaverEnabled: Boolean = false,
    val isBottomLayoutEnabled: Boolean = false,
    val showControlGradients: Boolean = true
)



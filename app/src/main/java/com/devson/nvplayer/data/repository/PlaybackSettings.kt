package com.devson.nvplayer.data.repository

import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.player.model.AudioDecoderMode
import com.devson.nvplayer.domain.model.LayoutMode
import androidx.compose.runtime.Immutable

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

enum class FolderFilterMode {
    NONE, WHITELIST, BLACKLIST
}

@Immutable
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
    val softButtonMode: SoftButtonMode = SoftButtonMode.HIDE,
    val showBatteryClockOverlay: Boolean = false,
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
    val whitelistedFolders: Set<String> = emptySet(),
    val folderFilterMode: FolderFilterMode = FolderFilterMode.NONE,
    val keepAwakeAlways: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HW,
    val useSwAudioDecoder: Boolean = false,
    val audioDecoderMode: AudioDecoderMode = AudioDecoderMode.AUTOMATIC,
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
    val ytdlQuality: Int = -1, // -1 for auto
    val customUserAgent: String = "",
    val isDataSaverEnabled: Boolean = false,
    val isBottomLayoutEnabled: Boolean = false,
    val showControlGradients: Boolean = true,
    val showUpNextQueue: Boolean = true,
    val queueLayoutMode: LayoutMode = LayoutMode.LIST,
    val isAmbientModeEnabled: Boolean = false,
    val ambientBlurStyle: AmbientBlurStyle = AmbientBlurStyle.GLOW,
    val saveBrightnessLevel: Boolean = false,
    val showStatsOverlay: Boolean = false
)



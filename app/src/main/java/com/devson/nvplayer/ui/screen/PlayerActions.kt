package com.devson.nvplayer.ui.screen

import androidx.compose.runtime.Immutable
import com.devson.nvplayer.data.repository.AmbientBlurStyle
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.data.repository.DoubleTapAction
import com.devson.nvplayer.data.repository.EnhanceMode
import com.devson.nvplayer.data.repository.FullScreenMode
import com.devson.nvplayer.data.repository.MultiFingerAction
import com.devson.nvplayer.data.repository.OrientationMode
import com.devson.nvplayer.data.repository.SoftButtonMode
import com.devson.nvplayer.data.repository.SubtitleFont

/**
 * Grouped action holders to reduce parameter explosion and keep Composable method
 * instruction bytecode under ART runtime JIT compiler limits.
 */
@Immutable
data class PlayerSettingsActions(
    val onSpeedSelected: (Float) -> Unit = {},
    val onUpdateDoubleTapAction: (DoubleTapAction) -> Unit = {},
    val onUpdateDoubleTapSeekDuration: (Long) -> Unit = {},
    val onUpdateTwoFingerAction: (MultiFingerAction) -> Unit = {},
    val onUpdateThreeFingerAction: (MultiFingerAction) -> Unit = {},
    val onUpdateLongPressEnabled: (Boolean) -> Unit = {},
    val onUpdateTapAndHoldSpeed: (Float) -> Unit = {},
    val onUpdateLongPressSpeed: (Float) -> Unit = {},
    val onUpdateOrientationMode: (OrientationMode) -> Unit = {},
    val onUpdateFullScreenMode: (FullScreenMode) -> Unit = {},
    val onUpdateAspectMode: (AspectMode) -> Unit = {},
    val onUpdateSoftButtonMode: (SoftButtonMode) -> Unit = {},
    val onUpdateControlIconSize: (String) -> Unit = {},
    val onUpdateSeekBarStyle: (String) -> Unit = {},
    val onUpdateAutoPlayEnabled: (Boolean) -> Unit = {},
    val onUpdateShowSeekButtons: (Boolean) -> Unit = {},
    val onUpdateShowNextPrevButtons: (Boolean) -> Unit = {},
    val onUpdateShowRemainingTime: (Boolean) -> Unit = {},
    val onUpdateShowBatteryClockOverlay: (Boolean) -> Unit = {},
    val onUpdatePauseWhenObstructed: (Boolean) -> Unit = {},
    val onUpdateKeepAwakeAlways: (Boolean) -> Unit = {},
    val onUpdateIsBottomLayoutEnabled: (Boolean) -> Unit = {},
    val onUpdateShowControlGradients: (Boolean) -> Unit = {},
    val onUpdateShowUpNextQueue: (Boolean) -> Unit = {},
    val onUpdateIsAmbientModeEnabled: (Boolean) -> Unit = {},
    val onUpdateAmbientBlurStyle: (AmbientBlurStyle) -> Unit = {},
    val onUpdateSaveBrightnessLevel: (Boolean) -> Unit = {},
    val onUpdateBackgroundPlayEnabled: (Boolean) -> Unit = {}
)

@Immutable
data class PlayerSubtitleActions(
    val onSelectSubtitleTrack: (Int) -> Unit = {},
    val onSetSubtitleDelay: (Long) -> Unit = {},
    val onSeekNextSubtitle: () -> Unit = {},
    val onSeekPrevSubtitle: () -> Unit = {},
    val onUpdateUseSystemCaptionStyle: (Boolean) -> Unit = {},
    val onUpdateSubtitleFont: (SubtitleFont) -> Unit = {},
    val onUpdateIsSubtitleBold: (Boolean) -> Unit = {},
    val onUpdateForceAssSubtitleOverride: (Boolean) -> Unit = {},
    val onUpdateSubtitleTextSizeScale: (Float) -> Unit = {},
    val onUpdateSubtitleBgStyle: (Int) -> Unit = {},
    val onUpdateSubtitleDelay: (Long) -> Unit = {},
    val onUpdateSubtitleVerticalOffset: (Float) -> Unit = {},
    val onUpdateSubtitleGesturesEnabled: (Boolean) -> Unit = {}
)

@Immutable
data class PlayerEnhanceActions(
    val onUpdateEnhanceMode: (EnhanceMode) -> Unit = {},
    val onUpdateEnhanceSaturation: (Int) -> Unit = {},
    val onUpdateEnhanceContrast: (Int) -> Unit = {},
    val onUpdateEnhanceBrightness: (Int) -> Unit = {},
    val onUpdateEnhanceGamma: (Int) -> Unit = {},
    val onUpdateEnhanceHue: (Int) -> Unit = {}
)

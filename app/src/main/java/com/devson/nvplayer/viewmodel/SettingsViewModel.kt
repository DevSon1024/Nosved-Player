package com.devson.nvplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.repository.PlaybackSettingsRepository
import com.devson.nvplayer.ui.theme.AppThemePalette
import com.devson.nvplayer.ui.theme.AppThemePaletteHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = PlaybackSettingsRepository(application.applicationContext)
    private val viewSettingsRepo = com.devson.nvplayer.repository.ViewSettingsRepository(application.applicationContext)

    val viewSettings = viewSettingsRepo.viewSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.devson.nvplayer.model.ViewSettings())

    /**
     * Returns the DefaultScreen that was persisted in SharedPreferences.
     * Unlike [viewSettings].value (which starts with the data-class default until the flow
     * subscribes), this reads the repository's MutableStateFlow which is populated
     * synchronously in ViewSettingsRepository's constructor - safe to call at composition time.
     */
    fun getInitialDefaultScreen(): com.devson.nvplayer.model.DefaultScreen =
        viewSettingsRepo.viewSettingsFlow.value.defaultScreen

    /**
     * Emits null = follow system, true = force dark, false = force light.
     */
    val isDarkTheme: StateFlow<Boolean?> = settingsRepo.isDarkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isAmoledTheme: StateFlow<Boolean> = settingsRepo.isAmoledThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val defaultAudioLang: StateFlow<String> = settingsRepo.defaultAudioLangFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val defaultSubtitleLang: StateFlow<String> = settingsRepo.defaultSubtitleLangFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val isDeveloperMode: StateFlow<Boolean> = settingsRepo.isDeveloperModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * true = Modern-style player UI, false = default player UI.
     * Persisted via PlaybackSettingsRepository / DataStore.
     */
    val useModernPlayerStyle: StateFlow<Boolean> = settingsRepo.useModernPlayerStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * null  = DataStore not yet loaded (show blank splash)
     * false = first launch, show onboarding
     * true  = already seen onboarding, go straight to Home
     */
    val hasSeenOnboarding: StateFlow<Boolean?> = settingsRepo.hasSeenOnboardingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * true = use Material You (wallpaper-based) colours, false = Nosved custom palette.
     * Only takes visual effect on API 31+.
     */
    val dynamicColor: StateFlow<Boolean> = settingsRepo.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The currently selected built-in colour palette (defaults to BLUE / Nosved Blue). */
    val selectedPalette: StateFlow<AppThemePalette> = settingsRepo.selectedPaletteFlow
        .map { key -> AppThemePaletteHelper.fromKey(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemePalette.BLUE)

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch { settingsRepo.setDarkTheme(isDark) }
    }

    /** Clears any explicit dark/light override - theme follows the device system setting. */
    fun resetDarkTheme() {
        viewModelScope.launch { settingsRepo.resetDarkTheme() }
    }

    fun setAmoledTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAmoledTheme(enabled) }
    }

    fun setDefaultAudioLang(langCode: String) {
        viewModelScope.launch { settingsRepo.setDefaultAudioLanguage(langCode) }
    }

    fun setDefaultSubtitleLang(langCode: String) {
        viewModelScope.launch { settingsRepo.setDefaultSubtitleLanguage(langCode) }
    }

    fun enableDeveloperMode() {
        viewModelScope.launch { settingsRepo.setDeveloperMode(true) }
    }

    fun setModernPlayerStyle(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setModernPlayerStyle(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    }

    fun setSelectedPalette(palette: AppThemePalette) {
        viewModelScope.launch { settingsRepo.setSelectedPalette(palette.name) }
    }

    /** Call when the user finishes or skips the onboarding screen. */
    fun markOnboardingComplete() {
        viewModelScope.launch { settingsRepo.setHasSeenOnboarding(true) }
    }

    fun updateRecognizeNoMedia(recognize: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateRecognizeNoMedia(recognize) }
    }

    fun updateShowHiddenFiles(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowHiddenFiles(show) }
    }

    fun updateShowQuickFab(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowQuickFab(show) }
    }

    fun updateSelectByThumbnail(select: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateSelectByThumbnail(select) }
    }

    fun updateEnableFabPreview(enable: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateEnableFabPreview(enable) }
    }

    fun updateScanFoldersList(folders: Set<String>) {
        viewModelScope.launch { viewSettingsRepo.updateScanFoldersList(folders) }
    }

    fun updateShowHistoryCard(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowHistoryCard(show) }
    }

    fun updateShowVideoCard(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowVideoCard(show) }
    }

    fun updateShowStorageTracker(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowStorageTracker(show) }
    }

    fun updateDefaultScreen(screen: com.devson.nvplayer.model.DefaultScreen) {
        viewModelScope.launch { viewSettingsRepo.updateDefaultScreen(screen) }
    }

    val playbackSettings = settingsRepo.playbackSettingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.devson.nvplayer.repository.PlaybackSettings(
                seekDurationSeconds = 10,
                seekBarStyle = "line",
                controlIconSize = "medium",
                autoPlayEnabled = false,
                showSeekButtons = true,
                fastplaySpeed = 2.0f,
                orientationMode = com.devson.nvplayer.repository.OrientationMode.SYSTEM_DEFAULT,
                fullScreenMode = com.devson.nvplayer.repository.FullScreenMode.AUTO_SWITCH,
                softButtonMode = com.devson.nvplayer.repository.SoftButtonMode.AUTO_HIDE,
                showElapsedTimeOverlay = false,
                showBatteryClockOverlay = false,
                showScreenRotationButton = true,
                pauseWhenObstructed = true,
                showRemainingTime = false,
                useSystemCaptionStyle = false,
                subtitleFont = com.devson.nvplayer.repository.SubtitleFont.DEFAULT,
                isSubtitleBold = false,
                forceAssSubtitleOverride = false,
                // New Gesture Defaults
                seekGestureEnabled = true,
                seekSpeedSecPerCm = 10,
                brightnessGestureEnabled = true,
                brightnessSensitivity = 0.5f,
                volumeGestureEnabled = true,
                volumeSensitivity = 0.5f,
                twoFingerAction = com.devson.nvplayer.repository.MultiFingerAction.PLAY_PAUSE,
                threeFingerAction = com.devson.nvplayer.repository.MultiFingerAction.FAST_PLAY,
                longPressEnabled = true,
                longPressSpeed = 2.0f,
                doubleTapAction = com.devson.nvplayer.repository.DoubleTapAction.BOTH,
                customPlaybackSpeed = 1.0f,
                tapAndHoldSpeed = 2.0f,
                doubleTapSeekDuration = 10000L,
                screenshotLocation = "Pictures/NVPlayer/Screenshot"
            )
        )

    fun updateSeekBarStyle(style: String) {
        viewModelScope.launch { settingsRepo.updateSeekBarStyle(style) }
    }

    fun updateOrientationMode(mode: com.devson.nvplayer.repository.OrientationMode) {
        viewModelScope.launch { settingsRepo.updateOrientationMode(mode) }
    }

    fun updateFullScreenMode(mode: com.devson.nvplayer.repository.FullScreenMode) {
        viewModelScope.launch { settingsRepo.updateFullScreenMode(mode) }
    }

    fun updateSoftButtonMode(mode: com.devson.nvplayer.repository.SoftButtonMode) {
        viewModelScope.launch { settingsRepo.updateSoftButtonMode(mode) }
    }

    fun updateShowElapsedTimeOverlay(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowElapsedTimeOverlay(show) }
    }

    fun updateShowBatteryClockOverlay(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowBatteryClockOverlay(show) }
    }

    fun updateShowScreenRotationButton(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowScreenRotationButton(show) }
    }

    fun updatePauseWhenObstructed(pause: Boolean) {
        viewModelScope.launch { settingsRepo.updatePauseWhenObstructed(pause) }
    }

    fun updateShowRemainingTime(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowRemainingTime(show) }
    }

    val isNavBarTransparent: StateFlow<Boolean> = settingsRepo.isNavBarTransparentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setNavBarTransparent(transparent: Boolean) {
        viewModelScope.launch { settingsRepo.setNavBarTransparent(transparent) }
    }

    fun updateUseSystemCaptionStyle(useSystem: Boolean) {
        viewModelScope.launch { settingsRepo.updateUseSystemCaptionStyle(useSystem) }
    }

    fun updateSubtitleFont(font: com.devson.nvplayer.repository.SubtitleFont) {
        viewModelScope.launch { settingsRepo.updateSubtitleFont(font) }
    }

    fun updateIsSubtitleBold(isBold: Boolean) {
        viewModelScope.launch { settingsRepo.updateIsSubtitleBold(isBold) }
    }

    fun updateForceAssSubtitleOverride(force: Boolean) {
        viewModelScope.launch { settingsRepo.updateForceAssSubtitleOverride(force) }
    }

    // --- New Gesture Dispatchers ---

    fun updateSeekGesture(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSeekGestureEnabled(enabled) }
    }

    fun updateSeekSpeedSecPerCm(speed: Int) {
        viewModelScope.launch { settingsRepo.updateSeekSpeedSecPerCm(speed) }
    }

    fun updateBrightnessGesture(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateBrightnessGestureEnabled(enabled) }
    }

    fun updateBrightnessSensitivity(sensitivity: Float) {
        viewModelScope.launch { settingsRepo.updateBrightnessSensitivity(sensitivity) }
    }

    fun updateVolumeGesture(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateVolumeGestureEnabled(enabled) }
    }

    fun updateVolumeSensitivity(sensitivity: Float) {
        viewModelScope.launch { settingsRepo.updateVolumeSensitivity(sensitivity) }
    }

    fun updateTwoFingerAction(action: com.devson.nvplayer.repository.MultiFingerAction) {
        viewModelScope.launch { settingsRepo.updateTwoFingerAction(action) }
    }

    fun updateThreeFingerAction(action: com.devson.nvplayer.repository.MultiFingerAction) {
        viewModelScope.launch { settingsRepo.updateThreeFingerAction(action) }
    }

    fun updateLongPressEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateLongPressEnabled(enabled) }
    }

    fun updateLongPressSpeed(speed: Float) {
        viewModelScope.launch { settingsRepo.updateLongPressSpeed(speed) }
    }

    fun updateDoubleTapAction(action: com.devson.nvplayer.repository.DoubleTapAction) {
        viewModelScope.launch { settingsRepo.updateDoubleTapAction(action) }
    }

    fun updateSubtitleTextSizeScale(scale: Float) {
        viewModelScope.launch { settingsRepo.updateSubtitleTextSizeScale(scale) }
    }

    fun updateSubtitleBgStyle(style: Int) {
        viewModelScope.launch { settingsRepo.updateSubtitleBgStyle(style) }
    }

    fun updateSubtitleDelay(delayMs: Long) {
        viewModelScope.launch { settingsRepo.updateSubtitleDelay(delayMs) }
    }

    fun updateSubtitleVerticalOffset(offset: Float) {
        viewModelScope.launch { settingsRepo.updateSubtitleVerticalOffset(offset) }
    }

    fun updateSubtitleGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSubtitleGesturesEnabled(enabled) }
    }

    fun updateCustomPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsRepo.updateCustomPlaybackSpeed(speed) }
    }

    fun updateTapAndHoldSpeed(speed: Float) {
        viewModelScope.launch { settingsRepo.updateTapAndHoldSpeed(speed) }
    }

    fun updateDoubleTapSeekDuration(durationMs: Long) {
        viewModelScope.launch { settingsRepo.updateDoubleTapSeekDuration(durationMs) }
    }

    fun updateShowSeekButtons(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowSeekButtons(show) }
    }

    fun updateControlIconSize(size: String) {
        viewModelScope.launch { settingsRepo.updateControlIconSize(size) }
    }

    fun updateAutoPlayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateAutoPlayEnabled(enabled) }
    }

    fun updateShowNextPrevButtons(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowNextPrevButtons(show) }
    }

    fun updateFastplaySpeed(speed: Float) {
        viewModelScope.launch { settingsRepo.updateFastplaySpeed(speed) }
    }

    fun updateSeekDurationSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepo.updateSeekDurationSeconds(seconds) }
    }

    fun updateScreenshotLocation(location: String) {
        viewModelScope.launch { settingsRepo.updateScreenshotLocation(location) }
    }
}
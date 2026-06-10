package com.devson.nvplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.repository.DoubleTapAction
import com.devson.nvplayer.data.repository.FullScreenMode
import com.devson.nvplayer.data.repository.MpvConfigRepository
import com.devson.nvplayer.data.repository.MultiFingerAction
import com.devson.nvplayer.data.repository.OrientationMode
import com.devson.nvplayer.data.repository.PlaybackSettings
import com.devson.nvplayer.data.repository.SoftButtonMode
import com.devson.nvplayer.data.repository.SubtitleFont
import com.devson.nvplayer.data.repository.PlaybackSettingsRepository
import com.devson.nvplayer.data.repository.ViewSettingsRepository
import com.devson.nvplayer.ui.theme.AppThemePalette
import com.devson.nvplayer.ui.theme.AppThemePaletteHelper
import com.devson.nvplayer.domain.model.PlayerButton
import com.devson.nvplayer.domain.model.ControlRegion
import com.devson.nvplayer.domain.model.DefaultScreen
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.domain.model.ThumbnailMode
import com.devson.nvplayer.domain.thumbnail.ThumbnailRepository
import com.devson.nvplayer.player.model.DecoderMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = PlaybackSettingsRepository(application.applicationContext)
    private val viewSettingsRepo = ViewSettingsRepository.getInstance(application.applicationContext)

    val viewSettings = viewSettingsRepo.viewSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewSettings())

    /**
     * Returns the DefaultScreen that was persisted in SharedPreferences.
     * Unlike [viewSettings].value (which starts with the data-class default until the flow
     * subscribes), this reads the repository's MutableStateFlow which is populated
     * synchronously in ViewSettingsRepository's constructor - safe to call at composition time.
     */
    fun getInitialDefaultScreen(): DefaultScreen =
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

    fun updateShowQuickFab(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowQuickFab(show) }
    }

    fun updateSelectByThumbnail(select: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateSelectByThumbnail(select) }
    }

    fun updateShowThumbnail(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowThumbnail(show) }
    }

    fun updateThumbnailMode(mode: ThumbnailMode) {
        viewModelScope.launch { viewSettingsRepo.updateThumbnailMode(mode) }
    }

    fun updateThumbnailFramePosition(pos: Float) {
        viewModelScope.launch { viewSettingsRepo.updateThumbnailFramePosition(pos) }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            ThumbnailRepository.getInstance(getApplication()).clearThumbnailCache()
        }
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

    fun updateShowStorageTracker(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowStorageTracker(show) }
    }

    fun updateDefaultScreen(screen: DefaultScreen) {
        viewModelScope.launch { viewSettingsRepo.updateDefaultScreen(screen) }
    }

    val playbackSettings = settingsRepo.playbackSettingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaybackSettings(
                seekDurationSeconds = 10,
                seekBarStyle = "standard",
                controlIconSize = "medium",
                autoPlayEnabled = false,
                showSeekButtons = true,
                fastplaySpeed = 2.0f,
                orientationMode = OrientationMode.SYSTEM_DEFAULT,
                fullScreenMode = FullScreenMode.AUTO_SWITCH,
                softButtonMode = SoftButtonMode.AUTO_HIDE,
                showBatteryClockOverlay = false,
                showScreenRotationButton = true,
                pauseWhenObstructed = true,
                showRemainingTime = false,
                useSystemCaptionStyle = false,
                subtitleFont = SubtitleFont.DEFAULT,
                isSubtitleBold = false,
                forceAssSubtitleOverride = false,
                // New Gesture Defaults
                seekGestureEnabled = true,
                seekSpeedSecPerCm = 10,
                brightnessGestureEnabled = true,
                brightnessSensitivity = 0.5f,
                volumeGestureEnabled = true,
                volumeSensitivity = 0.5f,
                twoFingerAction = MultiFingerAction.PLAY_PAUSE,
                threeFingerAction = MultiFingerAction.FAST_PLAY,
                longPressEnabled = true,
                longPressSpeed = 2.0f,
                doubleTapAction = DoubleTapAction.BOTH,
                customPlaybackSpeed = 1.0f,
                tapAndHoldSpeed = 2.0f,
                doubleTapSeekDuration = 10000L,
                screenshotLocation = "Pictures/NVPlayer/Screenshot",
                keepAwakeAlways = false,
                isBottomLayoutEnabled = false,
                showControlGradients = true
            )
        )

    fun updateSeekBarStyle(style: String) {
        viewModelScope.launch { settingsRepo.updateSeekBarStyle(style) }
    }

    fun updateOrientationMode(mode: OrientationMode) {
        viewModelScope.launch { settingsRepo.updateOrientationMode(mode) }
    }

    fun updateFullScreenMode(mode: FullScreenMode) {
        viewModelScope.launch { settingsRepo.updateFullScreenMode(mode) }
    }

    fun updateSoftButtonMode(mode: SoftButtonMode) {
        viewModelScope.launch { settingsRepo.updateSoftButtonMode(mode) }
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

    fun updateSubtitleFont(font: SubtitleFont) {
        viewModelScope.launch { settingsRepo.updateSubtitleFont(font) }
    }

    fun updateIsSubtitleBold(isBold: Boolean) {
        viewModelScope.launch { settingsRepo.updateIsSubtitleBold(isBold) }
    }

    fun updateForceAssSubtitleOverride(force: Boolean) {
        viewModelScope.launch { settingsRepo.updateForceAssSubtitleOverride(force) }
    }

    // New Gesture Dispatchers

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

    fun updateTwoFingerAction(action: MultiFingerAction) {
        viewModelScope.launch { settingsRepo.updateTwoFingerAction(action) }
    }

    fun updateThreeFingerAction(action: MultiFingerAction) {
        viewModelScope.launch { settingsRepo.updateThreeFingerAction(action) }
    }

    fun updateLongPressEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateLongPressEnabled(enabled) }
    }

    fun updateLongPressSpeed(speed: Float) {
        viewModelScope.launch { settingsRepo.updateLongPressSpeed(speed) }
    }

    fun updateDoubleTapAction(action: DoubleTapAction) {
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

    fun addToBlacklist(paths: List<String>) {
        viewModelScope.launch {
            val current = settingsRepo.playbackSettingsFlow.value.blacklistedFolders
            settingsRepo.updateBlacklistedFolders(current + paths)
        }
    }

    fun removeFromBlacklist(path: String) {
        viewModelScope.launch {
            val current = settingsRepo.playbackSettingsFlow.value.blacklistedFolders
            settingsRepo.updateBlacklistedFolders(current - path)
        }
    }

    fun clearBlacklist() {
        viewModelScope.launch {
            settingsRepo.updateBlacklistedFolders(emptySet())
        }
    }

    private val _mpvConfigFlow = MutableStateFlow("")
    val mpvConfigFlow: StateFlow<String> = _mpvConfigFlow.asStateFlow()

    fun loadMpvConfig() {
        viewModelScope.launch {
            _mpvConfigFlow.value = MpvConfigRepository.loadMpvConfig(getApplication())
        }
    }

    fun saveMpvConfig(content: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = MpvConfigRepository.saveMpvConfig(getApplication(), content)
            if (success) {
                _mpvConfigFlow.value = content
            }
            onResult(success)
        }
    }

    fun updateKeepAwakeAlways(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateKeepAwakeAlways(enabled) }
    }

    fun updateBackgroundPlayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateBackgroundPlayEnabled(enabled) }
    }

    fun updateDecoderMode(mode: DecoderMode) {
        viewModelScope.launch { settingsRepo.updateDecoderMode(mode) }
    }

    val topLeftControls: StateFlow<List<PlayerButton>> = settingsRepo.playbackSettingsFlow
        .map { parseControls(it.topLeftControls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parseControls("BACK_ARROW,VIDEO_TITLE"))

    val topRightControls: StateFlow<List<PlayerButton>> = settingsRepo.playbackSettingsFlow
        .map { parseControls(it.topRightControls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parseControls("DECODER,SUBTITLES,AUDIO_TRACK,MORE_OPTIONS"))

    val bottomLeftControls: StateFlow<List<PlayerButton>> = settingsRepo.playbackSettingsFlow
        .map { parseControls(it.bottomLeftControls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parseControls("LOCK_CONTROLS,PICTURE_IN_PICTURE"))

    val bottomRightControls: StateFlow<List<PlayerButton>> = settingsRepo.playbackSettingsFlow
        .map { parseControls(it.bottomRightControls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parseControls("ASPECT_RATIO,SCREEN_ROTATION"))

    val portraitTopLeftControls: StateFlow<List<PlayerButton>> = settingsRepo.playbackSettingsFlow
        .map { parseControls(it.portraitTopLeftControls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parseControls("BACK_ARROW,VIDEO_TITLE"))

    val portraitTopRightControls: StateFlow<List<PlayerButton>> = settingsRepo.playbackSettingsFlow
        .map { parseControls(it.portraitTopRightControls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parseControls("SUBTITLES,AUDIO_TRACK,MORE_OPTIONS"))

    val portraitBottomControls: StateFlow<List<PlayerButton>> = settingsRepo.playbackSettingsFlow
        .map { parseControls(it.portraitBottomControls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), parseControls("DECODER,CHAPTERS,SMART_ENHANCE,ASPECT_RATIO,SCREEN_ROTATION"))

    private fun parseControls(value: String): List<PlayerButton> {
        if (value.isBlank()) return emptyList()
        return value.split(',').mapNotNull { runCatching { PlayerButton.valueOf(it) }.getOrNull() }
    }

    fun updateControls(region: ControlRegion, controls: List<PlayerButton>) {
        val controlsStr = controls.joinToString(",")
        viewModelScope.launch {
            when (region) {
                ControlRegion.TOP_LEFT -> settingsRepo.updateTopLeftControls(controlsStr)
                ControlRegion.TOP_RIGHT -> settingsRepo.updateTopRightControls(controlsStr)
                ControlRegion.BOTTOM_LEFT -> settingsRepo.updateBottomLeftControls(controlsStr)
                ControlRegion.BOTTOM_RIGHT -> settingsRepo.updateBottomRightControls(controlsStr)
                ControlRegion.PORTRAIT_TOP_LEFT -> settingsRepo.updatePortraitTopLeftControls(controlsStr)
                ControlRegion.PORTRAIT_TOP_RIGHT -> settingsRepo.updatePortraitTopRightControls(controlsStr)
                ControlRegion.PORTRAIT_BOTTOM -> settingsRepo.updatePortraitBottomControls(controlsStr)
            }
        }
    }

    fun updateYtdlFormat(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlFormat(value) }
    }
    fun updateYtdlQuality(value: Int) {
        viewModelScope.launch { settingsRepo.updateYtdlQuality(value) }
    }
    fun updatePreferH264(value: Boolean) {
        viewModelScope.launch { settingsRepo.updatePreferH264(value) }
    }
    fun updateYtdlCodecPreference(value: com.devson.nvplayer.player.ytdlp.YtdlCodecPreference) {
        viewModelScope.launch { settingsRepo.updateYtdlCodecPreference(value) }
    }
    fun updateYtdlMaxFps(value: Int) {
        viewModelScope.launch { settingsRepo.updateYtdlMaxFps(value) }
    }
    fun updateYtdlHdrPreference(value: com.devson.nvplayer.player.ytdlp.YtdlHdrPreference) {
        viewModelScope.launch { settingsRepo.updateYtdlHdrPreference(value) }
    }
    fun updateYtdlContainerPreference(value: com.devson.nvplayer.player.ytdlp.YtdlContainerPreference) {
        viewModelScope.launch { settingsRepo.updateYtdlContainerPreference(value) }
    }
    fun updateYtdlFormatSort(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlFormatSort(value) }
    }
    fun updateYtdlMergeOutputFormat(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlMergeOutputFormat(value) }
    }
    fun updateYtdlWriteSubs(value: Boolean) {
        viewModelScope.launch { settingsRepo.updateYtdlWriteSubs(value) }
    }
    fun updateYtdlWriteAutoSubs(value: Boolean) {
        viewModelScope.launch { settingsRepo.updateYtdlWriteAutoSubs(value) }
    }
    fun updateYtdlSubtitleLanguages(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlSubtitleLanguages(value) }
    }
    fun updateYtdlCustomUserAgent(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlCustomUserAgent(value) }
    }
    fun updateYtdlReferer(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlReferer(value) }
    }
    fun updateYtdlCookiesFile(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlCookiesFile(value) }
    }
    fun updateYtdlProxy(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlProxy(value) }
    }
    fun updateYtdlExtractorArgs(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlExtractorArgs(value) }
    }
    fun updateYtdlGeoBypass(value: Boolean) {
        viewModelScope.launch { settingsRepo.updateYtdlGeoBypass(value) }
    }
    fun updateYtdlPlaylistMode(value: com.devson.nvplayer.player.ytdlp.YtdlPlaylistMode) {
        viewModelScope.launch { settingsRepo.updateYtdlPlaylistMode(value) }
    }
    fun updateYtdlLiveFromStart(value: Boolean) {
        viewModelScope.launch { settingsRepo.updateYtdlLiveFromStart(value) }
    }
    fun updateYtdlSponsorBlockMark(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlSponsorBlockMark(value) }
    }
    fun updateYtdlSponsorBlockRemove(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlSponsorBlockRemove(value) }
    }
    fun updateYtdlCustomRawOptions(value: String) {
        viewModelScope.launch { settingsRepo.updateYtdlCustomRawOptions(value) }
    }

    fun updateIsBottomLayoutEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateIsBottomLayoutEnabled(enabled) }
    }

    fun updateShowControlGradients(show: Boolean) {
        viewModelScope.launch { settingsRepo.updateShowControlGradients(show) }
    }

    override fun onCleared() {
        super.onCleared()
        // RELEASE FIX: Unregister the SharedPreferences listener to break the strong reference
        // that SharedPreferences holds to the settingsRepo lambda (prevents GC leak in release).
        settingsRepo.close()
    }
}

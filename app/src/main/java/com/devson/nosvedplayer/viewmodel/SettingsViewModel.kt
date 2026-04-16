package com.devson.nosvedplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.repository.PlaybackSettingsRepository
import com.devson.nosvedplayer.ui.theme.AppThemePalette
import com.devson.nosvedplayer.ui.theme.AppThemePaletteHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = PlaybackSettingsRepository(application.applicationContext)
    private val viewSettingsRepo = com.devson.nosvedplayer.repository.ViewSettingsRepository(application.applicationContext)

    val viewSettings = viewSettingsRepo.viewSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.devson.nosvedplayer.model.ViewSettings())

    /**
     * Emits null = follow system, true = force dark, false = force light.
     */
    val isDarkTheme: StateFlow<Boolean?> = settingsRepo.isDarkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isDeveloperMode: StateFlow<Boolean> = settingsRepo.isDeveloperModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * true = YouTube-style player UI, false = default player UI.
     * Persisted via PlaybackSettingsRepository / DataStore.
     */
    val useYoutubePlayerStyle: StateFlow<Boolean> = settingsRepo.useYoutubePlayerStyleFlow
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

    fun enableDeveloperMode() {
        viewModelScope.launch { settingsRepo.setDeveloperMode(true) }
    }

    fun setYoutubePlayerStyle(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setYoutubePlayerStyle(enabled) }
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

    fun updateShowFloatingButton(show: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateShowFloatingButton(show) }
    }

    fun updateSelectByThumbnail(select: Boolean) {
        viewModelScope.launch { viewSettingsRepo.updateSelectByThumbnail(select) }
    }

    fun updateScanFoldersList(folders: Set<String>) {
        viewModelScope.launch { viewSettingsRepo.updateScanFoldersList(folders) }
    }
    val playbackSettings = settingsRepo.playbackSettingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.devson.nosvedplayer.repository.PlaybackSettings(
                seekDurationSeconds = 10,
                seekBarStyle = "line",
                controlIconSize = "medium",
                autoPlayEnabled = false,
                showSeekButtons = true,
                fastplaySpeed = 2.0f,
                orientationMode = com.devson.nosvedplayer.repository.OrientationMode.SYSTEM_DEFAULT,
                fullScreenMode = com.devson.nosvedplayer.repository.FullScreenMode.AUTO_SWITCH,
                softButtonMode = com.devson.nosvedplayer.repository.SoftButtonMode.AUTO_HIDE,
                isCustomBrightnessEnabled = false,
                customBrightnessLevel = 0.5f,
                showElapsedTimeOverlay = false,
                showBatteryClockOverlay = false,
                showScreenRotationButton = true,
                pauseWhenObstructed = true,
                showRemainingTime = false
            )
        )

    fun updateOrientationMode(mode: com.devson.nosvedplayer.repository.OrientationMode) {
        viewModelScope.launch { settingsRepo.updateOrientationMode(mode) }
    }

    fun updateFullScreenMode(mode: com.devson.nosvedplayer.repository.FullScreenMode) {
        viewModelScope.launch { settingsRepo.updateFullScreenMode(mode) }
    }

    fun updateSoftButtonMode(mode: com.devson.nosvedplayer.repository.SoftButtonMode) {
        viewModelScope.launch { settingsRepo.updateSoftButtonMode(mode) }
    }

    fun updateIsCustomBrightnessEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateIsCustomBrightnessEnabled(enabled) }
    }

    fun updateCustomBrightnessLevel(level: Float) {
        viewModelScope.launch { settingsRepo.updateCustomBrightnessLevel(level) }
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
}

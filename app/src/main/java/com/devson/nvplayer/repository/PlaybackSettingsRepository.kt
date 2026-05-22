package com.devson.nvplayer.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE)

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "is_dark_theme" -> {
                _isDarkThemeFlow.value = if (prefs.contains("is_dark_theme")) prefs.getBoolean("is_dark_theme", false) else null
            }
            "is_amoled_theme" -> {
                _isAmoledThemeFlow.value = prefs.getBoolean("is_amoled_theme", false)
            }
            "default_audio_lang" -> {
                _defaultAudioLangFlow.value = prefs.getString("default_audio_lang", "") ?: ""
            }
            "default_subtitle_lang" -> {
                _defaultSubtitleLangFlow.value = prefs.getString("default_subtitle_lang", "") ?: ""
            }
            "is_developer_mode" -> {
                _isDeveloperModeFlow.value = prefs.getBoolean("is_developer_mode", false)
            }
            "use_modern_player_style" -> {
                _useModernPlayerStyleFlow.value = prefs.getBoolean("use_modern_player_style", true)
            }
            "has_seen_onboarding" -> {
                _hasSeenOnboardingFlow.value = if (prefs.contains("has_seen_onboarding")) prefs.getBoolean("has_seen_onboarding", false) else null
            }
            "dynamic_color" -> {
                _dynamicColorFlow.value = prefs.getBoolean("dynamic_color", false)
            }
            "selected_palette" -> {
                _selectedPaletteFlow.value = prefs.getString("selected_palette", "BLUE") ?: "BLUE"
            }
            "is_navbar_transparent" -> {
                _isNavBarTransparentFlow.value = prefs.getBoolean("is_navbar_transparent", true)
            }
            "seek_duration_seconds", "seek_bar_style", "control_icon_size", "auto_play_enabled",
            "show_seek_buttons", "fastplay_speed", "orientation_mode", "fullscreen_mode", "soft_button_mode",
            "show_elapsed_time_overlay", "show_battery_clock_overlay", "show_screen_rotation_button",
            "pause_when_obstructed", "show_remaining_time", "use_system_caption_style", "subtitle_font",
            "is_subtitle_bold", "force_ass_subtitle_override", "seek_gesture_enabled", "seek_speed_sec_cm",
            "brightness_gesture_enabled", "brightness_sensitivity", "volume_gesture_enabled", "volume_sensitivity",
            "two_finger_action", "three_finger_action", "long_press_enabled", "long_press_speed", "double_tap_action",
            "subtitle_text_size_scale", "subtitle_bg_style", "subtitle_delay_ms", "subtitle_vertical_offset", "subtitle_gestures_enabled",
            "custom_playback_speed", "tap_and_hold_speed", "double_tap_seek_duration", "screenshot_location" -> {
                _playbackSettingsFlow.value = loadPlaybackSettings()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    // Theme and general setting flows
    private val _isDarkThemeFlow = MutableStateFlow<Boolean?>(
        if (prefs.contains("is_dark_theme")) prefs.getBoolean("is_dark_theme", false) else null
    )
    val isDarkThemeFlow: StateFlow<Boolean?> = _isDarkThemeFlow.asStateFlow()

    private val _isAmoledThemeFlow = MutableStateFlow(prefs.getBoolean("is_amoled_theme", false))
    val isAmoledThemeFlow: StateFlow<Boolean> = _isAmoledThemeFlow.asStateFlow()

    private val _defaultAudioLangFlow = MutableStateFlow(prefs.getString("default_audio_lang", "") ?: "")
    val defaultAudioLangFlow: StateFlow<String> = _defaultAudioLangFlow.asStateFlow()

    private val _defaultSubtitleLangFlow = MutableStateFlow(prefs.getString("default_subtitle_lang", "") ?: "")
    val defaultSubtitleLangFlow: StateFlow<String> = _defaultSubtitleLangFlow.asStateFlow()

    private val _isDeveloperModeFlow = MutableStateFlow(prefs.getBoolean("is_developer_mode", false))
    val isDeveloperModeFlow: StateFlow<Boolean> = _isDeveloperModeFlow.asStateFlow()

    private val _useModernPlayerStyleFlow = MutableStateFlow(prefs.getBoolean("use_modern_player_style", true))
    val useModernPlayerStyleFlow: StateFlow<Boolean> = _useModernPlayerStyleFlow.asStateFlow()

    private val _hasSeenOnboardingFlow = MutableStateFlow<Boolean?>(
        if (prefs.contains("has_seen_onboarding")) prefs.getBoolean("has_seen_onboarding", false) else null
    )
    val hasSeenOnboardingFlow: StateFlow<Boolean?> = _hasSeenOnboardingFlow.asStateFlow()

    private val _dynamicColorFlow = MutableStateFlow(prefs.getBoolean("dynamic_color", false))
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

    private val _selectedPaletteFlow = MutableStateFlow(prefs.getString("selected_palette", "BLUE") ?: "BLUE")
    val selectedPaletteFlow: StateFlow<String> = _selectedPaletteFlow.asStateFlow()

    private val _isNavBarTransparentFlow = MutableStateFlow(prefs.getBoolean("is_navbar_transparent", true))
    val isNavBarTransparentFlow: StateFlow<Boolean> = _isNavBarTransparentFlow.asStateFlow()

    // Full playback settings flow
    private val _playbackSettingsFlow = MutableStateFlow(loadPlaybackSettings())
    val playbackSettingsFlow: StateFlow<PlaybackSettings> = _playbackSettingsFlow.asStateFlow()

    private fun loadPlaybackSettings(): PlaybackSettings {
        return PlaybackSettings(
            seekDurationSeconds = prefs.getInt("seek_duration_seconds", 10),
            seekBarStyle = prefs.getString("seek_bar_style", "line") ?: "line",
            controlIconSize = prefs.getString("control_icon_size", "medium") ?: "medium",
            autoPlayEnabled = prefs.getBoolean("auto_play_enabled", false),
            showNextPrevButtons = prefs.getBoolean("show_next_prev_buttons", true),
            showSeekButtons = prefs.getBoolean("show_seek_buttons", true),
            fastplaySpeed = prefs.getFloat("fastplay_speed", 2.0f),
            orientationMode = try {
                OrientationMode.valueOf(prefs.getString("orientation_mode", OrientationMode.SYSTEM_DEFAULT.name) ?: OrientationMode.SYSTEM_DEFAULT.name)
            } catch (e: Exception) {
                OrientationMode.SYSTEM_DEFAULT
            },
            fullScreenMode = try {
                FullScreenMode.valueOf(prefs.getString("fullscreen_mode", FullScreenMode.AUTO_SWITCH.name) ?: FullScreenMode.AUTO_SWITCH.name)
            } catch (e: Exception) {
                FullScreenMode.AUTO_SWITCH
            },
            softButtonMode = try {
                SoftButtonMode.valueOf(prefs.getString("soft_button_mode", SoftButtonMode.AUTO_HIDE.name) ?: SoftButtonMode.AUTO_HIDE.name)
            } catch (e: Exception) {
                SoftButtonMode.AUTO_HIDE
            },
            showElapsedTimeOverlay = prefs.getBoolean("show_elapsed_time_overlay", false),
            showBatteryClockOverlay = prefs.getBoolean("show_battery_clock_overlay", false),
            showScreenRotationButton = prefs.getBoolean("show_screen_rotation_button", true),
            pauseWhenObstructed = prefs.getBoolean("pause_when_obstructed", true),
            showRemainingTime = prefs.getBoolean("show_remaining_time", false),
            useSystemCaptionStyle = prefs.getBoolean("use_system_caption_style", false),
            subtitleFont = try {
                SubtitleFont.valueOf(prefs.getString("subtitle_font", SubtitleFont.DEFAULT.name) ?: SubtitleFont.DEFAULT.name)
            } catch (e: Exception) {
                SubtitleFont.DEFAULT
            },
            isSubtitleBold = prefs.getBoolean("is_subtitle_bold", false),
            forceAssSubtitleOverride = prefs.getBoolean("force_ass_subtitle_override", false),
            seekGestureEnabled = prefs.getBoolean("seek_gesture_enabled", true),
            seekSpeedSecPerCm = prefs.getInt("seek_speed_sec_cm", 10),
            brightnessGestureEnabled = prefs.getBoolean("brightness_gesture_enabled", true),
            brightnessSensitivity = prefs.getFloat("brightness_sensitivity", 0.5f),
            volumeGestureEnabled = prefs.getBoolean("volume_gesture_enabled", true),
            volumeSensitivity = prefs.getFloat("volume_sensitivity", 0.5f),
            twoFingerAction = try {
                MultiFingerAction.valueOf(prefs.getString("two_finger_action", MultiFingerAction.PLAY_PAUSE.name) ?: MultiFingerAction.PLAY_PAUSE.name)
            } catch (e: Exception) {
                MultiFingerAction.PLAY_PAUSE
            },
            threeFingerAction = try {
                MultiFingerAction.valueOf(prefs.getString("three_finger_action", MultiFingerAction.FAST_PLAY.name) ?: MultiFingerAction.FAST_PLAY.name)
            } catch (e: Exception) {
                MultiFingerAction.FAST_PLAY
            },
            longPressEnabled = prefs.getBoolean("long_press_enabled", true),
            longPressSpeed = prefs.getFloat("long_press_speed", 2.0f),
            doubleTapAction = try {
                DoubleTapAction.valueOf(prefs.getString("double_tap_action", DoubleTapAction.BOTH.name) ?: DoubleTapAction.BOTH.name)
            } catch (e: Exception) {
                DoubleTapAction.BOTH
            },
            subtitleTextSizeScale = prefs.getFloat("subtitle_text_size_scale", 1.0f),
            subtitleBgStyle = prefs.getInt("subtitle_bg_style", 1),
            subtitleDelayMs = prefs.getLong("subtitle_delay_ms", 0L),
            subtitleVerticalOffset = prefs.getFloat("subtitle_vertical_offset", 0f),
            subtitleGesturesEnabled = prefs.getBoolean("subtitle_gestures_enabled", true),
            customPlaybackSpeed = prefs.getFloat("custom_playback_speed", 1.0f),
            tapAndHoldSpeed = prefs.getFloat("tap_and_hold_speed", 2.0f),
            doubleTapSeekDuration = prefs.getLong("double_tap_seek_duration", 10000L),
            screenshotLocation = prefs.getString("screenshot_location", "Pictures/NVPlayer/Screenshot") ?: "Pictures/NVPlayer/Screenshot"
        )
    }

    private fun updatePlaybackSettings(updater: (PlaybackSettings) -> PlaybackSettings) {
        val current = _playbackSettingsFlow.value
        val updated = updater(current)
        _playbackSettingsFlow.value = updated

        prefs.edit().apply {
            putInt("seek_duration_seconds", updated.seekDurationSeconds)
            putString("seek_bar_style", updated.seekBarStyle)
            putString("control_icon_size", updated.controlIconSize)
            putBoolean("auto_play_enabled", updated.autoPlayEnabled)
            putBoolean("show_next_prev_buttons", updated.showNextPrevButtons)
            putBoolean("show_seek_buttons", updated.showSeekButtons)
            putFloat("fastplay_speed", updated.fastplaySpeed)
            putString("orientation_mode", updated.orientationMode.name)
            putString("fullscreen_mode", updated.fullScreenMode.name)
            putString("soft_button_mode", updated.softButtonMode.name)
            putBoolean("show_elapsed_time_overlay", updated.showElapsedTimeOverlay)
            putBoolean("show_battery_clock_overlay", updated.showBatteryClockOverlay)
            putBoolean("show_screen_rotation_button", updated.showScreenRotationButton)
            putBoolean("pause_when_obstructed", updated.pauseWhenObstructed)
            putBoolean("show_remaining_time", updated.showRemainingTime)
            putBoolean("use_system_caption_style", updated.useSystemCaptionStyle)
            putString("subtitle_font", updated.subtitleFont.name)
            putBoolean("is_subtitle_bold", updated.isSubtitleBold)
            putBoolean("force_ass_subtitle_override", updated.forceAssSubtitleOverride)
            putBoolean("seek_gesture_enabled", updated.seekGestureEnabled)
            putInt("seek_speed_sec_cm", updated.seekSpeedSecPerCm)
            putBoolean("brightness_gesture_enabled", updated.brightnessGestureEnabled)
            putFloat("brightness_sensitivity", updated.brightnessSensitivity)
            putBoolean("volume_gesture_enabled", updated.volumeGestureEnabled)
            putFloat("volume_sensitivity", updated.volumeSensitivity)
            putString("two_finger_action", updated.twoFingerAction.name)
            putString("three_finger_action", updated.threeFingerAction.name)
            putBoolean("long_press_enabled", updated.longPressEnabled)
            putFloat("long_press_speed", updated.longPressSpeed)
            putString("double_tap_action", updated.doubleTapAction.name)
            putFloat("subtitle_text_size_scale", updated.subtitleTextSizeScale)
            putInt("subtitle_bg_style", updated.subtitleBgStyle)
            putLong("subtitle_delay_ms", updated.subtitleDelayMs)
            putFloat("subtitle_vertical_offset", updated.subtitleVerticalOffset)
            putBoolean("subtitle_gestures_enabled", updated.subtitleGesturesEnabled)
            putFloat("custom_playback_speed", updated.customPlaybackSpeed)
            putFloat("tap_and_hold_speed", updated.tapAndHoldSpeed)
            putLong("double_tap_seek_duration", updated.doubleTapSeekDuration)
            putString("screenshot_location", updated.screenshotLocation)
            apply()
        }
    }

    // Setters for Theme / General Settings
    suspend fun setDarkTheme(isDark: Boolean) {
        _isDarkThemeFlow.value = isDark
        prefs.edit().putBoolean("is_dark_theme", isDark).apply()
    }

    suspend fun resetDarkTheme() {
        _isDarkThemeFlow.value = null
        prefs.edit().remove("is_dark_theme").apply()
    }

    suspend fun setAmoledTheme(enabled: Boolean) {
        _isAmoledThemeFlow.value = enabled
        prefs.edit().putBoolean("is_amoled_theme", enabled).apply()
    }

    suspend fun setDefaultAudioLanguage(langCode: String) {
        _defaultAudioLangFlow.value = langCode
        prefs.edit().putString("default_audio_lang", langCode).apply()
    }

    suspend fun setDefaultSubtitleLanguage(langCode: String) {
        _defaultSubtitleLangFlow.value = langCode
        prefs.edit().putString("default_subtitle_lang", langCode).apply()
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        _isDeveloperModeFlow.value = enabled
        prefs.edit().putBoolean("is_developer_mode", enabled).apply()
    }

    suspend fun setModernPlayerStyle(enabled: Boolean) {
        _useModernPlayerStyleFlow.value = enabled
        prefs.edit().putBoolean("use_modern_player_style", enabled).apply()
    }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        _hasSeenOnboardingFlow.value = seen
        prefs.edit().putBoolean("has_seen_onboarding", seen).apply()
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        _dynamicColorFlow.value = enabled
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
    }

    suspend fun setSelectedPalette(paletteKey: String) {
        _selectedPaletteFlow.value = paletteKey
        prefs.edit().putString("selected_palette", paletteKey).apply()
    }

    suspend fun setNavBarTransparent(transparent: Boolean) {
        _isNavBarTransparentFlow.value = transparent
        prefs.edit().putBoolean("is_navbar_transparent", transparent).apply()
    }

    // Setters for PlaybackSettings
    suspend fun updateSeekBarStyle(style: String) {
        updatePlaybackSettings { it.copy(seekBarStyle = style) }
    }

    suspend fun updateOrientationMode(mode: OrientationMode) {
        updatePlaybackSettings { it.copy(orientationMode = mode) }
    }

    suspend fun updateFullScreenMode(mode: FullScreenMode) {
        updatePlaybackSettings { it.copy(fullScreenMode = mode) }
    }

    suspend fun updateSoftButtonMode(mode: SoftButtonMode) {
        updatePlaybackSettings { it.copy(softButtonMode = mode) }
    }

    suspend fun updateShowElapsedTimeOverlay(show: Boolean) {
        updatePlaybackSettings { it.copy(showElapsedTimeOverlay = show) }
    }

    suspend fun updateShowBatteryClockOverlay(show: Boolean) {
        updatePlaybackSettings { it.copy(showBatteryClockOverlay = show) }
    }

    suspend fun updateShowScreenRotationButton(show: Boolean) {
        updatePlaybackSettings { it.copy(showScreenRotationButton = show) }
    }

    suspend fun updatePauseWhenObstructed(pause: Boolean) {
        updatePlaybackSettings { it.copy(pauseWhenObstructed = pause) }
    }

    suspend fun updateShowRemainingTime(show: Boolean) {
        updatePlaybackSettings { it.copy(showRemainingTime = show) }
    }

    suspend fun updateUseSystemCaptionStyle(useSystem: Boolean) {
        updatePlaybackSettings { it.copy(useSystemCaptionStyle = useSystem) }
    }

    suspend fun updateSubtitleFont(font: SubtitleFont) {
        updatePlaybackSettings { it.copy(subtitleFont = font) }
    }

    suspend fun updateIsSubtitleBold(isBold: Boolean) {
        updatePlaybackSettings { it.copy(isSubtitleBold = isBold) }
    }

    suspend fun updateForceAssSubtitleOverride(force: Boolean) {
        updatePlaybackSettings { it.copy(forceAssSubtitleOverride = force) }
    }

    suspend fun updateSeekGestureEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(seekGestureEnabled = enabled) }
    }

    suspend fun updateSeekSpeedSecPerCm(speed: Int) {
        updatePlaybackSettings { it.copy(seekSpeedSecPerCm = speed) }
    }

    suspend fun updateBrightnessGestureEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(brightnessGestureEnabled = enabled) }
    }

    suspend fun updateBrightnessSensitivity(sensitivity: Float) {
        updatePlaybackSettings { it.copy(brightnessSensitivity = sensitivity) }
    }

    suspend fun updateVolumeGestureEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(volumeGestureEnabled = enabled) }
    }

    suspend fun updateVolumeSensitivity(sensitivity: Float) {
        updatePlaybackSettings { it.copy(volumeSensitivity = sensitivity) }
    }

    suspend fun updateTwoFingerAction(action: MultiFingerAction) {
        updatePlaybackSettings { it.copy(twoFingerAction = action) }
    }

    suspend fun updateThreeFingerAction(action: MultiFingerAction) {
        updatePlaybackSettings { it.copy(threeFingerAction = action) }
    }

    suspend fun updateLongPressEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(longPressEnabled = enabled) }
    }

    suspend fun updateLongPressSpeed(speed: Float) {
        updatePlaybackSettings { it.copy(longPressSpeed = speed) }
    }

    suspend fun updateDoubleTapAction(action: DoubleTapAction) {
        updatePlaybackSettings { it.copy(doubleTapAction = action) }
    }

    suspend fun updateSubtitleTextSizeScale(scale: Float) {
        updatePlaybackSettings { it.copy(subtitleTextSizeScale = scale) }
    }

    suspend fun updateSubtitleBgStyle(style: Int) {
        updatePlaybackSettings { it.copy(subtitleBgStyle = style) }
    }

    suspend fun updateSubtitleDelay(delayMs: Long) {
        updatePlaybackSettings { it.copy(subtitleDelayMs = delayMs) }
    }

    suspend fun updateSubtitleVerticalOffset(offset: Float) {
        updatePlaybackSettings { it.copy(subtitleVerticalOffset = offset) }
    }

    suspend fun updateSubtitleGesturesEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(subtitleGesturesEnabled = enabled) }
    }

    suspend fun updateCustomPlaybackSpeed(speed: Float) {
        updatePlaybackSettings { it.copy(customPlaybackSpeed = speed) }
    }

    suspend fun updateTapAndHoldSpeed(speed: Float) {
        updatePlaybackSettings { it.copy(tapAndHoldSpeed = speed) }
    }

    suspend fun updateDoubleTapSeekDuration(durationMs: Long) {
        updatePlaybackSettings { it.copy(doubleTapSeekDuration = durationMs) }
    }

    suspend fun updateShowSeekButtons(show: Boolean) {
        updatePlaybackSettings { it.copy(showSeekButtons = show) }
    }

    suspend fun updateControlIconSize(size: String) {
        updatePlaybackSettings { it.copy(controlIconSize = size) }
    }

    suspend fun updateAutoPlayEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(autoPlayEnabled = enabled) }
    }

    suspend fun updateShowNextPrevButtons(show: Boolean) {
        updatePlaybackSettings { it.copy(showNextPrevButtons = show) }
    }

    suspend fun updateFastplaySpeed(speed: Float) {
        updatePlaybackSettings { it.copy(fastplaySpeed = speed) }
    }

    suspend fun updateSeekDurationSeconds(seconds: Int) {
        updatePlaybackSettings { it.copy(seekDurationSeconds = seconds) }
    }

    suspend fun updateScreenshotLocation(location: String) {
        updatePlaybackSettings { it.copy(screenshotLocation = location) }
    }
}

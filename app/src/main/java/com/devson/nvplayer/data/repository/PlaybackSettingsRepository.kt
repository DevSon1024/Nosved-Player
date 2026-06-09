package com.devson.nvplayer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.player.ytdlp.YtdlCodecPreference
import com.devson.nvplayer.player.ytdlp.YtdlContainerPreference
import com.devson.nvplayer.player.ytdlp.YtdlHdrPreference
import com.devson.nvplayer.player.ytdlp.YtdlPlaylistMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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
            "custom_playback_speed", "tap_and_hold_speed", "double_tap_seek_duration", "screenshot_location", "blacklisted_folders", "keep_awake_always", "decoder_mode",
            "enhance_mode", "enhance_saturation", "enhance_contrast", "enhance_brightness", "enhance_gamma", "enhance_hue",
            "top_left_controls", "top_right_controls", "bottom_left_controls", "bottom_right_controls",
            "portrait_top_left_controls", "portrait_top_right_controls", "portrait_bottom_controls",
            "aspect_mode", "background_play_enabled",
            "ytdl_format", "ytdl_quality", "ytdl_prefer_h264", "ytdl_codec_preference", "ytdl_max_fps", "ytdl_hdr_preference",
            "ytdl_container_preference", "ytdl_format_sort", "ytdl_merge_output_format", "ytdl_write_subs", "ytdl_write_auto_subs",
            "ytdl_subtitle_languages", "ytdl_custom_user_agent", "ytdl_referer", "ytdl_cookies_file", "ytdl_proxy", "ytdl_extractor_args",
            "ytdl_geo_bypass", "ytdl_playlist_mode", "ytdl_live_from_start", "ytdl_sponsorblock_mark", "ytdl_sponsorblock_remove", "ytdl_custom_raw_options",
            "is_data_saver_enabled", "is_bottom_layout_enabled", "show_control_gradients" -> {
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

    private val _defaultAudioLangFlow =
        MutableStateFlow(prefs.getString("default_audio_lang", "") ?: "")
    val defaultAudioLangFlow: StateFlow<String> = _defaultAudioLangFlow.asStateFlow()

    private val _defaultSubtitleLangFlow =
        MutableStateFlow(prefs.getString("default_subtitle_lang", "") ?: "")
    val defaultSubtitleLangFlow: StateFlow<String> = _defaultSubtitleLangFlow.asStateFlow()

    private val _isDeveloperModeFlow =
        MutableStateFlow(prefs.getBoolean("is_developer_mode", false))
    val isDeveloperModeFlow: StateFlow<Boolean> = _isDeveloperModeFlow.asStateFlow()

    private val _hasSeenOnboardingFlow = MutableStateFlow<Boolean?>(
        if (prefs.contains("has_seen_onboarding")) prefs.getBoolean(
            "has_seen_onboarding",
            false
        ) else null
    )
    val hasSeenOnboardingFlow: StateFlow<Boolean?> = _hasSeenOnboardingFlow.asStateFlow()

    private val _dynamicColorFlow = MutableStateFlow(prefs.getBoolean("dynamic_color", false))
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

    private val _selectedPaletteFlow =
        MutableStateFlow(prefs.getString("selected_palette", "BLUE") ?: "BLUE")
    val selectedPaletteFlow: StateFlow<String> = _selectedPaletteFlow.asStateFlow()

    private val _isNavBarTransparentFlow =
        MutableStateFlow(prefs.getBoolean("is_navbar_transparent", true))
    val isNavBarTransparentFlow: StateFlow<Boolean> = _isNavBarTransparentFlow.asStateFlow()

    // Full playback settings flow
    private val _playbackSettingsFlow = MutableStateFlow(loadPlaybackSettings())
    val playbackSettingsFlow: StateFlow<PlaybackSettings> = _playbackSettingsFlow.asStateFlow()

    private fun loadPlaybackSettings(): PlaybackSettings {
        return PlaybackSettings(
            seekDurationSeconds = prefs.getInt("seek_duration_seconds", 10),
            seekBarStyle = prefs.getString("seek_bar_style", "standard")?.let {
                if (it == "line") "standard" else it
            } ?: "standard",
            controlIconSize = prefs.getString("control_icon_size", "medium") ?: "medium",
            autoPlayEnabled = prefs.getBoolean("auto_play_enabled", false),
            showNextPrevButtons = prefs.getBoolean("show_next_prev_buttons", true),
            showSeekButtons = prefs.getBoolean("show_seek_buttons", true),
            fastplaySpeed = prefs.getFloat("fastplay_speed", 2.0f),
            orientationMode = try {
                OrientationMode.valueOf(
                    prefs.getString(
                        "orientation_mode",
                        OrientationMode.SYSTEM_DEFAULT.name
                    ) ?: OrientationMode.SYSTEM_DEFAULT.name
                )
            } catch (e: Exception) {
                OrientationMode.SYSTEM_DEFAULT
            },
            fullScreenMode = try {
                FullScreenMode.valueOf(
                    prefs.getString(
                        "fullscreen_mode",
                        FullScreenMode.AUTO_SWITCH.name
                    ) ?: FullScreenMode.AUTO_SWITCH.name
                )
            } catch (e: Exception) {
                FullScreenMode.AUTO_SWITCH
            },
            softButtonMode = try {
                SoftButtonMode.valueOf(
                    prefs.getString(
                        "soft_button_mode",
                        SoftButtonMode.AUTO_HIDE.name
                    ) ?: SoftButtonMode.AUTO_HIDE.name
                )
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
                SubtitleFont.valueOf(
                    prefs.getString("subtitle_font", SubtitleFont.DEFAULT.name)
                        ?: SubtitleFont.DEFAULT.name
                )
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
                // RELEASE FIX: Default was PLAY_PAUSE here but PINCH_ZOOM in the data class - aligned.
                MultiFingerAction.valueOf(
                    prefs.getString(
                        "two_finger_action",
                        MultiFingerAction.PINCH_ZOOM.name
                    ) ?: MultiFingerAction.PINCH_ZOOM.name
                )
            } catch (e: Exception) {
                MultiFingerAction.PINCH_ZOOM
            },
            threeFingerAction = try {
                MultiFingerAction.valueOf(
                    prefs.getString(
                        "three_finger_action",
                        MultiFingerAction.FAST_PLAY.name
                    ) ?: MultiFingerAction.FAST_PLAY.name
                )
            } catch (e: Exception) {
                MultiFingerAction.FAST_PLAY
            },
            longPressEnabled = prefs.getBoolean("long_press_enabled", true),
            longPressSpeed = prefs.getFloat("long_press_speed", 2.0f),
            doubleTapAction = try {
                DoubleTapAction.valueOf(
                    prefs.getString(
                        "double_tap_action",
                        DoubleTapAction.BOTH.name
                    ) ?: DoubleTapAction.BOTH.name
                )
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
            screenshotLocation = prefs.getString(
                "screenshot_location",
                "Pictures/NVPlayer/Screenshot"
            ) ?: "Pictures/NVPlayer/Screenshot",
            blacklistedFolders = prefs.getStringSet("blacklisted_folders", emptySet())
                ?: emptySet(),
            keepAwakeAlways = prefs.getBoolean("keep_awake_always", false),
            decoderMode = try {
                DecoderMode.valueOf(
                    prefs.getString("decoder_mode", DecoderMode.HW.name) ?: DecoderMode.HW.name
                )
            } catch (e: Exception) {
                DecoderMode.HW
            },
            enhanceMode = try {
                EnhanceMode.valueOf(
                    prefs.getString("enhance_mode", EnhanceMode.OFF.name) ?: EnhanceMode.OFF.name
                )
            } catch (e: Exception) {
                EnhanceMode.OFF
            },
            enhanceSaturation = prefs.getInt("enhance_saturation", 0),
            enhanceContrast = prefs.getInt("enhance_contrast", 0),
            enhanceBrightness = prefs.getInt("enhance_brightness", 0),
            enhanceGamma = prefs.getInt("enhance_gamma", 0),
            enhanceHue = prefs.getInt("enhance_hue", 0),
            topLeftControls = prefs.getString("top_left_controls", "BACK_ARROW,VIDEO_TITLE")
                ?: "BACK_ARROW,VIDEO_TITLE",
            topRightControls = prefs.getString(
                "top_right_controls",
                "DECODER,SUBTITLES,AUDIO_TRACK,MORE_OPTIONS"
            ) ?: "DECODER,SUBTITLES,AUDIO_TRACK,MORE_OPTIONS",
            bottomLeftControls = prefs.getString(
                "bottom_left_controls",
                "LOCK_CONTROLS,PICTURE_IN_PICTURE"
            ) ?: "LOCK_CONTROLS,PICTURE_IN_PICTURE",
            bottomRightControls = prefs.getString(
                "bottom_right_controls",
                "ASPECT_RATIO,SCREEN_ROTATION"
            ) ?: "ASPECT_RATIO,SCREEN_ROTATION",
            portraitTopLeftControls = prefs.getString(
                "portrait_top_left_controls",
                "BACK_ARROW,VIDEO_TITLE"
            ) ?: "BACK_ARROW,VIDEO_TITLE",
            portraitTopRightControls = prefs.getString(
                "portrait_top_right_controls",
                "SUBTITLES,AUDIO_TRACK,MORE_OPTIONS"
            ) ?: "SUBTITLES,AUDIO_TRACK,MORE_OPTIONS",
            portraitBottomControls = run {
                val bottomVal = prefs.getString(
                    "portrait_bottom_controls",
                    "DECODER,CHAPTERS,SMART_ENHANCE,ASPECT_RATIO,SCREEN_ROTATION"
                ) ?: "DECODER,CHAPTERS,SMART_ENHANCE,ASPECT_RATIO,SCREEN_ROTATION"
                val topRightVal = prefs.getString(
                    "portrait_top_right_controls",
                    "SUBTITLES,AUDIO_TRACK,MORE_OPTIONS"
                ) ?: "SUBTITLES,AUDIO_TRACK,MORE_OPTIONS"
                val topLeftVal =
                    prefs.getString("portrait_top_left_controls", "BACK_ARROW,VIDEO_TITLE")
                        ?: "BACK_ARROW,VIDEO_TITLE"
                val filterSet = (topRightVal.split(',') + topLeftVal.split(',')).toSet()
                bottomVal.split(',')
                    .filter { it.isNotBlank() && it !in filterSet }
                    .joinToString(",")
            },
            aspectMode = try {
                AspectMode.valueOf(
                    prefs.getString("aspect_mode", AspectMode.FIT.name) ?: AspectMode.FIT.name
                )
            } catch (e: Exception) {
                AspectMode.FIT
            },
            backgroundPlayEnabled = prefs.getBoolean("background_play_enabled", false),
            ytdlFormat = prefs.getString("ytdl_format", "") ?: "",
            ytdlQuality = prefs.getInt("ytdl_quality", -1),
            preferH264 = prefs.getBoolean("ytdl_prefer_h264", false),
            codecPreference = try {
                YtdlCodecPreference.valueOf(
                    prefs.getString(
                        "ytdl_codec_preference",
                        YtdlCodecPreference.AUTO.name
                    ) ?: YtdlCodecPreference.AUTO.name
                )
            } catch (e: Exception) {
                YtdlCodecPreference.AUTO
            },
            maxFps = prefs.getInt("ytdl_max_fps", 0),
            hdrPreference = try {
                YtdlHdrPreference.valueOf(
                    prefs.getString(
                        "ytdl_hdr_preference",
                        YtdlHdrPreference.ANY.name
                    ) ?: YtdlHdrPreference.ANY.name
                )
            } catch (e: Exception) {
                YtdlHdrPreference.ANY
            },
            containerPreference = try {
                YtdlContainerPreference.valueOf(
                    prefs.getString(
                        "ytdl_container_preference",
                        YtdlContainerPreference.ANY.name
                    ) ?: YtdlContainerPreference.ANY.name
                )
            } catch (e: Exception) {
                YtdlContainerPreference.ANY
            },
            formatSort = prefs.getString("ytdl_format_sort", "") ?: "",
            mergeOutputFormat = prefs.getString("ytdl_merge_output_format", "") ?: "",
            writeSubs = prefs.getBoolean("ytdl_write_subs", true),
            writeAutoSubs = prefs.getBoolean("ytdl_write_auto_subs", false),
            subtitleLanguages = prefs.getString("ytdl_subtitle_languages", "") ?: "",
            customUserAgent = prefs.getString("ytdl_custom_user_agent", "") ?: "",
            referer = prefs.getString("ytdl_referer", "") ?: "",
            cookiesFile = prefs.getString("ytdl_cookies_file", "") ?: "",
            proxy = prefs.getString("ytdl_proxy", "") ?: "",
            extractorArgs = prefs.getString("ytdl_extractor_args", "") ?: "",
            geoBypass = prefs.getBoolean("ytdl_geo_bypass", false),
            playlistMode = try {
                YtdlPlaylistMode.valueOf(
                    prefs.getString(
                        "ytdl_playlist_mode",
                        YtdlPlaylistMode.DEFAULT.name
                    ) ?: YtdlPlaylistMode.DEFAULT.name
                )
            } catch (e: Exception) {
                YtdlPlaylistMode.DEFAULT
            },
            liveFromStart = prefs.getBoolean("ytdl_live_from_start", false),
            sponsorBlockMark = prefs.getString("ytdl_sponsorblock_mark", "") ?: "",
            sponsorBlockRemove = prefs.getString("ytdl_sponsorblock_remove", "") ?: "",
            customRawOptions = prefs.getString("ytdl_custom_raw_options", "") ?: "",
            isDataSaverEnabled = prefs.getBoolean("is_data_saver_enabled", false),
            isBottomLayoutEnabled = prefs.getBoolean("is_bottom_layout_enabled", false),
            showControlGradients = prefs.getBoolean("show_control_gradients", true)
        )
    }

    /**
     * Applies [updater] to the current settings, emits to the StateFlow on the main thread,
     * and persists every field to SharedPreferences.
     *
     * RELEASE FIX: StateFlow mutations MUST happen on the main thread to avoid a race with the
     * SharedPreferences.OnSharedPreferenceChangeListener, which always fires on the main thread.
     * R8-optimised release builds tighten coroutine dispatch, making the race window much larger.
     */
    private suspend fun updatePlaybackSettings(updater: (PlaybackSettings) -> PlaybackSettings) =
        withContext(Dispatchers.Main.immediate) {
            val updated = updater(_playbackSettingsFlow.value)
            // Update in-memory state synchronously on the main thread.
            _playbackSettingsFlow.value = updated

            // Persist to disk - SharedPreferences.apply() is always async and thread-safe.
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
                putStringSet("blacklisted_folders", updated.blacklistedFolders)
                putBoolean("keep_awake_always", updated.keepAwakeAlways)
                putString("decoder_mode", updated.decoderMode.name)
                putString("enhance_mode", updated.enhanceMode.name)
                putInt("enhance_saturation", updated.enhanceSaturation)
                putInt("enhance_contrast", updated.enhanceContrast)
                putInt("enhance_brightness", updated.enhanceBrightness)
                putInt("enhance_gamma", updated.enhanceGamma)
                putInt("enhance_hue", updated.enhanceHue)
                putString("top_left_controls", updated.topLeftControls)
                putString("top_right_controls", updated.topRightControls)
                putString("bottom_left_controls", updated.bottomLeftControls)
                putString("bottom_right_controls", updated.bottomRightControls)
                putString("portrait_top_left_controls", updated.portraitTopLeftControls)
                putString("portrait_top_right_controls", updated.portraitTopRightControls)
                putString("portrait_bottom_controls", updated.portraitBottomControls)
                putString("aspect_mode", updated.aspectMode.name)
                putBoolean("background_play_enabled", updated.backgroundPlayEnabled)
                putString("ytdl_format", updated.ytdlFormat)
                putInt("ytdl_quality", updated.ytdlQuality)
                putBoolean("ytdl_prefer_h264", updated.preferH264)
                putString("ytdl_codec_preference", updated.codecPreference.name)
                putInt("ytdl_max_fps", updated.maxFps)
                putString("ytdl_hdr_preference", updated.hdrPreference.name)
                putString("ytdl_container_preference", updated.containerPreference.name)
                putString("ytdl_format_sort", updated.formatSort)
                putString("ytdl_merge_output_format", updated.mergeOutputFormat)
                putBoolean("ytdl_write_subs", updated.writeSubs)
                putBoolean("ytdl_write_auto_subs", updated.writeAutoSubs)
                putString("ytdl_subtitle_languages", updated.subtitleLanguages)
                putString("ytdl_custom_user_agent", updated.customUserAgent)
                putString("ytdl_referer", updated.referer)
                putString("ytdl_cookies_file", updated.cookiesFile)
                putString("ytdl_proxy", updated.proxy)
                putString("ytdl_extractor_args", updated.extractorArgs)
                putBoolean("ytdl_geo_bypass", updated.geoBypass)
                putString("ytdl_playlist_mode", updated.playlistMode.name)
                putBoolean("ytdl_live_from_start", updated.liveFromStart)
                putString("ytdl_sponsorblock_mark", updated.sponsorBlockMark)
                putString("ytdl_sponsorblock_remove", updated.sponsorBlockRemove)
                putString("ytdl_custom_raw_options", updated.customRawOptions)
                putBoolean("is_data_saver_enabled", updated.isDataSaverEnabled)
                putBoolean("is_bottom_layout_enabled", updated.isBottomLayoutEnabled)
                putBoolean("show_control_gradients", updated.showControlGradients)
                apply()
            }
        }

    /**
     * RELEASE FIX: Unregisters the SharedPreferences listener to break the strong reference
     * that SharedPreferences holds to this repository via the listener lambda.
     * Must be called from the owning ViewModel's onCleared().
     */
    fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    suspend fun updateBlacklistedFolders(folders: Set<String>) {
        updatePlaybackSettings { it.copy(blacklistedFolders = folders) }
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

    suspend fun updateKeepAwakeAlways(enabled: Boolean) {
        updatePlaybackSettings { it.copy(keepAwakeAlways = enabled) }
    }

    suspend fun updateDecoderMode(mode: DecoderMode) {
        updatePlaybackSettings { it.copy(decoderMode = mode) }
    }

    suspend fun updateEnhanceMode(mode: EnhanceMode) {
        updatePlaybackSettings { it.copy(enhanceMode = mode) }
    }

    suspend fun updateEnhanceSaturation(value: Int) {
        updatePlaybackSettings { it.copy(enhanceSaturation = value) }
    }

    suspend fun updateEnhanceContrast(value: Int) {
        updatePlaybackSettings { it.copy(enhanceContrast = value) }
    }

    suspend fun updateEnhanceBrightness(value: Int) {
        updatePlaybackSettings { it.copy(enhanceBrightness = value) }
    }

    suspend fun updateEnhanceGamma(value: Int) {
        updatePlaybackSettings { it.copy(enhanceGamma = value) }
    }

    suspend fun updateEnhanceHue(value: Int) {
        updatePlaybackSettings { it.copy(enhanceHue = value) }
    }

    suspend fun updateTopLeftControls(controls: String) {
        updatePlaybackSettings { it.copy(topLeftControls = controls) }
    }

    suspend fun updateTopRightControls(controls: String) {
        updatePlaybackSettings { it.copy(topRightControls = controls) }
    }

    suspend fun updateBottomLeftControls(controls: String) {
        updatePlaybackSettings { it.copy(bottomLeftControls = controls) }
    }

    suspend fun updateBottomRightControls(controls: String) {
        updatePlaybackSettings { it.copy(bottomRightControls = controls) }
    }

    suspend fun updatePortraitBottomControls(controls: String) {
        updatePlaybackSettings { it.copy(portraitBottomControls = controls) }
    }

    suspend fun updatePortraitTopLeftControls(controls: String) {
        updatePlaybackSettings { it.copy(portraitTopLeftControls = controls) }
    }

    suspend fun updatePortraitTopRightControls(controls: String) {
        updatePlaybackSettings { it.copy(portraitTopRightControls = controls) }
    }

    suspend fun updateAspectMode(mode: AspectMode) {
        updatePlaybackSettings { it.copy(aspectMode = mode) }
    }

    suspend fun updateBackgroundPlayEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(backgroundPlayEnabled = enabled) }
    }

    suspend fun updateYtdlFormat(value: String) {
        updatePlaybackSettings { it.copy(ytdlFormat = value) }
    }
    suspend fun updateYtdlQuality(value: Int) {
        updatePlaybackSettings { it.copy(ytdlQuality = value) }
    }
    suspend fun updatePreferH264(value: Boolean) {
        updatePlaybackSettings { it.copy(preferH264 = value) }
    }
    suspend fun updateYtdlCodecPreference(value: YtdlCodecPreference) {
        updatePlaybackSettings { it.copy(codecPreference = value) }
    }
    suspend fun updateYtdlMaxFps(value: Int) {
        updatePlaybackSettings { it.copy(maxFps = value) }
    }
    suspend fun updateYtdlHdrPreference(value: YtdlHdrPreference) {
        updatePlaybackSettings { it.copy(hdrPreference = value) }
    }
    suspend fun updateYtdlContainerPreference(value: YtdlContainerPreference) {
        updatePlaybackSettings { it.copy(containerPreference = value) }
    }
    suspend fun updateYtdlFormatSort(value: String) {
        updatePlaybackSettings { it.copy(formatSort = value) }
    }
    suspend fun updateYtdlMergeOutputFormat(value: String) {
        updatePlaybackSettings { it.copy(mergeOutputFormat = value) }
    }
    suspend fun updateYtdlWriteSubs(value: Boolean) {
        updatePlaybackSettings { it.copy(writeSubs = value) }
    }
    suspend fun updateYtdlWriteAutoSubs(value: Boolean) {
        updatePlaybackSettings { it.copy(writeAutoSubs = value) }
    }
    suspend fun updateYtdlSubtitleLanguages(value: String) {
        updatePlaybackSettings { it.copy(subtitleLanguages = value) }
    }
    suspend fun updateYtdlCustomUserAgent(value: String) {
        updatePlaybackSettings { it.copy(customUserAgent = value) }
    }
    suspend fun updateYtdlReferer(value: String) {
        updatePlaybackSettings { it.copy(referer = value) }
    }
    suspend fun updateYtdlCookiesFile(value: String) {
        updatePlaybackSettings { it.copy(cookiesFile = value) }
    }
    suspend fun updateYtdlProxy(value: String) {
        updatePlaybackSettings { it.copy(proxy = value) }
    }
    suspend fun updateYtdlExtractorArgs(value: String) {
        updatePlaybackSettings { it.copy(extractorArgs = value) }
    }
    suspend fun updateYtdlGeoBypass(value: Boolean) {
        updatePlaybackSettings { it.copy(geoBypass = value) }
    }
    suspend fun updateYtdlPlaylistMode(value: YtdlPlaylistMode) {
        updatePlaybackSettings { it.copy(playlistMode = value) }
    }
    suspend fun updateYtdlLiveFromStart(value: Boolean) {
        updatePlaybackSettings { it.copy(liveFromStart = value) }
    }
    suspend fun updateYtdlSponsorBlockMark(value: String) {
        updatePlaybackSettings { it.copy(sponsorBlockMark = value) }
    }
    suspend fun updateYtdlSponsorBlockRemove(value: String) {
        updatePlaybackSettings { it.copy(sponsorBlockRemove = value) }
    }
    suspend fun updateYtdlCustomRawOptions(value: String) {
        updatePlaybackSettings { it.copy(customRawOptions = value) }
    }

    suspend fun updateDataSaverEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(isDataSaverEnabled = enabled) }
    }

    suspend fun updateIsBottomLayoutEnabled(enabled: Boolean) {
        updatePlaybackSettings { it.copy(isBottomLayoutEnabled = enabled) }
    }

    suspend fun updateShowControlGradients(show: Boolean) {
        updatePlaybackSettings { it.copy(showControlGradients = show) }
    }
}
package com.devson.nvplayer.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_settings")

enum class OrientationMode { VIDEO_ORIENTATION, LANDSCAPE, REVERSE_LANDSCAPE, AUTO_ROTATION, SYSTEM_DEFAULT }
enum class FullScreenMode { ON, OFF, AUTO_SWITCH }
enum class SoftButtonMode { SHOW, HIDE, AUTO_HIDE }
enum class DecoderMode(val displayName: String) { HW("HW"), HW_PLUS("HW+"), SW("SW") }
enum class SubtitleFont { DEFAULT, MONOSPACE, SANS_SERIF, SERIF }
enum class DoubleTapAction(val displayName: String) {
    PLAY_PAUSE("Play / Pause"),
    FAST_FORWARD_REWIND("Fast Forward / Rewind"),
    BOTH("Both"),
    NONE("None")
}
enum class MultiFingerAction(val displayName: String) {
    PLAY_PAUSE("Play / Pause"),
    FAST_PLAY("Lock Video at Fast Play"),
    NONE("None")
}
data class PlaybackSettings(
    val seekDurationSeconds: Int,
    val seekBarStyle: String,
    val controlIconSize: String,
    val autoPlayEnabled: Boolean,
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
    val decoderMode: DecoderMode = DecoderMode.HW_PLUS,
    val isAmoledTheme: Boolean = false,
    val defaultAudioLanguage: String = "",
    val defaultSubtitleLanguage: String = "",
    val useSystemCaptionStyle: Boolean = false,
    val subtitleFont: SubtitleFont = SubtitleFont.DEFAULT,
    val isSubtitleBold: Boolean = false,
    val forceAssSubtitleOverride: Boolean = false,

    // Video Filters
    val shouldApplyVideoFilters: Boolean = false,
    val isVideoBrightnessFilterEnabled: Boolean = false,
    val videoBrightness: Float = 0f,
    val isVideoContrastFilterEnabled: Boolean = false,
    val videoContrast: Float = 0f,
    val isVideoSaturationFilterEnabled: Boolean = false,
    val videoSaturation: Float = 0f,
    val isVideoHueFilterEnabled: Boolean = false,
    val videoHue: Float = 0f,
    val isVideoGammaFilterEnabled: Boolean = false,
    val videoGamma: Float = 1f,
    val isVideoSharpeningFilterEnabled: Boolean = false,
    val videoSharpening: Float = 0f,

    // Gesture Settings
    val seekGestureEnabled: Boolean = true,
    val seekSensitivity: Float = 0.5f,
    val brightnessGestureEnabled: Boolean = true,
    val brightnessSensitivity: Float = 0.5f,
    val volumeGestureEnabled: Boolean = true,
    val volumeSensitivity: Float = 0.5f,
    val twoFingerAction: MultiFingerAction = MultiFingerAction.PLAY_PAUSE,
    val threeFingerAction: MultiFingerAction = MultiFingerAction.FAST_PLAY,
    val longPressEnabled: Boolean = true,
    val longPressSpeed: Float = 2.0f,
    val doubleTapAction: DoubleTapAction = DoubleTapAction.BOTH
)

class PlaybackSettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val SEEK_DURATION = intPreferencesKey("seek_duration_seconds")
        val SEEK_BAR_STYLE = stringPreferencesKey("seek_bar_style")
        val CONTROL_ICON_SIZE = stringPreferencesKey("control_icon_size")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val SHOW_SEEK_BUTTONS = booleanPreferencesKey("show_seek_buttons")
        val FASTPLAY_SPEED = floatPreferencesKey("fastplay_speed")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DARK_THEME_SET = booleanPreferencesKey("dark_theme_set")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val Modern_PLAYER_STYLE = booleanPreferencesKey("Modern_player_style")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val SELECTED_PALETTE = stringPreferencesKey("selected_palette")
        val ORIENTATION_MODE = stringPreferencesKey("orientation_mode")
        val FULL_SCREEN_MODE = stringPreferencesKey("full_screen_mode")
        val SOFT_BUTTON_MODE = stringPreferencesKey("soft_button_mode")
        val IS_CUSTOM_BRIGHTNESS_ENABLED = booleanPreferencesKey("is_custom_brightness_enabled")
        val CUSTOM_BRIGHTNESS_LEVEL = floatPreferencesKey("custom_brightness_level")
        val SHOW_ELAPSED_TIME_OVERLAY = booleanPreferencesKey("show_elapsed_time_overlay")
        val SHOW_BATTERY_CLOCK_OVERLAY = booleanPreferencesKey("show_battery_clock_overlay")
        val SHOW_SCREEN_ROTATION_BUTTON = booleanPreferencesKey("show_screen_rotation_button")
        val PAUSE_WHEN_OBSTRUCTED = booleanPreferencesKey("pause_when_obstructed")
        val SHOW_REMAINING_TIME = booleanPreferencesKey("show_remaining_time")
        val NAV_BAR_TRANSPARENT = booleanPreferencesKey("nav_bar_transparent")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val AMOLED_THEME = booleanPreferencesKey("amoled_theme")
        val DEFAULT_AUDIO_LANG = stringPreferencesKey("default_audio_lang")
        val DEFAULT_SUBTITLE_LANG = stringPreferencesKey("default_subtitle_lang")
        val USE_SYSTEM_CAPTION_STYLE = booleanPreferencesKey("use_system_caption_style")
        val SUBTITLE_FONT = stringPreferencesKey("subtitle_font")
        val IS_SUBTITLE_BOLD = booleanPreferencesKey("is_subtitle_bold")
        val FORCE_ASS_SUBTITLE_OVERRIDE = booleanPreferencesKey("force_ass_subtitle_override")

        // Video Filters Keys
        val SHOULD_APPLY_VIDEO_FILTERS = booleanPreferencesKey("should_apply_video_filters")
        val IS_VIDEO_BRIGHTNESS_FILTER_ENABLED = booleanPreferencesKey("is_video_brightness_filter_enabled")
        val VIDEO_BRIGHTNESS = floatPreferencesKey("video_brightness")
        val IS_VIDEO_CONTRAST_FILTER_ENABLED = booleanPreferencesKey("is_video_contrast_filter_enabled")
        val VIDEO_CONTRAST = floatPreferencesKey("video_contrast")
        val IS_VIDEO_SATURATION_FILTER_ENABLED = booleanPreferencesKey("is_video_saturation_filter_enabled")
        val VIDEO_SATURATION = floatPreferencesKey("video_saturation")
        val IS_VIDEO_HUE_FILTER_ENABLED = booleanPreferencesKey("is_video_hue_filter_enabled")
        val VIDEO_HUE = floatPreferencesKey("video_hue")
        val IS_VIDEO_GAMMA_FILTER_ENABLED = booleanPreferencesKey("is_video_gamma_filter_enabled")
        val VIDEO_GAMMA = floatPreferencesKey("video_gamma")
        val IS_VIDEO_SHARPENING_FILTER_ENABLED = booleanPreferencesKey("is_video_sharpening_filter_enabled")
        val VIDEO_SHARPENING = floatPreferencesKey("video_sharpening")

        // Gesture Keys
        val SEEK_GESTURE_ENABLED = booleanPreferencesKey("seek_gesture_enabled")
        val SEEK_SENSITIVITY = floatPreferencesKey("seek_sensitivity")
        val BRIGHTNESS_GESTURE_ENABLED = booleanPreferencesKey("brightness_gesture_enabled")
        val BRIGHTNESS_SENSITIVITY = floatPreferencesKey("brightness_sensitivity")
        val VOLUME_GESTURE_ENABLED = booleanPreferencesKey("volume_gesture_enabled")
        val VOLUME_SENSITIVITY = floatPreferencesKey("volume_sensitivity")
        val TWO_FINGER_ACTION = stringPreferencesKey("two_finger_action")
        val THREE_FINGER_ACTION = stringPreferencesKey("three_finger_action")
        val LONG_PRESS_ENABLED = booleanPreferencesKey("long_press_enabled")
        val LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
        val DOUBLE_TAP_ACTION = stringPreferencesKey("double_tap_action")
    }

    val playbackSettingsFlow: Flow<PlaybackSettings> = context.dataStore.data
        .map { preferences ->
            PlaybackSettings(
                seekDurationSeconds = preferences[PreferencesKeys.SEEK_DURATION] ?: 10,
                seekBarStyle = preferences[PreferencesKeys.SEEK_BAR_STYLE] ?: "DEFAULT",
                controlIconSize = preferences[PreferencesKeys.CONTROL_ICON_SIZE] ?: "MEDIUM",
                autoPlayEnabled = preferences[PreferencesKeys.AUTO_PLAY] ?: false,
                showSeekButtons = preferences[PreferencesKeys.SHOW_SEEK_BUTTONS] ?: true,
                fastplaySpeed = preferences[PreferencesKeys.FASTPLAY_SPEED] ?: 2.0f,
                orientationMode = try { OrientationMode.valueOf(preferences[PreferencesKeys.ORIENTATION_MODE] ?: OrientationMode.SYSTEM_DEFAULT.name) } catch (e: Exception) { OrientationMode.SYSTEM_DEFAULT },
                fullScreenMode = try { FullScreenMode.valueOf(preferences[PreferencesKeys.FULL_SCREEN_MODE] ?: FullScreenMode.AUTO_SWITCH.name) } catch (e: Exception) { FullScreenMode.AUTO_SWITCH },
                softButtonMode = try { SoftButtonMode.valueOf(preferences[PreferencesKeys.SOFT_BUTTON_MODE] ?: SoftButtonMode.AUTO_HIDE.name) } catch (e: Exception) { SoftButtonMode.AUTO_HIDE },
                showElapsedTimeOverlay = preferences[PreferencesKeys.SHOW_ELAPSED_TIME_OVERLAY] ?: false,
                showBatteryClockOverlay = preferences[PreferencesKeys.SHOW_BATTERY_CLOCK_OVERLAY] ?: false,
                showScreenRotationButton = preferences[PreferencesKeys.SHOW_SCREEN_ROTATION_BUTTON] ?: true,
                pauseWhenObstructed = preferences[PreferencesKeys.PAUSE_WHEN_OBSTRUCTED] ?: true,
                showRemainingTime = preferences[PreferencesKeys.SHOW_REMAINING_TIME] ?: false,
                decoderMode = try { DecoderMode.valueOf(preferences[PreferencesKeys.DECODER_MODE] ?: DecoderMode.HW_PLUS.name) } catch (e: Exception) { DecoderMode.HW_PLUS },
                isAmoledTheme = preferences[PreferencesKeys.AMOLED_THEME] ?: false,
                defaultAudioLanguage = preferences[PreferencesKeys.DEFAULT_AUDIO_LANG] ?: "",
                defaultSubtitleLanguage = preferences[PreferencesKeys.DEFAULT_SUBTITLE_LANG] ?: "",
                useSystemCaptionStyle = preferences[PreferencesKeys.USE_SYSTEM_CAPTION_STYLE] ?: false,
                subtitleFont = try { SubtitleFont.valueOf(preferences[PreferencesKeys.SUBTITLE_FONT] ?: SubtitleFont.DEFAULT.name) } catch (e: Exception) { SubtitleFont.DEFAULT },
                isSubtitleBold = preferences[PreferencesKeys.IS_SUBTITLE_BOLD] ?: false,
                forceAssSubtitleOverride = preferences[PreferencesKeys.FORCE_ASS_SUBTITLE_OVERRIDE] ?: false,

                shouldApplyVideoFilters = preferences[PreferencesKeys.SHOULD_APPLY_VIDEO_FILTERS] ?: false,
                isVideoBrightnessFilterEnabled = preferences[PreferencesKeys.IS_VIDEO_BRIGHTNESS_FILTER_ENABLED] ?: false,
                videoBrightness = preferences[PreferencesKeys.VIDEO_BRIGHTNESS] ?: 0f,
                isVideoContrastFilterEnabled = preferences[PreferencesKeys.IS_VIDEO_CONTRAST_FILTER_ENABLED] ?: false,
                videoContrast = preferences[PreferencesKeys.VIDEO_CONTRAST] ?: 0f,
                isVideoSaturationFilterEnabled = preferences[PreferencesKeys.IS_VIDEO_SATURATION_FILTER_ENABLED] ?: false,
                videoSaturation = preferences[PreferencesKeys.VIDEO_SATURATION] ?: 0f,
                isVideoHueFilterEnabled = preferences[PreferencesKeys.IS_VIDEO_HUE_FILTER_ENABLED] ?: false,
                videoHue = preferences[PreferencesKeys.VIDEO_HUE] ?: 0f,
                isVideoGammaFilterEnabled = preferences[PreferencesKeys.IS_VIDEO_GAMMA_FILTER_ENABLED] ?: false,
                videoGamma = preferences[PreferencesKeys.VIDEO_GAMMA] ?: 1f,
                isVideoSharpeningFilterEnabled = preferences[PreferencesKeys.IS_VIDEO_SHARPENING_FILTER_ENABLED] ?: false,
                videoSharpening = preferences[PreferencesKeys.VIDEO_SHARPENING] ?: 0f,

                seekGestureEnabled = preferences[PreferencesKeys.SEEK_GESTURE_ENABLED] ?: true,
                seekSensitivity = preferences[PreferencesKeys.SEEK_SENSITIVITY] ?: 0.5f,
                brightnessGestureEnabled = preferences[PreferencesKeys.BRIGHTNESS_GESTURE_ENABLED] ?: true,
                brightnessSensitivity = preferences[PreferencesKeys.BRIGHTNESS_SENSITIVITY] ?: 0.5f,
                volumeGestureEnabled = preferences[PreferencesKeys.VOLUME_GESTURE_ENABLED] ?: true,
                volumeSensitivity = preferences[PreferencesKeys.VOLUME_SENSITIVITY] ?: 0.5f,
                twoFingerAction = try { MultiFingerAction.valueOf(preferences[PreferencesKeys.TWO_FINGER_ACTION] ?: MultiFingerAction.PLAY_PAUSE.name) } catch (e: Exception) { MultiFingerAction.PLAY_PAUSE },
                threeFingerAction = try { MultiFingerAction.valueOf(preferences[PreferencesKeys.THREE_FINGER_ACTION] ?: MultiFingerAction.FAST_PLAY.name) } catch (e: Exception) { MultiFingerAction.FAST_PLAY },
                longPressEnabled = preferences[PreferencesKeys.LONG_PRESS_ENABLED] ?: true,
                longPressSpeed = preferences[PreferencesKeys.LONG_PRESS_SPEED] ?: 2.0f,
                doubleTapAction = try { DoubleTapAction.valueOf(preferences[PreferencesKeys.DOUBLE_TAP_ACTION] ?: DoubleTapAction.BOTH.name) } catch (e: Exception) { DoubleTapAction.BOTH }
            )
        }

    val isDarkThemeFlow: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        if (prefs[PreferencesKeys.DARK_THEME_SET] == true) {
            prefs[PreferencesKeys.DARK_THEME]
        } else null
    }

    val isAmoledThemeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AMOLED_THEME] ?: false
    }

    val defaultAudioLangFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DEFAULT_AUDIO_LANG] ?: ""
    }

    val defaultSubtitleLangFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DEFAULT_SUBTITLE_LANG] ?: ""
    }

    val isDeveloperModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DEVELOPER_MODE] ?: false
    }

    val useModernPlayerStyleFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.Modern_PLAYER_STYLE] ?: true
    }

    val hasSeenOnboardingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HAS_SEEN_ONBOARDING] ?: false
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DYNAMIC_COLOR] ?: false
    }

    val selectedPaletteFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SELECTED_PALETTE] ?: "BLUE"
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_THEME] = isDark
            prefs[PreferencesKeys.DARK_THEME_SET] = true
        }
    }

    suspend fun resetDarkTheme() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_THEME_SET] = false
            prefs.remove(PreferencesKeys.DARK_THEME)
        }
    }

    suspend fun setAmoledTheme(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AMOLED_THEME] = enabled }
    }

    suspend fun setDefaultAudioLanguage(langCode: String) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_AUDIO_LANG] = langCode }
    }

    suspend fun setDefaultSubtitleLanguage(langCode: String) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_SUBTITLE_LANG] = langCode }
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DEVELOPER_MODE] = enabled
        }
    }

    suspend fun setModernPlayerStyle(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.Modern_PLAYER_STYLE] = enabled
        }
    }

    suspend fun setHasSeenOnboarding(seen: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HAS_SEEN_ONBOARDING] = seen
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setSelectedPalette(paletteName: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SELECTED_PALETTE] = paletteName
        }
    }

    suspend fun updateSeekDuration(durationSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEEK_DURATION] = durationSeconds
        }
    }

    suspend fun updateSeekBarStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEEK_BAR_STYLE] = style
        }
    }

    suspend fun updateControlIconSize(size: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTROL_ICON_SIZE] = size
        }
    }

    suspend fun updateAutoPlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_PLAY] = enabled
        }
    }

    suspend fun updateShowSeekButtons(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_SEEK_BUTTONS] = show
        }
    }

    suspend fun updateFastplaySpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FASTPLAY_SPEED] = speed
        }
    }

    suspend fun updateOrientationMode(mode: OrientationMode) {
        context.dataStore.edit { it[PreferencesKeys.ORIENTATION_MODE] = mode.name }
    }

    suspend fun updateFullScreenMode(mode: FullScreenMode) {
        context.dataStore.edit { it[PreferencesKeys.FULL_SCREEN_MODE] = mode.name }
    }

    suspend fun updateSoftButtonMode(mode: SoftButtonMode) {
        context.dataStore.edit { it[PreferencesKeys.SOFT_BUTTON_MODE] = mode.name }
    }

    suspend fun updateShowElapsedTimeOverlay(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_ELAPSED_TIME_OVERLAY] = show }
    }

    suspend fun updateShowBatteryClockOverlay(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_BATTERY_CLOCK_OVERLAY] = show }
    }

    suspend fun updateShowScreenRotationButton(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_SCREEN_ROTATION_BUTTON] = show }
    }

    suspend fun updatePauseWhenObstructed(pause: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PAUSE_WHEN_OBSTRUCTED] = pause }
    }

    suspend fun updateShowRemainingTime(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_REMAINING_TIME] = show }
    }

    suspend fun updateDecoderMode(mode: DecoderMode) {
        context.dataStore.edit { it[PreferencesKeys.DECODER_MODE] = mode.name }
    }

    val isNavBarTransparentFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.NAV_BAR_TRANSPARENT] ?: true
    }

    suspend fun setNavBarTransparent(transparent: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NAV_BAR_TRANSPARENT] = transparent }
    }

    suspend fun updateUseSystemCaptionStyle(useSystem: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_SYSTEM_CAPTION_STYLE] = useSystem }
    }

    suspend fun updateSubtitleFont(font: SubtitleFont) {
        context.dataStore.edit { it[PreferencesKeys.SUBTITLE_FONT] = font.name }
    }

    suspend fun updateIsSubtitleBold(isBold: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.IS_SUBTITLE_BOLD] = isBold }
    }

    suspend fun updateForceAssSubtitleOverride(force: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.FORCE_ASS_SUBTITLE_OVERRIDE] = force }
    }

    suspend fun updateVideoFilters(
        shouldApply: Boolean,
        isBrightnessEnabled: Boolean, brightness: Float,
        isContrastEnabled: Boolean, contrast: Float,
        isSaturationEnabled: Boolean, saturation: Float,
        isHueEnabled: Boolean, hue: Float,
        isGammaEnabled: Boolean, gamma: Float,
        isSharpeningEnabled: Boolean, sharpening: Float
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SHOULD_APPLY_VIDEO_FILTERS] = shouldApply
            prefs[PreferencesKeys.IS_VIDEO_BRIGHTNESS_FILTER_ENABLED] = isBrightnessEnabled
            prefs[PreferencesKeys.VIDEO_BRIGHTNESS] = brightness
            prefs[PreferencesKeys.IS_VIDEO_CONTRAST_FILTER_ENABLED] = isContrastEnabled
            prefs[PreferencesKeys.VIDEO_CONTRAST] = contrast
            prefs[PreferencesKeys.IS_VIDEO_SATURATION_FILTER_ENABLED] = isSaturationEnabled
            prefs[PreferencesKeys.VIDEO_SATURATION] = saturation
            prefs[PreferencesKeys.IS_VIDEO_HUE_FILTER_ENABLED] = isHueEnabled
            prefs[PreferencesKeys.VIDEO_HUE] = hue
            prefs[PreferencesKeys.IS_VIDEO_GAMMA_FILTER_ENABLED] = isGammaEnabled
            prefs[PreferencesKeys.VIDEO_GAMMA] = gamma
            prefs[PreferencesKeys.IS_VIDEO_SHARPENING_FILTER_ENABLED] = isSharpeningEnabled
            prefs[PreferencesKeys.VIDEO_SHARPENING] = sharpening
        }
    }
    suspend fun updateSeekGestureEnabled(enabled: Boolean) { context.dataStore.edit { it[PreferencesKeys.SEEK_GESTURE_ENABLED] = enabled } }
    suspend fun updateSeekSensitivity(sensitivity: Float) { context.dataStore.edit { it[PreferencesKeys.SEEK_SENSITIVITY] = sensitivity } }
    suspend fun updateBrightnessGestureEnabled(enabled: Boolean) { context.dataStore.edit { it[PreferencesKeys.BRIGHTNESS_GESTURE_ENABLED] = enabled } }
    suspend fun updateBrightnessSensitivity(sensitivity: Float) { context.dataStore.edit { it[PreferencesKeys.BRIGHTNESS_SENSITIVITY] = sensitivity } }
    suspend fun updateVolumeGestureEnabled(enabled: Boolean) { context.dataStore.edit { it[PreferencesKeys.VOLUME_GESTURE_ENABLED] = enabled } }
    suspend fun updateVolumeSensitivity(sensitivity: Float) { context.dataStore.edit { it[PreferencesKeys.VOLUME_SENSITIVITY] = sensitivity } }
    suspend fun updateTwoFingerAction(action: MultiFingerAction) { context.dataStore.edit { it[PreferencesKeys.TWO_FINGER_ACTION] = action.name } }
    suspend fun updateThreeFingerAction(action: MultiFingerAction) { context.dataStore.edit { it[PreferencesKeys.THREE_FINGER_ACTION] = action.name } }
    suspend fun updateLongPressEnabled(enabled: Boolean) { context.dataStore.edit { it[PreferencesKeys.LONG_PRESS_ENABLED] = enabled } }
    suspend fun updateLongPressSpeed(speed: Float) { context.dataStore.edit { it[PreferencesKeys.LONG_PRESS_SPEED] = speed } }
    suspend fun updateDoubleTapAction(action: DoubleTapAction) { context.dataStore.edit { it[PreferencesKeys.DOUBLE_TAP_ACTION] = action.name } }
}

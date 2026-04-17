package com.devson.nosvedplayer.repository

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

// Extension property to get DataStore instance via Context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_settings")

enum class OrientationMode { VIDEO_ORIENTATION, LANDSCAPE, REVERSE_LANDSCAPE, AUTO_ROTATION, SYSTEM_DEFAULT }
enum class FullScreenMode { ON, OFF, AUTO_SWITCH }
enum class SoftButtonMode { SHOW, HIDE, AUTO_HIDE }

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
    val showRemainingTime: Boolean = false
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
        // null stored as absent = follow system
        val DARK_THEME_SET = booleanPreferencesKey("dark_theme_set")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val YOUTUBE_PLAYER_STYLE = booleanPreferencesKey("youtube_player_style")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        // Onboarding
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        // Theme palette
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
    }

    val playbackSettingsFlow: Flow<PlaybackSettings> = context.dataStore.data
        .map { preferences ->
            val seekDuration = preferences[PreferencesKeys.SEEK_DURATION] ?: 10
            val seekBarStyle = preferences[PreferencesKeys.SEEK_BAR_STYLE] ?: "DEFAULT"
            val controlIconSize = preferences[PreferencesKeys.CONTROL_ICON_SIZE] ?: "MEDIUM"
            val autoPlay = preferences[PreferencesKeys.AUTO_PLAY] ?: false
            val showSeekButtons = preferences[PreferencesKeys.SHOW_SEEK_BUTTONS] ?: true
            val fastplaySpeed = preferences[PreferencesKeys.FASTPLAY_SPEED] ?: 2.0f
            PlaybackSettings(
                seekDurationSeconds = seekDuration,
                seekBarStyle = seekBarStyle,
                controlIconSize = controlIconSize,
                autoPlayEnabled = autoPlay,
                showSeekButtons = showSeekButtons,
                fastplaySpeed = fastplaySpeed,
                orientationMode = try { OrientationMode.valueOf(preferences[PreferencesKeys.ORIENTATION_MODE] ?: OrientationMode.SYSTEM_DEFAULT.name) } catch (e: Exception) { OrientationMode.SYSTEM_DEFAULT },
                fullScreenMode = try { FullScreenMode.valueOf(preferences[PreferencesKeys.FULL_SCREEN_MODE] ?: FullScreenMode.AUTO_SWITCH.name) } catch (e: Exception) { FullScreenMode.AUTO_SWITCH },
                softButtonMode = try { SoftButtonMode.valueOf(preferences[PreferencesKeys.SOFT_BUTTON_MODE] ?: SoftButtonMode.AUTO_HIDE.name) } catch (e: Exception) { SoftButtonMode.AUTO_HIDE },
                showElapsedTimeOverlay = preferences[PreferencesKeys.SHOW_ELAPSED_TIME_OVERLAY] ?: false,
                showBatteryClockOverlay = preferences[PreferencesKeys.SHOW_BATTERY_CLOCK_OVERLAY] ?: false,
                showScreenRotationButton = preferences[PreferencesKeys.SHOW_SCREEN_ROTATION_BUTTON] ?: true,
                pauseWhenObstructed = preferences[PreferencesKeys.PAUSE_WHEN_OBSTRUCTED] ?: true,
                showRemainingTime = preferences[PreferencesKeys.SHOW_REMAINING_TIME] ?: false
            )
        }

    /**
     * Emits null when the user hasn't set a preference (follow system),
     * true for explicit dark, false for explicit light.
     */
    val isDarkThemeFlow: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        if (prefs[PreferencesKeys.DARK_THEME_SET] == true) {
            prefs[PreferencesKeys.DARK_THEME]
        } else null
    }

    val isDeveloperModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DEVELOPER_MODE] ?: false
    }

    val useYoutubePlayerStyleFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        // Defaults to TRUE so YouTube-style controls are shown on first launch,
        // immediately showcasing the custom player UI to app-store reviewers.
        prefs[PreferencesKeys.YOUTUBE_PLAYER_STYLE] ?: true
    }

    /** Emits false until the user completes (or skips) the onboarding flow. */
    val hasSeenOnboardingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HAS_SEEN_ONBOARDING] ?: false
    }

    /** false = use custom Nosved palette; true = use Material You wallpaper colours (SDK 31+) */
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DYNAMIC_COLOR] ?: false
    }

    /**
     * Emits the persisted palette name string (e.g. "CINEMATIC", "BLUE").
     * Defaults to "CINEMATIC" on first launch.
     * Use [com.devson.nosvedplayer.ui.theme.AppThemePaletteHelper.fromKey] to map back to the enum.
     */
    val selectedPaletteFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SELECTED_PALETTE] ?: "BLUE"
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_THEME] = isDark
            prefs[PreferencesKeys.DARK_THEME_SET] = true
        }
    }

    /** Clears the explicit dark/light override - theme follows the system setting. */
    suspend fun resetDarkTheme() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_THEME_SET] = false
            prefs.remove(PreferencesKeys.DARK_THEME)
        }
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DEVELOPER_MODE] = enabled
        }
    }

    suspend fun setYoutubePlayerStyle(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.YOUTUBE_PLAYER_STYLE] = enabled
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

    val isNavBarTransparentFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.NAV_BAR_TRANSPARENT] ?: true
    }

    suspend fun setNavBarTransparent(transparent: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NAV_BAR_TRANSPARENT] = transparent }
    }
}

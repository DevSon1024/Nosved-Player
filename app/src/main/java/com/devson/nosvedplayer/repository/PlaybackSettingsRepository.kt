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

data class PlaybackSettings(
    val seekDurationSeconds: Int,
    val seekBarStyle: String,
    val controlIconSize: String,
    val autoPlayEnabled: Boolean,
    val showSeekButtons: Boolean = true,
    val fastplaySpeed: Float = 2.0f
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
                fastplaySpeed = fastplaySpeed
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
        prefs[PreferencesKeys.YOUTUBE_PLAYER_STYLE] ?: false
    }

    /** false = use custom Nosved palette; true = use Material You wallpaper colours (SDK 31+) */
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DYNAMIC_COLOR] ?: false
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DARK_THEME] = isDark
            prefs[PreferencesKeys.DARK_THEME_SET] = true
        }
    }

    /** Clears the explicit dark/light override — theme follows the system setting. */
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

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DYNAMIC_COLOR] = enabled
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
}

package com.devson.nosvedplayer.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    val controlIconSize: String
)

class PlaybackSettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val SEEK_DURATION = intPreferencesKey("seek_duration_seconds")
        val SEEK_BAR_STYLE = stringPreferencesKey("seek_bar_style")
        val CONTROL_ICON_SIZE = stringPreferencesKey("control_icon_size")
    }

    val playbackSettingsFlow: Flow<PlaybackSettings> = context.dataStore.data
        .map { preferences ->
            val seekDuration = preferences[PreferencesKeys.SEEK_DURATION] ?: 10
            val seekBarStyle = preferences[PreferencesKeys.SEEK_BAR_STYLE] ?: "DEFAULT"
            val controlIconSize = preferences[PreferencesKeys.CONTROL_ICON_SIZE] ?: "MEDIUM"

            PlaybackSettings(
                seekDurationSeconds = seekDuration,
                seekBarStyle = seekBarStyle,
                controlIconSize = controlIconSize
            )
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
}

package com.devson.nosvedplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.repository.PlaybackSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = PlaybackSettingsRepository(application.applicationContext)

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * true = use Material You (wallpaper-based) colours, false = Nosved custom palette.
     * Only takes visual effect on API 31+.
     */
    val dynamicColor: StateFlow<Boolean> = settingsRepo.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
}

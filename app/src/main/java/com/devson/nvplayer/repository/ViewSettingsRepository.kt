package com.devson.nvplayer.repository

import android.content.Context
import android.content.SharedPreferences
import com.devson.nvplayer.model.DefaultScreen
import com.devson.nvplayer.model.ViewSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViewSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("view_settings", Context.MODE_PRIVATE)

    private val _viewSettingsFlow = MutableStateFlow(loadSettings())
    val viewSettingsFlow: StateFlow<ViewSettings> = _viewSettingsFlow.asStateFlow()

    private fun loadSettings(): ViewSettings {
        return ViewSettings(
            recognizeNoMedia = prefs.getBoolean("recognize_no_media", false),
            showHiddenFiles = prefs.getBoolean("show_hidden_files", false),
            showFloatingButton = prefs.getBoolean("show_floating_button", true),
            selectByThumbnail = prefs.getBoolean("select_by_thumbnail", false),
            enableFabPreview = prefs.getBoolean("enable_fab_preview", true),
            scanFoldersList = prefs.getStringSet("scan_folders_list", emptySet()) ?: emptySet(),
            showHistoryCard = prefs.getBoolean("show_history_card", true),
            showVideoCard = prefs.getBoolean("show_video_card", true),
            showStorageTracker = prefs.getBoolean("show_storage_tracker", true),
            defaultScreen = try {
                DefaultScreen.valueOf(prefs.getString("default_screen", DefaultScreen.HOME.name) ?: DefaultScreen.HOME.name)
            } catch (e: Exception) {
                DefaultScreen.HOME
            }
        )
    }

    private fun updateSettings(updater: (ViewSettings) -> ViewSettings) {
        val current = _viewSettingsFlow.value
        val updated = updater(current)
        _viewSettingsFlow.value = updated
        
        prefs.edit().apply {
            putBoolean("recognize_no_media", updated.recognizeNoMedia)
            putBoolean("show_hidden_files", updated.showHiddenFiles)
            putBoolean("show_floating_button", updated.showFloatingButton)
            putBoolean("select_by_thumbnail", updated.selectByThumbnail)
            putBoolean("enable_fab_preview", updated.enableFabPreview)
            putStringSet("scan_folders_list", updated.scanFoldersList)
            putBoolean("show_history_card", updated.showHistoryCard)
            putBoolean("show_video_card", updated.showVideoCard)
            putBoolean("show_storage_tracker", updated.showStorageTracker)
            putString("default_screen", updated.defaultScreen.name)
            apply()
        }
    }

    suspend fun updateRecognizeNoMedia(recognize: Boolean) {
        updateSettings { it.copy(recognizeNoMedia = recognize) }
    }

    suspend fun updateShowHiddenFiles(show: Boolean) {
        updateSettings { it.copy(showHiddenFiles = show) }
    }

    suspend fun updateShowFloatingButton(show: Boolean) {
        updateSettings { it.copy(showFloatingButton = show) }
    }

    suspend fun updateSelectByThumbnail(select: Boolean) {
        updateSettings { it.copy(selectByThumbnail = select) }
    }

    suspend fun updateEnableFabPreview(enable: Boolean) {
        updateSettings { it.copy(enableFabPreview = enable) }
    }

    suspend fun updateScanFoldersList(folders: Set<String>) {
        updateSettings { it.copy(scanFoldersList = folders) }
    }

    suspend fun updateShowHistoryCard(show: Boolean) {
        updateSettings { it.copy(showHistoryCard = show) }
    }

    suspend fun updateShowVideoCard(show: Boolean) {
        updateSettings { it.copy(showVideoCard = show) }
    }

    suspend fun updateShowStorageTracker(show: Boolean) {
        updateSettings { it.copy(showStorageTracker = show) }
    }

    suspend fun updateDefaultScreen(screen: DefaultScreen) {
        updateSettings { it.copy(defaultScreen = screen) }
    }
}

package com.devson.nvplayer.repository

import android.content.Context
import android.content.SharedPreferences
import com.devson.nvplayer.model.DefaultScreen
import com.devson.nvplayer.model.ViewSettings
import com.devson.nvplayer.model.LayoutMode
import com.devson.nvplayer.model.SortField
import com.devson.nvplayer.model.SortDirection
import com.devson.nvplayer.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViewSettingsRepository private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: ViewSettingsRepository? = null

        fun getInstance(context: Context): ViewSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ViewSettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    private val prefs: SharedPreferences = context.getSharedPreferences("view_settings", Context.MODE_PRIVATE)

    private val _viewSettingsFlow = MutableStateFlow(loadSettings())
    val viewSettingsFlow: StateFlow<ViewSettings> = _viewSettingsFlow.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _viewSettingsFlow.value = loadSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadSettings(): ViewSettings {
        return ViewSettings(
            showQuickFab = prefs.getBoolean("show_quick_fab", true),
            selectByThumbnail = prefs.getBoolean("select_by_thumbnail", false),
            enableFabPreview = prefs.getBoolean("enable_fab_preview", true),
            scanFoldersList = prefs.getStringSet("scan_folders_list", emptySet()) ?: emptySet(),
            showHistoryCard = prefs.getBoolean("show_history_card", true),
            showStorageTracker = prefs.getBoolean("show_storage_tracker", true),
            defaultScreen = try {
                DefaultScreen.valueOf(prefs.getString("default_screen", DefaultScreen.HOME.name) ?: DefaultScreen.HOME.name)
            } catch (e: Exception) {
                DefaultScreen.HOME
            },
            layoutMode = try {
                LayoutMode.valueOf(prefs.getString("layout_mode", LayoutMode.LIST.name) ?: LayoutMode.LIST.name)
            } catch (e: Exception) {
                LayoutMode.LIST
            },
            gridColumns = prefs.getInt("grid_columns", 2),
            showThumbnail = prefs.getBoolean("show_thumbnail", true),
            showLength = prefs.getBoolean("show_length", true),
            displayLengthOverThumbnail = prefs.getBoolean("display_length_over_thumbnail", true),
            showFileExtension = prefs.getBoolean("show_file_extension", true),
            showSize = prefs.getBoolean("show_size", true),
            showDate = prefs.getBoolean("show_date", true),
            showPath = prefs.getBoolean("show_path", false),
            showPlayedTime = prefs.getBoolean("show_played_time", true),
            showResolution = prefs.getBoolean("show_resolution", true),
            showFrameRate = prefs.getBoolean("show_frame_rate", true),
            sortField = try {
                SortField.valueOf(prefs.getString("sort_field", SortField.TITLE.name) ?: SortField.TITLE.name)
            } catch (e: Exception) {
                SortField.TITLE
            },
            sortDirection = try {
                SortDirection.valueOf(prefs.getString("sort_direction", SortDirection.ASCENDING.name) ?: SortDirection.ASCENDING.name)
            } catch (e: Exception) {
                SortDirection.ASCENDING
            },
            viewMode = try {
                ViewMode.valueOf(prefs.getString("view_mode", ViewMode.ALL_FOLDERS.name) ?: ViewMode.ALL_FOLDERS.name)
            } catch (e: Exception) {
                ViewMode.ALL_FOLDERS
            }
        )
    }

    private fun updateSettings(updater: (ViewSettings) -> ViewSettings) {
        val current = _viewSettingsFlow.value
        val updated = updater(current)
        _viewSettingsFlow.value = updated
        
        prefs.edit().apply {
            putBoolean("show_quick_fab", updated.showQuickFab)
            putBoolean("select_by_thumbnail", updated.selectByThumbnail)
            putBoolean("enable_fab_preview", updated.enableFabPreview)
            putStringSet("scan_folders_list", updated.scanFoldersList)
            putBoolean("show_history_card", updated.showHistoryCard)
            putBoolean("show_storage_tracker", updated.showStorageTracker)
            putString("default_screen", updated.defaultScreen.name)
            
            putString("layout_mode", updated.layoutMode.name)
            putInt("grid_columns", updated.gridColumns)
            putBoolean("show_thumbnail", updated.showThumbnail)
            putBoolean("show_length", updated.showLength)
            putBoolean("display_length_over_thumbnail", updated.displayLengthOverThumbnail)
            putBoolean("show_file_extension", updated.showFileExtension)
            putBoolean("show_size", updated.showSize)
            putBoolean("show_date", updated.showDate)
            putBoolean("show_path", updated.showPath)
            putBoolean("show_played_time", updated.showPlayedTime)
            putBoolean("show_resolution", updated.showResolution)
            putBoolean("show_frame_rate", updated.showFrameRate)
            putString("sort_field", updated.sortField.name)
            putString("sort_direction", updated.sortDirection.name)
            putString("view_mode", updated.viewMode.name)
            apply()
        }
    }

    suspend fun updateShowQuickFab(show: Boolean) {
        updateSettings { it.copy(showQuickFab = show) }
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

    suspend fun updateShowStorageTracker(show: Boolean) {
        updateSettings { it.copy(showStorageTracker = show) }
    }

    suspend fun updateDefaultScreen(screen: DefaultScreen) {
        updateSettings { it.copy(defaultScreen = screen) }
    }

    suspend fun updateLayoutMode(mode: LayoutMode) {
        updateSettings { it.copy(layoutMode = mode) }
    }

    suspend fun updateGridColumns(cols: Int) {
        updateSettings { it.copy(gridColumns = cols) }
    }

    suspend fun updateSortField(field: SortField) {
        updateSettings { it.copy(sortField = field) }
    }

    suspend fun updateSortDirection(dir: SortDirection) {
        updateSettings { it.copy(sortDirection = dir) }
    }

    suspend fun updateShowThumbnail(show: Boolean) {
        updateSettings { it.copy(showThumbnail = show) }
    }

    suspend fun updateShowLength(show: Boolean) {
        updateSettings { it.copy(showLength = show) }
    }

    suspend fun updateShowFileExtension(show: Boolean) {
        updateSettings { it.copy(showFileExtension = show) }
    }

    suspend fun updateShowPlayedTime(show: Boolean) {
        updateSettings { it.copy(showPlayedTime = show) }
    }

    suspend fun updateShowResolution(show: Boolean) {
        updateSettings { it.copy(showResolution = show) }
    }

    suspend fun updateShowPath(show: Boolean) {
        updateSettings { it.copy(showPath = show) }
    }

    suspend fun updateShowSize(show: Boolean) {
        updateSettings { it.copy(showSize = show) }
    }

    suspend fun updateShowDate(show: Boolean) {
        updateSettings { it.copy(showDate = show) }
    }

    suspend fun updateDisplayLengthOverThumbnail(show: Boolean) {
        updateSettings { it.copy(displayLengthOverThumbnail = show) }
    }

    suspend fun updateShowFrameRate(show: Boolean) {
        updateSettings { it.copy(showFrameRate = show) }
    }

    suspend fun updateViewMode(mode: ViewMode) {
        updateSettings { it.copy(viewMode = mode) }
    }
}

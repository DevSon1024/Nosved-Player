package com.devson.nosvedplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.devson.nosvedplayer.repository.ViewSettingsRepository
import com.devson.nosvedplayer.model.SortOrder
import com.devson.nosvedplayer.model.ViewSettings

class VideoListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val _videosByFolder = MutableStateFlow<Map<com.devson.nosvedplayer.model.VideoFolder, List<Video>>>(emptyMap())
    val videosByFolder: StateFlow<Map<com.devson.nosvedplayer.model.VideoFolder, List<Video>>> = _videosByFolder.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFolder = MutableStateFlow<com.devson.nosvedplayer.model.VideoFolder?>(null)
    val selectedFolder: StateFlow<com.devson.nosvedplayer.model.VideoFolder?> = _selectedFolder.asStateFlow()

    private val settingsRepository = ViewSettingsRepository(application)
    
    private val _viewSettings = MutableStateFlow(ViewSettings())
    val viewSettings: StateFlow<ViewSettings> = _viewSettings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.viewSettingsFlow.collect { settings ->
                _viewSettings.value = settings
                // Force a reload/sort when settings change if needed
            }
        }
    }

    fun loadVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            val folders = repository.getFoldersWithVideos()
            _videosByFolder.value = folders
            _isLoading.value = false
        }
    }

    fun selectFolder(folder: com.devson.nosvedplayer.model.VideoFolder?) {
        _selectedFolder.value = folder
    }

    // Settings update functions
    fun updateIsGrid(isGrid: Boolean) = viewModelScope.launch { settingsRepository.updateIsGrid(isGrid) }
    fun updateGridColumns(columns: Int) = viewModelScope.launch { settingsRepository.updateGridColumns(columns) }
    fun updateSortOrder(order: SortOrder) = viewModelScope.launch { settingsRepository.updateSortOrder(order) }
    fun updateShowThumbnail(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowThumbnail(show) }
    fun updateShowDuration(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowDuration(show) }
    fun updateShowSize(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowSize(show) }
    fun updateShowDate(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowDate(show) }
    fun updateShowSubtitleType(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowSubtitleType(show) }
    fun updateShowResolution(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowResolution(show) }
    fun updateShowFramerate(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFramerate(show) }
    fun updateShowPlayedTime(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowPlayedTime(show) }
    fun updateShowPath(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowPath(show) }
    fun updateShowFileExtension(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFileExtension(show) }
    
    // Folder specific
    fun updateShowFolderVideoCount(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFolderVideoCount(show) }
    fun updateShowFolderSize(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFolderSize(show) }
    fun updateShowFolderDate(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFolderDate(show) }
}

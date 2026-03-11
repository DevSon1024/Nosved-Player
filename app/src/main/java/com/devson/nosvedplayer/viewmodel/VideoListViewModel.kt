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

class VideoListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val _videosByFolder = MutableStateFlow<Map<String, List<Video>>>(emptyMap())
    val videosByFolder: StateFlow<Map<String, List<Video>>> = _videosByFolder.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    fun loadVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            val folders = repository.getFoldersWithVideos()
            _videosByFolder.value = folders
            _isLoading.value = false
        }
    }

    fun selectFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }
}

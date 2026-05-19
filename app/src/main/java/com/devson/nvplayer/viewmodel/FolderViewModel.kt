package com.devson.nvplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.model.VideoItem
import com.devson.nvplayer.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderViewModel(private val repository: VideoRepository) : ViewModel() {
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadVideos(folderName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val fetchedVideos = repository.getVideosByFolder(folderName)
            _videos.value = fetchedVideos
            _isLoading.value = false
        }
    }
}

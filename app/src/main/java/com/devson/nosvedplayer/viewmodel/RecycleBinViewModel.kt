package com.devson.nosvedplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecycleBinViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)

    private val _trashedVideos = MutableStateFlow<List<Video>>(emptyList())
    val trashedVideos: StateFlow<List<Video>> = _trashedVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadTrashedVideos()
    }

    fun loadTrashedVideos() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val videos = withContext(Dispatchers.IO) {
                    repository.getTrashedVideos()
                }
                _trashedVideos.value = videos
            } finally {
                _isLoading.value = false
            }
        }
    }
}

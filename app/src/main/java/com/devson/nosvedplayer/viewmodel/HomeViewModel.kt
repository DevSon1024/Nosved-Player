package com.devson.nosvedplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.repository.VideoRepository
import com.devson.nosvedplayer.repository.WatchHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepo = WatchHistoryRepository(application.applicationContext)
    private val videoRepo = VideoRepository(application.applicationContext)

    val history: StateFlow<List<WatchHistory>> = historyRepo.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latestVideos: StateFlow<List<Video>> = flow {
        val videos = videoRepo.getAllVideos()
            .sortedByDescending { it.dateAdded }
            .take(10)
        emit(videos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteHistoryItem(uri: String) {
        viewModelScope.launch { historyRepo.delete(uri) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { historyRepo.clearAll() }
    }
}

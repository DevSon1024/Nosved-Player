package com.devson.nvplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.model.WatchHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val context: Context,
    private val repository: VideoRepository
) : ViewModel() {
    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences("watch_history_prefs", Context.MODE_PRIVATE)
    
    private val _history = MutableStateFlow<List<WatchHistory>>(emptyList())
    val history: StateFlow<List<WatchHistory>> = _history.asStateFlow()

    init {
        loadWatchHistory()
    }

    fun loadWatchHistory() {
        val allPrefs = sharedPrefs.all
        val historyList = allPrefs.mapNotNull { (key, value) ->
            val pos = value as? Long ?: (value as? Int)?.toLong() ?: return@mapNotNull null
            WatchHistory(uri = key, lastPositionMs = pos)
        }
        _history.value = historyList
    }

    fun setWatchStatus(video: Video, position: Long) {
        sharedPrefs.edit().putLong(video.uri, position).apply()
        loadWatchHistory()
    }

    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            val fetchedFolders = repository.getFolders()
            _folders.value = fetchedFolders
            _isLoading.value = false
        }
    }
}

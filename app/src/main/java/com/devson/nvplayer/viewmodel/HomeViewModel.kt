package com.devson.nvplayer.viewmodel

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.model.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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
 
    val historyMapFlow: StateFlow<Map<String, WatchHistory>> = _history
        .map { list -> list.associateBy { it.uri } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _storageInfo = MutableStateFlow<Triple<Double, Double, Int>>(Triple(0.0, 0.0, 0))
    val storageInfo: StateFlow<Triple<Double, Double, Int>> = _storageInfo.asStateFlow()

    init {
        loadWatchHistory(forceVerify = true)
    }

    fun loadStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = Environment.getExternalStorageDirectory().absolutePath
                val stat = StatFs(path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availableBlocks = stat.availableBlocksLong
                
                val totalBytes = totalBlocks * blockSize
                val availableBytes = availableBlocks * blockSize
                val usedBytes = totalBytes - availableBytes
                
                val totalGB = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val usedGB = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val progress = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
                val percentage = (progress * 100).toInt()
                
                _storageInfo.value = Triple(totalGB, usedGB, percentage)
            } catch (e: Exception) {
                _storageInfo.value = Triple(0.0, 0.0, 0)
            }
        }
    }

    fun loadWatchHistory(forceVerify: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val allPrefs = sharedPrefs.all
            val timePrefs = context.getSharedPreferences("watch_history_timestamps_prefs", Context.MODE_PRIVATE)
            val historyList = allPrefs.mapNotNull { (key, value) ->
                val pos = value as? Long ?: (value as? Int)?.toLong() ?: return@mapNotNull null
                val timestamp = timePrefs.getLong(key, 0L)
                WatchHistory(uri = key, lastPositionMs = pos, lastPlayedAt = timestamp)
            }.sortedByDescending { it.lastPlayedAt }

            if (forceVerify) {
                val visibleVideos = repository.getAllVideos()
                val visibleUris = visibleVideos.map { it.uri.toString() }.toSet()
                _history.value = historyList.filter { visibleUris.contains(it.uri) }
            } else {
                _history.value = historyList
            }
            loadStorageInfo()
        }
    }

    fun setWatchStatus(video: Video, position: Long) {
        sharedPrefs.edit().putLong(video.uri, position).apply()
        val timePrefs = context.getSharedPreferences("watch_history_timestamps_prefs", Context.MODE_PRIVATE)
        timePrefs.edit().putLong(video.uri, System.currentTimeMillis()).apply()
        loadWatchHistory(forceVerify = false)
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

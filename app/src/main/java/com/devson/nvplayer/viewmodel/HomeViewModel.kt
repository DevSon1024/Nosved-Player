package com.devson.nvplayer.viewmodel

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.net.Uri
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.database.WatchHistoryEntity
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val dao = AppDatabase.getDatabase(context).watchHistoryDao()
    private var watchHistoryJob: Job? = null
    
    val networkHistory = dao.getNetworkStreams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        watchHistoryJob?.cancel()
        watchHistoryJob = viewModelScope.launch(Dispatchers.IO) {
            val metadataDao = AppDatabase.getDatabase(context).videoMetadataDao()
            dao.getAllHistoryFlow().collect { entities ->
                val allCached = metadataDao.getAllMetadataSync().associateBy { it.uri }
                val historyList = entities.map { entity ->
                    val cached = allCached[entity.uri]
                    WatchHistory(
                        uri = entity.uri,
                        lastPositionMs = entity.lastPositionMs,
                        lastPlayedAt = entity.lastPlayedAt,
                        videoTitle = entity.videoTitle,
                        durationMs = cached?.duration ?: 0L,
                        fileSize = cached?.size ?: 0L
                    )
                }
                if (forceVerify) {
                    val visibleVideos = repository.getAllVideos()
                    val visibleUris = visibleVideos.map { it.uri.toString() }.toSet()
                    _history.value = historyList.filter { item ->
                        visibleUris.contains(item.uri) || 
                        item.uri.startsWith("http") || 
                        item.uri.startsWith("ytdl") ||
                        (item.uri.startsWith("file://") && File(Uri.parse(item.uri).path ?: "").exists()) ||
                        (item.uri.startsWith("content://") && !item.uri.contains("media/external/video"))
                    }
                } else {
                    _history.value = historyList
                }
                loadStorageInfo()
            }
        }
    }

    fun setWatchStatus(video: Video, position: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(
                WatchHistoryEntity(
                    uri = video.uri,
                    lastPositionMs = position,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    fun removeFromHistory(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteHistory(uri)
        }
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

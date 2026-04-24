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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import java.io.File
import android.os.Environment

data class StorageVolumeStats(
    val name: String,
    val usedSpace: Long,
    val totalSpace: Long,
    val progress: Float
)

data class HomeUiState(
    val showHistoryCard: Boolean = true,
    val showVideoCard: Boolean = true,
    val showStorageTracker: Boolean = true,
    val storageVolumes: List<StorageVolumeStats> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepo = WatchHistoryRepository(application.applicationContext)
    private val videoRepo = VideoRepository(application.applicationContext)

    val history: StateFlow<List<WatchHistory>> = kotlinx.coroutines.flow.combine(
        historyRepo.historyFlow,
        com.devson.nosvedplayer.repository.ViewSettingsRepository(application.applicationContext).viewSettingsFlow
    ) { historyList, settings ->
        if (!settings.showHistoryCard) return@combine emptyList()
        val allowedRoots = settings.scanFoldersList.filter { !it.startsWith("HIDDEN:") }
        if (allowedRoots.isEmpty()) return@combine historyList
        historyList.filter { item ->
            allowedRoots.any { item.path.startsWith(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latestVideos: StateFlow<List<Video>> = com.devson.nosvedplayer.repository.ViewSettingsRepository(application.applicationContext).viewSettingsFlow
        .map { settings ->
            if (!settings.showVideoCard) emptyList()
            else videoRepo.getAllVideos(
                showHiddenFiles = settings.showHiddenFiles,
                recognizeNoMedia = settings.recognizeNoMedia,
                scanFoldersList = settings.scanFoldersList
            ).sortedByDescending { it.dateAdded }.take(10)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<HomeUiState> = com.devson.nosvedplayer.repository.ViewSettingsRepository(application.applicationContext).viewSettingsFlow
        .map { settings ->
            val volumes = if (settings.showStorageTracker) {
                val dirs = ContextCompat.getExternalFilesDirs(application.applicationContext, null)
                dirs.filterNotNull().map { dir ->
                    val path = dir.absolutePath
                    val isEmulated = Environment.isExternalStorageEmulated(dir)
                    val name = if (isEmulated) "Internal Storage" else "SD Card"
                    val total = dir.totalSpace
                    val usable = dir.usableSpace
                    val used = total - usable
                    val progress = if (total > 0) used.toFloat() / total.toFloat() else 0f
                    StorageVolumeStats(name, used, total, progress)
                }
            } else emptyList()
            HomeUiState(
                showHistoryCard = settings.showHistoryCard,
                showVideoCard = settings.showVideoCard,
                showStorageTracker = settings.showStorageTracker,
                storageVolumes = volumes
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun deleteHistoryItem(uri: String) {
        viewModelScope.launch { historyRepo.delete(uri) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { historyRepo.clearAll() }
    }

    fun setWatchStatus(video: Video, positionMs: Long) {
        viewModelScope.launch { historyRepo.setWatchStatus(video, positionMs) }
    }
}

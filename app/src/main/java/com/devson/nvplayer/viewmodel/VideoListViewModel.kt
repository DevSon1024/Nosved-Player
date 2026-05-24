package com.devson.nvplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.repository.ViewSettingsRepository
import com.devson.nvplayer.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class VideoListViewModel(
    private val repository: VideoRepository,
    private val viewSettingsRepo: ViewSettingsRepository
) : ViewModel() {

    // --- Raw (unfiltered) scan result — populated once per disk scan ---
    private val _rawVideosByFolder = MutableStateFlow<Map<VideoFolder, List<Video>>>(emptyMap())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _selectedFolder = MutableStateFlow<VideoFolder?>(null)
    val selectedFolder: StateFlow<VideoFolder?> = _selectedFolder.asStateFlow()

    val viewSettings: StateFlow<ViewSettings> = viewSettingsRepo.viewSettingsFlow

    private val _currentExplorerPath = MutableStateFlow<String?>(null)
    val currentExplorerPath: StateFlow<String?> = _currentExplorerPath.asStateFlow()

    private val _explorerNodes = MutableStateFlow<Pair<List<VideoFolder>, List<Video>>>(Pair(emptyList(), emptyList()))
    val explorerNodes: StateFlow<Pair<List<VideoFolder>, List<Video>>> = _explorerNodes.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private var currentSearchQuery = ""

    /**
     * Public, filtered view of videos by folder.
     * Instantly re-derived in memory whenever raw data or settings change —
     * no disk I/O is triggered by toggling showHiddenFiles / recognizeNoMedia.
     */
    val videosByFolder: StateFlow<Map<VideoFolder, List<Video>>> =
        combine(_rawVideosByFolder, viewSettingsRepo.viewSettingsFlow) { raw, settings ->
            raw.mapValues { (_, videos) ->
                videos.filter { video ->
                    val passHidden = settings.showHiddenFiles ||
                        !video.path.split("/").any { seg -> seg.startsWith(".") && seg.length > 1 }
                    val passNoMedia = !settings.recognizeNoMedia || !hasNoMediaAncestor(video.path)
                    passHidden && passNoMedia
                }
            }.filterValues { it.isNotEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Returns true if any ancestor directory of [filePath] contains a .nomedia file. */
    private fun hasNoMediaAncestor(filePath: String): Boolean {
        var dir = File(filePath).parentFile
        while (dir != null) {
            if (File(dir, ".nomedia").exists()) return true
            dir = dir.parentFile
        }
        return false
    }

    fun loadVideos(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) _isRefreshing.value = true else _isLoading.value = true
            _loadingProgress.value = 0f
            try {
                val folderItems = repository.getFolders()
                val mappedVideos = mutableMapOf<VideoFolder, List<Video>>()
                val totalFolders = folderItems.size

                if (totalFolders > 0) {
                    folderItems.forEachIndexed { index, folderItem ->
                        _loadingProgress.value = index.toFloat() / totalFolders.toFloat()
                        val videoItems = repository.getVideosByFolder(folderItem.name)
                        val videos = videoItems.map { item ->
                            val dateVal = try {
                                File(item.path).lastModified() / 1000L
                            } catch (e: Exception) {
                                0L
                            }
                            Video(
                                uri = item.uri.toString(),
                                title = item.title,
                                duration = item.duration,
                                folderName = item.folderName,
                                path = item.path,
                                size = item.size,
                                width = item.width,
                                height = item.height,
                                dateAdded = dateVal,
                                playedTime = null,
                                lastPlayedAt = null,
                                resolution = "${item.width}x${item.height}",
                                frameRate = 30.0f,
                                thumbnailUri = item.thumbnailUri?.toString()
                            )
                        }
                        if (videos.isNotEmpty()) {
                            val videoFolder = VideoFolder(
                                id = File(videos.first().path).parentFile?.absolutePath ?: folderItem.name,
                                name = folderItem.name
                            )
                            mappedVideos[videoFolder] = videos
                        }
                    }
                }

                _loadingProgress.value = 1f
                // Store raw (unfiltered) — the combine flow handles filtering reactively
                _rawVideosByFolder.value = mappedVideos
                updateExplorerNodes()
                performSearch()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun selectFolder(folder: VideoFolder?) {
        _selectedFolder.value = folder
    }

    fun clearSearch() {
        currentSearchQuery = ""
        _searchSuggestions.value = emptyList()
        loadVideos()
    }

    fun onSearchQueryChanged(query: String) {
        currentSearchQuery = query
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
        } else {
            val allVideos = videosByFolder.value.values.flatten()
            val matches = allVideos.filter { it.title.contains(query, ignoreCase = true) }
                .map { it.title }
                .distinct()
                .take(5)
            _searchSuggestions.value = matches
        }
        performSearch()
    }

    fun getSearchResults(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        return videosByFolder.value.values.flatten().filter { it.title.contains(query, ignoreCase = true) }
    }

    private fun performSearch() {
        if (currentSearchQuery.isBlank()) return
        val filtered = _rawVideosByFolder.value.mapValues { (_, videos) ->
            videos.filter { it.title.contains(currentSearchQuery, ignoreCase = true) }
        }.filterValues { it.isNotEmpty() }
        _rawVideosByFolder.value = filtered
    }

    // --- Explorer Path Navigation ---

    fun navigateToExplorerPath(path: String) {
        _currentExplorerPath.value = path
        updateExplorerNodes()
    }

    fun navigateExplorerUp() {
        val current = _currentExplorerPath.value
        if (current != null) {
            val parent = File(current).parent
            _currentExplorerPath.value = if (parent == null || parent == "/" || parent.isBlank()) null else parent
            updateExplorerNodes()
        }
    }

    private fun updateExplorerNodes() {
        val currentPath = _currentExplorerPath.value
        val folders = mutableListOf<VideoFolder>()
        val videos = mutableListOf<Video>()
        val snapshot = videosByFolder.value

        if (currentPath == null) {
            snapshot.keys.forEach { folder -> folders.add(folder) }
        } else {
            snapshot.forEach { (folder, videoList) ->
                if (folder.id == currentPath) {
                    videos.addAll(videoList)
                } else if (folder.id.startsWith(currentPath) && folder.id != currentPath) {
                    val remainingPath = folder.id.removePrefix(currentPath).removePrefix("/")
                    val nextSegment = remainingPath.substringBefore("/")
                    val subFolderId = "$currentPath/$nextSegment"
                    if (folders.none { it.id == subFolderId }) {
                        folders.add(VideoFolder(id = subFolderId, name = nextSegment))
                    }
                }
            }
        }
        _explorerNodes.value = Pair(folders.distinctBy { it.id }, videos.distinctBy { it.uri })
    }

    // --- View Settings Update Callbacks ---

    fun updateViewMode(mode: ViewMode) {
        viewModelScope.launch {
            viewSettingsRepo.updateViewMode(mode)
        }
        if (mode == ViewMode.FILES || mode == ViewMode.FOLDERS) {
            _selectedFolder.value = null
        }
    }

    fun updateLayoutMode(mode: LayoutMode) {
        viewModelScope.launch {
            viewSettingsRepo.updateLayoutMode(mode)
        }
    }

    fun updateGridColumns(cols: Int) {
        viewModelScope.launch {
            viewSettingsRepo.updateGridColumns(cols)
        }
    }

    fun updateSortField(field: SortField) {
        viewModelScope.launch {
            viewSettingsRepo.updateSortField(field)
        }
    }

    fun updateSortDirection(dir: SortDirection) {
        viewModelScope.launch {
            viewSettingsRepo.updateSortDirection(dir)
        }
    }

    fun updateShowThumbnail(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowThumbnail(show)
        }
    }

    fun updateShowLength(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowLength(show)
        }
    }

    fun updateShowFileExtension(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowFileExtension(show)
        }
    }

    fun updateShowPlayedTime(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowPlayedTime(show)
        }
    }

    fun updateShowResolution(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowResolution(show)
        }
    }

    fun updateShowPath(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowPath(show)
        }
    }

    fun updateShowSize(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowSize(show)
        }
    }

    fun updateShowDate(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowDate(show)
        }
    }

    fun updateDisplayLengthOverThumbnail(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateDisplayLengthOverThumbnail(show)
        }
    }

    fun updateShowHiddenFiles(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowHiddenFiles(show)
        }
        // No loadVideos() — combine flow re-filters _rawVideosByFolder instantly
    }

    fun updateRecognizeNoMedia(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateRecognizeNoMedia(show)
        }
        // No loadVideos() — combine flow re-filters _rawVideosByFolder instantly
    }

    fun updateShowFrameRate(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowFrameRate(show)
        }
    }

    fun updateSelectByThumbnail(select: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateSelectByThumbnail(select)
        }
    }
}

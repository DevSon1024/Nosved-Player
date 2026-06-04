package com.devson.nvplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.repository.ViewSettingsRepository
import com.devson.nvplayer.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class VideoListViewModel(
    private val repository: VideoRepository,
    private val viewSettingsRepo: ViewSettingsRepository
) : ViewModel() {

    // Raw (unfiltered) scan result - populated once per disk scan
    private val _rawVideosByFolder = MutableStateFlow<Map<VideoFolder, List<Video>>>(emptyMap())
 
    private val _historyMap = MutableStateFlow<Map<String, WatchHistory>>(emptyMap())

    fun setHistoryMap(historyMap: Map<String, WatchHistory>) {
        _historyMap.value = historyMap
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    // RELEASE FIX: Expose load errors via StateFlow so the UI can show an error state.
    // Previously, exceptions were swallowed by e.printStackTrace() which is a no-op in release.
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()
    fun clearLoadError() { _loadError.value = null }

    private val _selectedFolder = MutableStateFlow<VideoFolder?>(null)
    val selectedFolder: StateFlow<VideoFolder?> = _selectedFolder.asStateFlow()

    val viewSettings: StateFlow<ViewSettings> = viewSettingsRepo.viewSettingsFlow

    private val _currentExplorerPath = MutableStateFlow<String?>(null)
    val currentExplorerPath: StateFlow<String?> = _currentExplorerPath.asStateFlow()

    private val _explorerNodes = MutableStateFlow<Pair<List<VideoFolder>, List<Video>>>(Pair(emptyList(), emptyList()))
    val explorerNodes: StateFlow<Pair<List<VideoFolder>, List<Video>>> = _explorerNodes.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _feedVideos = MutableStateFlow<List<Video>?>(null)
    val feedVideos: StateFlow<List<Video>?> = _feedVideos.asStateFlow()

    fun setFeedVideos(videos: List<Video>?) {
        _feedVideos.value = videos
    }

    /**
     * Public, filtered view of videos by folder.
     * Instantly re-derived in memory whenever raw data or settings change -
     * no disk I/O is triggered by toggling showHiddenFiles / recognizeNoMedia.
     */
    val videosByFolder: StateFlow<Map<VideoFolder, List<Video>>> =
        combine(_rawVideosByFolder, _searchQuery) { raw, query ->
            raw.mapValues { (_, videos) ->
                videos.filter { video ->
                    val matchesPath = !video.path.contains("/.")
                    val matchesSearch = query.isBlank() || video.title.contains(query, ignoreCase = true)
                    matchesPath && matchesSearch
                }
            }.filterValues { it.isNotEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
 
    val quickFabLastPlayedVideo: StateFlow<Video?> = combine(
        videosByFolder,
        _currentExplorerPath,
        _selectedFolder,
        viewSettingsRepo.viewSettingsFlow,
        _historyMap
    ) { videosMap, explorerPath, selectedFld, settings, histMap ->
        val allVideosFlat = videosMap.values.flatten()
        val candidateVideos = when {
            settings.viewMode == ViewMode.ALL_FOLDERS && selectedFld != null -> {
                videosMap[selectedFld] ?: emptyList()
            }
            settings.viewMode == ViewMode.FOLDERS && explorerPath != null -> {
                allVideosFlat.filter { it.path.startsWith(explorerPath) }
            }
            else -> {
                allVideosFlat
            }
        }
        candidateVideos
            .mapNotNull { video ->
                val hist = histMap[video.uri]
                if (hist != null && hist.lastPlayedAt > 0L) video to hist.lastPlayedAt else null
            }
            .maxByOrNull { it.second }
            ?.first
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            combine(_currentExplorerPath, videosByFolder) { currentPath, snapshot ->
                Pair(currentPath, snapshot)
            }.collect { (currentPath, snapshot) ->
                val folders = mutableListOf<VideoFolder>()
                val videos = mutableListOf<Video>()

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
        }
    }

    fun loadVideos(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            if (forceRefresh) {
                _isRefreshing.value = true
                repository.resetThumbnailJobs()
            } else {
                _isLoading.value = true
            }
            _loadingProgress.value = 0f
            try {
                val videoItems = repository.getAllVideos()
                val mappedVideos = mutableMapOf<VideoFolder, List<Video>>()
                
                // Group by parent folder absolute path
                val groupedByPath = videoItems.groupBy { item ->
                    File(item.path).parentFile?.absolutePath ?: item.folderName
                }
                
                val totalPaths = groupedByPath.size
                var index = 0
                groupedByPath.forEach { (parentPath, items) ->
                    _loadingProgress.value = if (totalPaths > 0) index.toFloat() / totalPaths.toFloat() else 1f
                    
                    val folderName = File(parentPath).name.ifEmpty { parentPath }
                    val videos = items.map { item ->
                        Video(
                            uri = item.uri.toString(),
                            title = item.title,
                            duration = item.duration,
                            folderName = item.folderName,
                            path = item.path,
                            size = item.size,
                            width = item.width,
                            height = item.height,
                            dateAdded = item.dateModified,
                            dateModified = item.dateModified,
                            playedTime = null,
                            lastPlayedAt = null,
                            resolution = "${item.width}x${item.height}",
                            frameRate = 30.0f,
                            thumbnailUri = item.thumbnailUri?.toString()
                        )
                    }
                    if (videos.isNotEmpty()) {
                        val videoFolder = VideoFolder(
                            id = parentPath,
                            name = folderName
                        )
                        mappedVideos[videoFolder] = videos
                    }
                    index++
                }

                _loadingProgress.value = 1f
                // Store raw (unfiltered) - the combine flow handles filtering reactively
                _rawVideosByFolder.value = mappedVideos
            } catch (e: Exception) {
                // RELEASE FIX: e.printStackTrace() is silently discarded in release builds.
                // Emit to the error flow so the UI can react (show snackbar / empty state).
                android.util.Log.e("VideoListViewModel", "Failed to load videos", e)
                _loadError.value = e.localizedMessage ?: "Failed to load videos"
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
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
        } else {
            val allVideos = _rawVideosByFolder.value.values.flatten()
            val matches = allVideos.filter { it.title.contains(query, ignoreCase = true) }
                .map { it.title }
                .distinct()
                .take(5)
            _searchSuggestions.value = matches
        }
    }

    fun getSearchResults(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        return _rawVideosByFolder.value.values.flatten().filter { it.title.contains(query, ignoreCase = true) }
    }

    // Explorer Path Navigation 

    fun navigateToExplorerPath(path: String) {
        _currentExplorerPath.value = path
    }

    fun navigateExplorerUp() {
        val current = _currentExplorerPath.value
        if (current != null) {
            val parent = File(current).parent
            _currentExplorerPath.value = if (parent == null || parent == "/" || parent.isBlank()) null else parent
        }
    }

    // View Settings Update Callbacks

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

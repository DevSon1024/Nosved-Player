package com.devson.nvplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class VideoListViewModel(private val repository: VideoRepository) : ViewModel() {

    private val _videosByFolder = MutableStateFlow<Map<VideoFolder, List<Video>>>(emptyMap())
    val videosByFolder: StateFlow<Map<VideoFolder, List<Video>>> = _videosByFolder.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedFolder = MutableStateFlow<VideoFolder?>(null)
    val selectedFolder: StateFlow<VideoFolder?> = _selectedFolder.asStateFlow()

    private val _viewSettings = MutableStateFlow(ViewSettings())
    val viewSettings: StateFlow<ViewSettings> = _viewSettings.asStateFlow()

    private val _currentExplorerPath = MutableStateFlow<String?>(null)
    val currentExplorerPath: StateFlow<String?> = _currentExplorerPath.asStateFlow()

    private val _explorerNodes = MutableStateFlow<Pair<List<VideoFolder>, List<Video>>>(Pair(emptyList(), emptyList()))
    val explorerNodes: StateFlow<Pair<List<VideoFolder>, List<Video>>> = _explorerNodes.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private var allVideosList = emptyList<Video>()
    private var currentSearchQuery = ""

    fun loadVideos(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) _isRefreshing.value = true else _isLoading.value = true
            try {
                val folderItems = repository.getFolders()
                val mappedVideos = mutableMapOf<VideoFolder, List<Video>>()
                val allVideos = mutableListOf<Video>()

                folderItems.forEach { folderItem ->
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
                            frameRate = 30.0f
                        )
                    }
                    if (videos.isNotEmpty()) {
                        val videoFolder = VideoFolder(
                            id = File(videos.first().path).parentFile?.absolutePath ?: folderItem.name,
                            name = folderItem.name
                        )
                        mappedVideos[videoFolder] = videos
                        allVideos.addAll(videos)
                    }
                }

                allVideosList = allVideos
                _videosByFolder.value = mappedVideos
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
            // Generate suggestions based on matching titles
            val matches = allVideosList.filter { it.title.contains(query, ignoreCase = true) }
                .map { it.title }
                .distinct()
                .take(5)
            _searchSuggestions.value = matches
        }
        performSearch()
    }

    private fun performSearch() {
        if (currentSearchQuery.isBlank()) return
        val filteredMapped = _videosByFolder.value.mapValues { (_, videos) ->
            videos.filter { it.title.contains(currentSearchQuery, ignoreCase = true) }
        }.filterValues { it.isNotEmpty() }
        _videosByFolder.value = filteredMapped
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

        if (currentPath == null) {
            // Root Explorer: list all unique top-level directories that have videos
            _videosByFolder.value.keys.forEach { folder ->
                folders.add(folder)
            }
        } else {
            // Show only videos in the selected folder, or folders matching sub-directories
            _videosByFolder.value.forEach { (folder, videoList) ->
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
        _viewSettings.value = _viewSettings.value.copy(viewMode = mode)
    }

    fun updateLayoutMode(mode: LayoutMode) {
        _viewSettings.value = _viewSettings.value.copy(layoutMode = mode)
    }

    fun updateGridColumns(cols: Int) {
        _viewSettings.value = _viewSettings.value.copy(gridColumns = cols)
    }

    fun updateSortField(field: SortField) {
        _viewSettings.value = _viewSettings.value.copy(sortField = field)
    }

    fun updateSortDirection(dir: SortDirection) {
        _viewSettings.value = _viewSettings.value.copy(sortDirection = dir)
    }

    fun updateShowThumbnail(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showThumbnail = show)
    }

    fun updateShowLength(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showLength = show)
    }

    fun updateShowFileExtension(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showFileExtension = show)
    }

    fun updateShowPlayedTime(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showPlayedTime = show)
    }

    fun updateShowResolution(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showResolution = show)
    }

    fun updateShowPath(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showPath = show)
    }

    fun updateShowSize(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showSize = show)
    }

    fun updateShowDate(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showDate = show)
    }

    fun updateDisplayLengthOverThumbnail(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(displayLengthOverThumbnail = show)
    }

    fun updateShowHiddenFiles(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(showHiddenFiles = show)
    }

    fun updateRecognizeNoMedia(show: Boolean) {
        _viewSettings.value = _viewSettings.value.copy(recognizeNoMedia = show)
    }
}

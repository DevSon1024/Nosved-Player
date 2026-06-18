package com.devson.nvplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.data.repository.ViewSettingsRepository
import com.devson.nvplayer.domain.model.LayoutMode
import com.devson.nvplayer.domain.model.SortDirection
import com.devson.nvplayer.domain.model.SortField
import com.devson.nvplayer.domain.model.StorageVolumeInfo
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.ViewMode
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.domain.model.WatchHistory
import com.devson.nvplayer.util.getAvailableStorageVolumes
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
import com.devson.nvplayer.ui.screens.videolist.state.ExplorerItem
import com.devson.nvplayer.ui.screens.videolist.state.PathSegment

class VideoListViewModel(
    private val repository: VideoRepository,
    private val viewSettingsRepo: ViewSettingsRepository
) : ViewModel() {

    // Raw (unfiltered) scan result - populated once per disk scan
    private val _rawVideosByFolder = MutableStateFlow<Map<VideoFolder, List<Video>>>(emptyMap())
    private val _rawVideosFlat = MutableStateFlow<List<Video>>(emptyList())
 
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
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()
    fun clearLoadError() { _loadError.value = null }

    private val _selectedFolder = MutableStateFlow<VideoFolder?>(null)
    val selectedFolder: StateFlow<VideoFolder?> = _selectedFolder.asStateFlow()

    val viewSettings: StateFlow<ViewSettings> = viewSettingsRepo.viewSettingsFlow

    // Storage selection state
    private val _availableStorages = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val availableStorages: StateFlow<List<StorageVolumeInfo>> = _availableStorages.asStateFlow()

    private val _selectedStorage = MutableStateFlow<StorageVolumeInfo?>(null)
    val selectedStorage: StateFlow<StorageVolumeInfo?> = _selectedStorage.asStateFlow()

    private val _currentExplorerPath = MutableStateFlow<String>(
        android.os.Environment.getExternalStorageDirectory().absolutePath
    )
    val currentExplorerPath: StateFlow<String> = _currentExplorerPath.asStateFlow()

    private val _explorerItems = MutableStateFlow<List<ExplorerItem>>(emptyList())
    val explorerItems: StateFlow<List<ExplorerItem>> = _explorerItems.asStateFlow()

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
     * Master-filtered flat list: applies storage path filter first, then search query.
     * All subsequent derived flows consume this instead of _rawVideosFlat directly.
     */
    private val _activeVideosFlat: StateFlow<List<Video>> =
        combine(_rawVideosFlat, _selectedStorage) { raw, storage ->
            if (storage == null) raw
            else raw.filter { it.path.startsWith(storage.rootPath) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Master-filtered folder map: storage-filtered then hidden-path and search filtered.
     */
    val videosByFolder: StateFlow<Map<VideoFolder, List<Video>>> =
        combine(_rawVideosByFolder, _selectedStorage, _searchQuery) { raw, storage, query ->
            raw.mapValues { (_, videos) ->
                videos.filter { video ->
                    val matchesStorage = storage == null || video.path.startsWith(storage.rootPath)
                    val matchesPath = !video.path.contains("/.")
                    val matchesSearch = query.isBlank() || video.title.contains(query, ignoreCase = true)
                    matchesStorage && matchesPath && matchesSearch
                }
            }.filterValues { it.isNotEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val videosFlat: StateFlow<List<Video>> =
        combine(_activeVideosFlat, _searchQuery) { activeFlat, query ->
            activeFlat.filter { video ->
                val matchesPath = !video.path.contains("/.")
                val matchesSearch = query.isBlank() || video.title.contains(query, ignoreCase = true)
                matchesPath && matchesSearch
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val navContext: StateFlow<Triple<ViewMode, VideoFolder?, String?>> = combine(
        viewSettingsRepo.viewSettingsFlow,
        _selectedFolder,
        _currentExplorerPath
    ) { settings, folder, path ->
        Triple(settings.viewMode, folder, path)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(ViewMode.ALL_FOLDERS, null, null))
 
    val quickFabLastPlayedVideo: StateFlow<Video?> = combine(
        videosByFolder,
        videosFlat,
        navContext,
        _historyMap
    ) { videosMap, allVideosFlat, navCtx, histMap ->
        val (viewMode, selectedFld, explorerPath) = navCtx
        val candidateVideos = when {
            viewMode == ViewMode.ALL_FOLDERS && selectedFld != null -> {
                videosMap[selectedFld] ?: emptyList()
            }
            viewMode == ViewMode.FOLDERS && explorerPath != null -> {
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
        // Initialize storages; root path will be set before first video load
        refreshStorages(repository.context)

        viewModelScope.launch {
            combine(_currentExplorerPath, videosFlat) { currentPath, flatVideos ->
                getExplorerItemsForPath(currentPath, flatVideos)
            }.collect { items ->
                _explorerItems.value = items
            }
        }
    }

    /**
     * Detects all currently mounted storage volumes.
     * If the previously selected storage is no longer mounted, falls back to internal.
     */
    fun refreshStorages(context: Context) {
        val volumes = getAvailableStorageVolumes(context)
        _availableStorages.value = volumes

        val current = _selectedStorage.value
        if (current == null || volumes.none { it.id == current.id }) {
            // Fallback: select internal storage
            val internal = volumes.firstOrNull { it.isInternal } ?: volumes.firstOrNull()
            _selectedStorage.value = internal
            // Reset explorer path to the new root
            internal?.let { _currentExplorerPath.value = it.rootPath }
        }
    }

    /** Switches active storage and resets the explorer path to the new root. */
    fun onStorageSelected(storage: StorageVolumeInfo) {
        _selectedStorage.value = storage
        _currentExplorerPath.value = storage.rootPath
        // Clear folder selection so filtered views update cleanly
        _selectedFolder.value = null
    }

    fun loadVideos(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
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
                val metadataDao = repository.videoMetadataDao
                
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
                        val uriStr = item.uri.toString()
                        
                        val finalSize: Long
                        val finalDateModified: Long
                        val finalDuration: Long
                        
                        if (item.size > 0 && item.duration > 0) {
                            finalSize = item.size
                            finalDateModified = item.dateModified * 1000
                            finalDuration = item.duration
                        } else {
                            val cached = metadataDao.getMetadataByUri(uriStr)
                            if (cached != null) {
                                finalSize = cached.size
                                finalDateModified = cached.dateModified
                                finalDuration = cached.duration
                            } else {
                                val extracted = com.devson.nvplayer.util.getVideoMetadata(repository.context, item.uri)
                                finalSize = if (extracted.fileSize > 0) extracted.fileSize else item.size
                                finalDateModified = if (extracted.lastModified > 0) extracted.lastModified else item.dateModified * 1000
                                finalDuration = item.duration
                                
                                metadataDao.insertOrUpdate(
                                    com.devson.nvplayer.data.database.CachedVideoMetadata(
                                        uri = uriStr,
                                        size = finalSize,
                                        dateModified = finalDateModified,
                                        duration = finalDuration
                                    )
                                )
                            }
                        }

                        Video(
                            uri = uriStr,
                            title = item.title,
                            duration = finalDuration,
                            folderName = item.folderName,
                            path = item.path,
                            size = finalSize,
                            width = item.width,
                            height = item.height,
                            dateAdded = finalDateModified,
                            dateModified = finalDateModified,
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
                // Store raw (unfiltered) - the combine flows handle storage + search filtering reactively
                _rawVideosByFolder.value = mappedVideos
                _rawVideosFlat.value = mappedVideos.values.flatten()
            } catch (e: Exception) {
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
        if (folder != null) {
            val folderVideos = _rawVideosByFolder.value[folder] ?: emptyList()
            com.devson.nvplayer.domain.thumbnail.ThumbnailRepository.getInstance(repository.context)
                .startFolderThumbnailGeneration(folder.id, folderVideos, 512, 384)
        }
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
            val allVideos = _rawVideosFlat.value
            val matches = allVideos.filter { it.title.contains(query, ignoreCase = true) }
                .map { it.title }
                .distinct()
                .take(5)
            _searchSuggestions.value = matches
        }
    }

    fun getSearchResults(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        return _rawVideosFlat.value.filter { it.title.contains(query, ignoreCase = true) }
    }

    // Explorer Path Navigation

    private fun currentBaseRoot(): String =
        _selectedStorage.value?.rootPath
            ?: android.os.Environment.getExternalStorageDirectory().absolutePath

    fun navigateToExplorerPath(path: String) {
        _currentExplorerPath.value = path
        val folderVideos = _rawVideosFlat.value.filter { it.path.startsWith(path) }
        com.devson.nvplayer.domain.thumbnail.ThumbnailRepository.getInstance(repository.context)
            .startFolderThumbnailGeneration(path, folderVideos, 512, 384)
    }

    fun MapsUpInExplorer() {
        val current = _currentExplorerPath.value
        val baseRoot = currentBaseRoot()
        if (current == baseRoot) return
        val parent = File(current).parent
        if (parent != null && parent.startsWith(baseRoot)) {
            _currentExplorerPath.value = parent
        }
    }

    fun getExplorerItemsForPath(currentPath: String, allVideos: List<Video>): List<ExplorerItem> {
        val normalizedCurrentPath = currentPath.trimEnd('/')
        val prefix = "$normalizedCurrentPath/"
        
        val folders = mutableMapOf<String, VideoFolder>()
        val videosInPath = mutableListOf<Video>()
        
        allVideos.forEach { video ->
            if (video.path.startsWith(prefix)) {
                val relativePath = video.path.removePrefix(prefix)
                val nextSegment = relativePath.substringBefore('/')
                if (nextSegment == relativePath) {
                    videosInPath.add(video)
                } else {
                    val subFolderPath = "$normalizedCurrentPath/$nextSegment"
                    if (!folders.containsKey(subFolderPath)) {
                        folders[subFolderPath] = VideoFolder(
                            id = subFolderPath,
                            name = nextSegment
                        )
                    }
                }
            }
        }
        
        return folders.values.map { ExplorerItem.FolderItem(it) } +
               videosInPath.map { ExplorerItem.VideoItem(it) }
    }

    fun onPathSegmentClicked(path: String) {
        navigateToExplorerPath(path)
    }

    fun getPathSegments(currentPath: String): List<PathSegment> {
        val baseRoot = currentBaseRoot()
        val rootName = _selectedStorage.value?.name ?: "Internal Storage"

        if (!currentPath.startsWith(baseRoot)) {
            return listOf(PathSegment(rootName, baseRoot))
        }
        
        val segments = mutableListOf<PathSegment>()
        segments.add(PathSegment(rootName, baseRoot))
        
        val relativePart = currentPath.removePrefix(baseRoot).trim('/')
        if (relativePart.isNotEmpty()) {
            val parts = relativePart.split('/')
            var accumulatedPath = baseRoot
            parts.forEach { part ->
                accumulatedPath = "$accumulatedPath/$part"
                segments.add(PathSegment(part, accumulatedPath))
            }
        }
        return segments
    }

    // View Settings Update Callbacks

    fun updateViewMode(mode: ViewMode) {
        viewModelScope.launch {
            viewSettingsRepo.updateViewMode(mode)
        }
        if (mode == ViewMode.FILES || mode == ViewMode.FOLDERS) {
            _selectedFolder.value = null
        }
        // Reset explorer to the root of the active storage when switching to Explorer mode
        if (mode == ViewMode.FOLDERS) {
            _currentExplorerPath.value = currentBaseRoot()
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

package com.devson.nosvedplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.applySort
import com.devson.nosvedplayer.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.devson.nosvedplayer.repository.ViewSettingsRepository
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.SortDirection
import com.devson.nosvedplayer.model.SortField
import com.devson.nosvedplayer.model.ViewMode
import com.devson.nosvedplayer.model.ViewSettings

class VideoListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val settingsRepository = ViewSettingsRepository(application)
    
    private val _viewSettings = MutableStateFlow(ViewSettings())
    val viewSettings: StateFlow<ViewSettings> = _viewSettings.asStateFlow()

    private val _allVideosCache = MutableStateFlow<List<Video>>(emptyList())

    // Track which hidden-file settings were used for the last full load.
    // Avoids repeating the expensive file-walk scan every time the screen opens.
    private var lastLoadedShowHidden: Boolean? = null
    private var lastLoadedRecognizeNoMedia: Boolean? = null
    private var lastLoadedScanFoldersList: Set<String>? = null

    val videosByFolder: StateFlow<Map<com.devson.nosvedplayer.model.VideoFolder, List<Video>>> = combine(
        _allVideosCache,
        _viewSettings
    ) { allVideos, settings ->
        val filtered = if (settings.showHiddenFiles) {
            allVideos
        } else {
            allVideos.filter { !it.path.split('/').any { segment -> segment.startsWith(".") && segment.isNotEmpty() } }
        }
        filtered.groupBy { com.devson.nosvedplayer.model.VideoFolder(it.folderId, it.folderName) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedFolder = MutableStateFlow<com.devson.nosvedplayer.model.VideoFolder?>(null)
    val selectedFolder: StateFlow<com.devson.nosvedplayer.model.VideoFolder?> = _selectedFolder.asStateFlow()

    private val _currentExplorerPath = MutableStateFlow<String?>(null)
    val currentExplorerPath: StateFlow<String?> = _currentExplorerPath.asStateFlow()

    // Explorer nodes for the current path: Pair(Folders, Videos)
    val explorerNodes = combine(videosByFolder, _currentExplorerPath, _viewSettings) { folders, currentPath, settings ->
        val allVideos = folders.values.flatten()
        if (allVideos.isEmpty()) return@combine Pair(emptyList<com.devson.nosvedplayer.model.VideoFolder>(), emptyList<Video>())

        // Find common root if currentPath is null
        val base = currentPath ?: getCommonPrefix(allVideos.map { it.path })

        val childFolders = mutableSetOf<String>()
        val childVideos = mutableListOf<Video>()

        for (video in allVideos) {
            val path = video.path
            if (path.startsWith(base)) {
                val remainder = path.removePrefix(base)
                if (remainder.contains('/')) {
                    // It's in a subdirectory
                    val folderName = remainder.substringBefore('/')
                    childFolders.add(folderName)
                } else {
                    // It's a file in this directory
                    childVideos.add(video)
                }
            }
        }

        val mappedFolders = childFolders.map { folderName ->
            com.devson.nosvedplayer.model.VideoFolder(id = base + folderName + "/", name = folderName)
        }.sortedBy { it.name.lowercase() }

        // Apply user's sort settings to the child videos
        val sortedVideos = childVideos.applySort(settings.sortField, settings.sortDirection)

        Pair(mappedFolders, sortedVideos)
    }.stateIn(viewModelScope, SharingStarted.Lazily, Pair(emptyList(), emptyList()))



    init {
        viewModelScope.launch {
            settingsRepository.viewSettingsFlow.collect { settings ->
                val settingsChanged = _viewSettings.value != settings
                _viewSettings.value = settings
                // Re-run hidden-file scan only when the relevant toggles actually change
                val hiddenSettingsChanged =
                    settings.showHiddenFiles != lastLoadedShowHidden ||
                    settings.recognizeNoMedia != lastLoadedRecognizeNoMedia ||
                    settings.scanFoldersList != lastLoadedScanFoldersList
                if (settingsChanged && hiddenSettingsChanged && lastLoadedShowHidden != null) {
                    loadVideos()
                }
            }
        }
    }

    fun loadVideos(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _isRefreshing.value = true
            } else {
                val settings = _viewSettings.value
                // Skip expensive hidden scan if nothing has changed since last load
                if (settings.showHiddenFiles == lastLoadedShowHidden &&
                    settings.recognizeNoMedia == lastLoadedRecognizeNoMedia &&
                    settings.scanFoldersList == lastLoadedScanFoldersList &&
                    _allVideosCache.value.isNotEmpty()) {
                    _isLoading.value = false
                    return@launch
                }
                _isLoading.value = true
            }
            
            try {
                val settings = _viewSettings.value
                val videos = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repository.getAllVideos(
                        showHiddenFiles = settings.showHiddenFiles,
                        recognizeNoMedia = settings.recognizeNoMedia,
                        scanFoldersList = settings.scanFoldersList
                    )
                }
                lastLoadedShowHidden = settings.showHiddenFiles
                lastLoadedRecognizeNoMedia = settings.recognizeNoMedia
                lastLoadedScanFoldersList = settings.scanFoldersList
                _allVideosCache.value = videos
                
                // Trigger background metadata extraction for untracked items
                runBackgroundExtraction(videos)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    private fun runBackgroundExtraction(videos: List<Video>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val db = com.devson.nosvedplayer.data.NosvedDatabase.getInstance(context)
            val metadataDao = db.videoMetadataDao()
            val watchHistoryDao = db.watchHistoryDao()
            
            val cachedUris = metadataDao.getAllUris().toSet()
            val missingVideos = videos.filter { it.uri !in cachedUris }
            
            for (video in missingVideos) {
                try {
                    com.devson.nosvedplayer.util.getVideoMetadata(context, video, watchHistoryDao, metadataDao)
                    // Sleep tiny bit to avoid blocking DB repeatedly
                    kotlinx.coroutines.delay(10)
                } catch (_: Exception) {}
            }
        }
    }

    fun selectFolder(folder: com.devson.nosvedplayer.model.VideoFolder?) {
        _selectedFolder.value = folder
    }

    private fun getCommonPrefix(paths: List<String>): String {
        if (paths.isEmpty()) return "/"
        var commonPrefix = paths.first().substringBeforeLast('/') + "/"
        for (p in paths) {
            while (!p.startsWith(commonPrefix)) {
                commonPrefix = commonPrefix.substringBeforeLast('/', "").substringBeforeLast('/') + "/"
            }
        }
        if (commonPrefix == "/") commonPrefix = "/storage/" // fallback
        return commonPrefix
    }

    fun navigateToExplorerPath(path: String) {
        _currentExplorerPath.value = path
    }

    fun navigateExplorerUp(): Boolean {
        val current = _currentExplorerPath.value ?: return false
        val currentTrimmed = current.dropLast(1) // remove trailing slash
        if (!currentTrimmed.contains('/')) {
            _currentExplorerPath.value = null // Back to root
            return false
        }
        val parent = currentTrimmed.substringBeforeLast('/') + "/"
        
        val allVideos = videosByFolder.value.values.flatten()
        if (allVideos.isEmpty()) {
            _currentExplorerPath.value = null
            return false
        }
        
        val commonPrefix = getCommonPrefix(allVideos.map { it.path })

        if (parent.length < commonPrefix.length) {
            _currentExplorerPath.value = null
            return false
        } else {
            _currentExplorerPath.value = parent
            return true
        }
    }

    // Settings update functions
    fun updateViewMode(mode: ViewMode) = viewModelScope.launch { settingsRepository.updateViewMode(mode) }
    fun updateLayoutMode(mode: LayoutMode) = viewModelScope.launch { settingsRepository.updateLayoutMode(mode) }
    fun updateGridColumns(columns: Int) = viewModelScope.launch { settingsRepository.updateGridColumns(columns) }
    fun updateSortField(field: SortField) = viewModelScope.launch { settingsRepository.updateSortField(field) }
    fun updateSortDirection(direction: SortDirection) = viewModelScope.launch { settingsRepository.updateSortDirection(direction) }
    
    fun updateShowThumbnail(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowThumbnail(show) }
    fun updateShowLength(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowLength(show) }
    fun updateShowFileExtension(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFileExtension(show) }
    fun updateShowPlayedTime(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowPlayedTime(show) }
    fun updateShowResolution(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowResolution(show) }
    fun updateShowFrameRate(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowFrameRate(show) }
    fun updateShowPath(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowPath(show) }
    fun updateShowSize(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowSize(show) }
    fun updateShowDate(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowDate(show) }
    
    fun updateDisplayLengthOverThumbnail(display: Boolean) = viewModelScope.launch { settingsRepository.updateDisplayLengthOverThumbnail(display) }
    fun updateShowHiddenFiles(show: Boolean) = viewModelScope.launch { settingsRepository.updateShowHiddenFiles(show) }
    fun updateRecognizeNoMedia(recognize: Boolean) = viewModelScope.launch { settingsRepository.updateRecognizeNoMedia(recognize) }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<Video>>(emptyList())
    val searchSuggestions: StateFlow<List<Video>> = _searchSuggestions.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        val q = query.trim().lowercase()
        _searchSuggestions.value = if (q.isEmpty()) emptyList()
        else _allVideosCache.value.filter { it.title.lowercase().contains(q) }.take(8)
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
    }

    fun getSearchResults(query: String): List<Video> {
        val q = query.trim().lowercase()
        val settings = _viewSettings.value
        val all = _allVideosCache.value.let { cache ->
            if (settings.showHiddenFiles) cache
            else cache.filter { !it.path.split('/').any { seg -> seg.startsWith(".") && seg.isNotEmpty() } }
        }
        return if (q.isEmpty()) all else all.filter { it.title.lowercase().contains(q) }
    }
}

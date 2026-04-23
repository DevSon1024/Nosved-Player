package com.devson.nosvedplayer.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.applySort
import com.devson.nosvedplayer.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val explorerNodes = combine(videosByFolder, _currentExplorerPath, _viewSettings) { folders, currentPath, settings ->
        val allVideos = folders.values.flatten()
        if (allVideos.isEmpty()) return@combine Pair(emptyList<com.devson.nosvedplayer.model.VideoFolder>(), emptyList<Video>())

        val base = currentPath ?: getCommonPrefix(allVideos.map { it.path })

        val childFolders = mutableSetOf<String>()
        val childVideos = mutableListOf<Video>()

        for (video in allVideos) {
            val path = video.path
            if (path.startsWith(base)) {
                val remainder = path.removePrefix(base)
                if (remainder.contains('/')) {
                    val folderName = remainder.substringBefore('/')
                    childFolders.add(folderName)
                } else {
                    childVideos.add(video)
                }
            }
        }

        val mappedFolders = childFolders.map { folderName ->
            com.devson.nosvedplayer.model.VideoFolder(id = base + folderName + "/", name = folderName)
        }.sortedBy { it.name.lowercase() }

        val sortedVideos = childVideos.applySort(settings.sortField, settings.sortDirection)
        Pair(mappedFolders, sortedVideos)
    }.stateIn(viewModelScope, SharingStarted.Lazily, Pair(emptyList(), emptyList()))

    // Debounce handle for ContentObserver triggers to avoid burst re-scans
    private var mediaStoreObserverJob: Job? = null

    // ContentObserver registered against MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // Debounce: wait 800 ms after the last notification before syncing
            mediaStoreObserverJob?.cancel()
            mediaStoreObserverJob = viewModelScope.launch {
                delay(800)
                triggerDifferentialSync()
            }
        }
    }

    init {
        // Register MediaStore observer for external video changes
        getApplication<Application>().contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver
        )

        viewModelScope.launch {
            settingsRepository.viewSettingsFlow.collect { settings ->
                val settingsChanged = _viewSettings.value != settings
                _viewSettings.value = settings
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
                val videos = withContext(Dispatchers.IO) {
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
                runBackgroundExtraction(videos)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Quick-refresh: first prunes stale DB rows via File.exists(), then runs a
     * differential sync to add any newly-appeared files. Only the delta is merged
     * into the cache - no full wipe or re-scan.
     */
    fun quickRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Step 1: prune stale watch-history rows
                withContext(Dispatchers.IO) {
                    repository.quickRefreshValidation()
                }

                val settings = _viewSettings.value
                val currentCache = _allVideosCache.value
                val cachedUriSet = currentCache.map { it.uri }.toSet()

                // Step 2: differential sync against current MediaStore state
                withContext(Dispatchers.IO) {
                    repository.differentialSync(
                        cachedUris = cachedUriSet,
                        showHiddenFiles = settings.showHiddenFiles,
                        recognizeNoMedia = settings.recognizeNoMedia,
                        scanFoldersList = settings.scanFoldersList,
                        onStaleUris = { staleUris ->
                            // Remove deleted or hidden-renamed entries from cache on Main
                            viewModelScope.launch {
                                _allVideosCache.value = _allVideosCache.value.filter { it.uri !in staleUris }
                            }
                        }
                    )
                }.also { newVideos ->
                    if (newVideos.isNotEmpty()) {
                        _allVideosCache.value = _allVideosCache.value + newVideos
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Triggered by the ContentObserver; performs a silent differential sync. */
    private fun triggerDifferentialSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = _viewSettings.value
            val cachedUriSet = _allVideosCache.value.map { it.uri }.toSet()

            val newVideos = repository.differentialSync(
                cachedUris = cachedUriSet,
                showHiddenFiles = settings.showHiddenFiles,
                recognizeNoMedia = settings.recognizeNoMedia,
                scanFoldersList = settings.scanFoldersList,
                onStaleUris = { staleUris ->
                    viewModelScope.launch {
                        _allVideosCache.value = _allVideosCache.value.filter { it.uri !in staleUris }
                    }
                }
            )

            if (newVideos.isNotEmpty()) {
                viewModelScope.launch {
                    _allVideosCache.value = _allVideosCache.value + newVideos
                }
            }
        }
    }

    private fun runBackgroundExtraction(videos: List<Video>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val db = com.devson.nosvedplayer.data.NosvedDatabase.getInstance(context)
            val metadataDao = db.videoMetadataDao()
            val watchHistoryDao = db.watchHistoryDao()

            val cachedUris = metadataDao.getAllUris().toSet()
            val missingVideos = videos.filter { it.uri !in cachedUris }

            for (video in missingVideos) {
                try {
                    com.devson.nosvedplayer.util.getVideoMetadata(context, video, watchHistoryDao, metadataDao)
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
        if (commonPrefix == "/") commonPrefix = "/storage/"
        return commonPrefix
    }

    fun navigateToExplorerPath(path: String) {
        _currentExplorerPath.value = path
    }

    fun navigateExplorerUp(): Boolean {
        val current = _currentExplorerPath.value ?: return false
        val currentTrimmed = current.dropLast(1)
        if (!currentTrimmed.contains('/')) {
            _currentExplorerPath.value = null
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

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaStoreObserver)
    }
}

package com.devson.nvplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.repository.FolderFilterMode
import com.devson.nvplayer.data.repository.PlaybackSettingsRepository
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
import com.devson.nvplayer.util.SearchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.devson.nvplayer.domain.model.isPathHidden
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import com.devson.nvplayer.data.database.CachedVideoMetadata
import com.devson.nvplayer.data.media.VideoMetadataExtractor
import com.devson.nvplayer.ui.screens.videolist.state.ExplorerItem
import com.devson.nvplayer.ui.screens.videolist.state.PathSegment

class VideoListViewModel(
    private val repository: VideoRepository,
    private val viewSettingsRepo: ViewSettingsRepository,
    private val playbackSettingsRepo: PlaybackSettingsRepository
) : ViewModel() {

    // Raw (unfiltered) scan result - populated once per disk scan
    private val _rawVideosByFolder = MutableStateFlow<Map<VideoFolder, List<Video>>>(emptyMap())
    private val _rawVideosFlat = MutableStateFlow<List<Video>>(emptyList())
    val rawVideosFlat: StateFlow<List<Video>> = _rawVideosFlat.asStateFlow()
 
    private val _historyMap = MutableStateFlow<Map<String, WatchHistory>>(emptyMap())

    fun setHistoryMap(historyMap: Map<String, WatchHistory>) {
        _historyMap.value = historyMap
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isDiscoveringHidden = MutableStateFlow(false)
    val isDiscoveringHidden: StateFlow<Boolean> = _isDiscoveringHidden.asStateFlow()
    private var hiddenDiscoveryJob: kotlinx.coroutines.Job? = null

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

    private val _isSelectionActive = MutableStateFlow(false)
    val isSelectionActive: StateFlow<Boolean> = _isSelectionActive.asStateFlow()

    fun setSelectionActive(active: Boolean) {
        _isSelectionActive.value = active
    }

    /**
     * Master-filtered flat list: applies storage path filter, hidden filter, then folder filter mode (whitelist/blacklist).
     * All subsequent derived flows consume this instead of _rawVideosFlat directly.
     */
    private val _activeVideosFlat: StateFlow<List<Video>> =
        combine(
            _rawVideosFlat,
            _selectedStorage,
            playbackSettingsRepo.playbackSettingsFlow,
            viewSettingsRepo.viewSettingsFlow
        ) { raw, storage, settings, viewSettings ->
            val showHidden = viewSettings.showHiddenFolders
            val hiddenFiltered = if (showHidden) raw else raw.filter { !it.isPathHidden }

            val storageFiltered = if (storage == null) hiddenFiltered
            else {
                val cleanRoot = storage.rootPath.trimEnd('/')
                hiddenFiltered.filter { it.path == cleanRoot || it.path.startsWith("$cleanRoot/") }
            }

            when (settings.folderFilterMode) {
                FolderFilterMode.WHITELIST -> {
                    val whitelist = settings.whitelistedFolders
                    if (whitelist.isEmpty()) emptyList()
                    else storageFiltered.filter { video ->
                        whitelist.any { video.path.startsWith(it) }
                    }
                }
                FolderFilterMode.BLACKLIST -> {
                    val blacklist = settings.blacklistedFolders
                    if (blacklist.isEmpty()) storageFiltered
                    else storageFiltered.filter { video ->
                        blacklist.none { video.path.startsWith(it) }
                    }
                }
                FolderFilterMode.NONE -> storageFiltered
            }
        }.flowOn(Dispatchers.Default)
         .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeVideosFlat: StateFlow<List<Video>> = _activeVideosFlat

    /**
     * Master-filtered folder map: storage+folder-mode filtered, then hidden-path and search filtered.
     */
    val videosByFolder: StateFlow<Map<VideoFolder, List<Video>>> =
        combine(
            _rawVideosByFolder,
            _selectedStorage,
            _searchQuery,
            playbackSettingsRepo.playbackSettingsFlow,
            viewSettingsRepo.viewSettingsFlow
        ) { raw, storage, query, settings, viewSettings ->
            val showHidden = viewSettings.showHiddenFolders
            raw.mapNotNull { (folder, videos) ->
                if (!showHidden && folder.isPathHidden) return@mapNotNull null
                val filteredVideos = videos.filter { video ->
                    val matchesStorage = if (storage == null) true else {
                        val cleanRoot = storage.rootPath.trimEnd('/')
                        video.path == cleanRoot || video.path.startsWith("$cleanRoot/")
                    }
                    val matchesHidden = showHidden || !video.isPathHidden
                    val matchesSearch = query.isBlank() || SearchUtils.matches(video.title, query)
                    val matchesFilter = when (settings.folderFilterMode) {
                        FolderFilterMode.WHITELIST -> {
                            val whitelist = settings.whitelistedFolders
                            whitelist.isEmpty() || whitelist.any { video.path.startsWith(it) }
                        }
                        FolderFilterMode.BLACKLIST -> {
                            settings.blacklistedFolders.none { video.path.startsWith(it) }
                        }
                        FolderFilterMode.NONE -> true
                    }
                    matchesStorage && matchesHidden && matchesSearch && matchesFilter
                }
                if (filteredVideos.isNotEmpty()) folder to filteredVideos else null
            }.toMap()
        }.flowOn(Dispatchers.Default)
         .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val videosFlat: StateFlow<List<Video>> =
        combine(_activeVideosFlat, _searchQuery) { activeFlat, query ->
            activeFlat.filter { video ->
                val matchesSearch = query.isBlank() || SearchUtils.matches(video.title, query)
                matchesSearch
            }
        }.flowOn(Dispatchers.Default)
         .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            loadVideos(forceRefresh = false)
        }
    }

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

        viewModelScope.launch {
            viewSettingsRepo.viewSettingsFlow
                .map { it.showHiddenFolders }
                .distinctUntilChanged()
                .collect { showHidden ->
                    if (showHidden) {
                        discoverHiddenVideos()
                    } else {
                        hiddenDiscoveryJob?.cancel()
                        _isDiscoveringHidden.value = false
                    }
                }
        }

        try {
            repository.context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver
            )
        } catch (e: Exception) {
            android.util.Log.e("VideoListViewModel", "Failed to register ContentObserver", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        hiddenDiscoveryJob?.cancel()
        try {
            repository.context.contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Exception) {
            android.util.Log.e("VideoListViewModel", "Failed to unregister ContentObserver", e)
        }
    }

    /**
     * Detects all currently mounted storage volumes.
     * If the previously selected storage is no longer mounted, falls back to internal.
     */
    fun refreshStorages(context: Context) {
        val oldVolumes = _availableStorages.value
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

        // If a new external volume was connected, refresh videos automatically
        val hasNewExternalVolume = volumes.any { newVol ->
            !newVol.isInternal && oldVolumes.none { oldVol -> oldVol.id == newVol.id }
        }
        if (hasNewExternalVolume) {
            loadVideos(forceRefresh = true)
        }
    }

    /** Switches active storage and resets the explorer path to the new root. */
    fun onStorageSelected(storage: StorageVolumeInfo) {
        _selectedStorage.value = storage
        _currentExplorerPath.value = storage.rootPath
        // Clear folder selection so filtered views update cleanly
        _selectedFolder.value = null

        // If switching to an external storage that has not been scanned yet, trigger a scan
        val cleanRoot = storage.rootPath.trimEnd('/')
        if (!storage.isInternal && _rawVideosFlat.value.none { it.path == cleanRoot || it.path.startsWith("$cleanRoot/") }) {
            loadVideos(forceRefresh = true)
        }
    }

    fun loadVideos(forceRefresh: Boolean = false) {
        if (!forceRefresh && _rawVideosFlat.value.isNotEmpty()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val isInitial = _rawVideosFlat.value.isEmpty()
            if (forceRefresh) {
                _isRefreshing.value = true
                repository.resetThumbnailJobs()
                try {
                    repository.scanCommonDirectories()
                } catch (e: Exception) {
                    android.util.Log.e("VideoListViewModel", "Error scanning common directories", e)
                }
            } else if (isInitial) {
                _isLoading.value = true
            }
            _loadingProgress.value = 0f
            try {
                val videoItems = repository.getAllVideos()
                val metadataDao = repository.videoMetadataDao
                val cachedMap = try {
                    metadataDao.getAllMetadataSync().associateBy { it.uri }.toMutableMap()
                } catch (_: Exception) {
                    mutableMapOf()
                }

                // Group by parent folder absolute path
                val groupedByPath = videoItems.groupBy { item ->
                    val lastSlash = item.path.lastIndexOf('/')
                    if (lastSlash > 0) item.path.substring(0, lastSlash) else item.folderName
                }

                val mappedVideos = mutableMapOf<VideoFolder, List<Video>>()
                val uncachedVideos = mutableListOf<Triple<com.devson.nvplayer.data.model.VideoItem, Long, Long>>()

                val activeStorage = _selectedStorage.value
                val activeCleanRoot = activeStorage?.rootPath?.trimEnd('/')
                for ((parentPath, items) in groupedByPath) {
                    val cleanParent = parentPath.trimEnd('/')
                    val folderName = if (activeCleanRoot != null && cleanParent == activeCleanRoot) {
                        activeStorage?.name ?: cleanParent.substringAfterLast('/').ifEmpty { cleanParent }
                    } else {
                        cleanParent.substringAfterLast('/').ifEmpty { cleanParent }
                    }
                    val videos = items.map { item ->
                        val uriStr = item.uri.toString()
                        val cached = cachedMap[uriStr]
                        val finalSize: Long
                        val finalDateModified: Long
                        val finalDuration: Long
                        val finalFps: Float?
                        val finalEmbeddedSubs: List<String>
                        val externalSubs: List<String>

                        val finalWidth: Int
                        val finalHeight: Int

                        if (cached != null) {
                            finalSize = if (item.size > 0) item.size else cached.size
                            finalDateModified = if (item.dateModified > 0) item.dateModified * 1000 else cached.dateModified
                            finalDuration = if (item.duration > 0) item.duration else cached.duration
                            finalWidth = if (item.width > 0) item.width else cached.width
                            finalHeight = if (item.height > 0) item.height else cached.height
                            finalFps = if (cached.frameRate != null && cached.frameRate > 0f) cached.frameRate else null
                            finalEmbeddedSubs = cached.embeddedSubtitles?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                            externalSubs = cached.externalSubtitles?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

                            // Only re-probe if duration or dimensions are genuinely missing
                            if (finalDuration <= 0L || finalWidth <= 0 || finalHeight <= 0) {
                                uncachedVideos.add(Triple(item, finalDuration, finalDateModified))
                            }
                        } else {
                            finalSize = item.size
                            finalDateModified = item.dateModified * 1000
                            finalDuration = item.duration
                            finalWidth = item.width
                            finalHeight = item.height
                            finalFps = null
                            finalEmbeddedSubs = emptyList()
                            externalSubs = emptyList()
                            uncachedVideos.add(Triple(item, finalDuration, finalDateModified))
                        }

                        Video(
                            uri = uriStr,
                            title = item.title,
                            duration = finalDuration,
                            folderName = item.folderName,
                            path = item.path,
                            size = finalSize,
                            width = finalWidth,
                            height = finalHeight,
                            dateAdded = finalDateModified,
                            dateModified = finalDateModified,
                            playedTime = null,
                            lastPlayedAt = null,
                            resolution = if (finalWidth > 0 && finalHeight > 0) "${finalWidth}x${finalHeight}" else "",
                            frameRate = finalFps,
                            thumbnailUri = item.thumbnailUri?.toString(),
                            embeddedSubtitles = finalEmbeddedSubs,
                            externalSubtitles = externalSubs
                        )
                    }
                    if (videos.isNotEmpty()) {
                        mappedVideos[VideoFolder(id = parentPath, name = folderName)] = videos
                    }
                }

                _loadingProgress.value = 1f
                _rawVideosByFolder.value = mappedVideos.toMap()
                _rawVideosFlat.value = mappedVideos.values.flatten()
                _isLoading.value = false
                _isRefreshing.value = false

                // Background asynchronous metadata enhancement for uncached videos
                if (uncachedVideos.isNotEmpty()) {
                    launch(Dispatchers.IO) {
                        try {
                            val dispatcher = Dispatchers.IO.limitedParallelism(4)
                            val updatedItems = uncachedVideos.map { (item, duration, dateMod) ->
                                async(dispatcher) {
                                    val uriStr = item.uri.toString()
                                    val externalSubs = VideoMetadataExtractor.findExternalSubtitles(item.path)
                                    val details = try {
                                        withTimeoutOrNull(1500L) {
                                            VideoMetadataExtractor.extractDetails(repository.context, item.path, item.uri, duration)
                                        }
                                    } catch (_: Exception) {
                                        null
                                    }

                                    val fps = details?.frameRate
                                    val embeddedSubs = details?.embeddedSubtitles ?: emptyList()
                                    val resolvedDuration = if (duration > 0L) duration else (details?.durationMs ?: 0L)
                                    val resolvedWidth = if (item.width > 0) item.width else (details?.width ?: 0)
                                    val resolvedHeight = if (item.height > 0) item.height else (details?.height ?: 0)

                                    val cachedMetadata = CachedVideoMetadata(
                                        uri = uriStr,
                                        size = item.size,
                                        dateModified = dateMod,
                                        duration = resolvedDuration,
                                        externalSubtitleUri = null,
                                        frameRate = fps ?: -1f,
                                        embeddedSubtitles = embeddedSubs.joinToString(","),
                                        externalSubtitles = externalSubs.joinToString(","),
                                        width = resolvedWidth,
                                        height = resolvedHeight
                                    )
                                    Triple(uriStr, cachedMetadata, Triple(fps, Pair(embeddedSubs, externalSubs), Triple(resolvedDuration, resolvedWidth, resolvedHeight)))
                                }
                            }.awaitAll()

                            metadataDao.insertAll(updatedItems.map { it.second })

                            val enhancementMap = updatedItems.associate { it.first to it.third }
                            val currentByFolder = _rawVideosByFolder.value
                            val updatedByFolder = currentByFolder.mapValues { (_, folderVideos) ->
                                folderVideos.map { video ->
                                    val enhancement = enhancementMap[video.uri]
                                    if (enhancement != null) {
                                        val (fps, subsPair, dims) = enhancement
                                        val (embedded, external) = subsPair
                                        val (dur, w, h) = dims
                                        val res = if (w > 0 && h > 0) "${w}x${h}" else video.resolution
                                        video.copy(
                                            duration = if (dur > 0L) dur else video.duration,
                                            width = if (w > 0) w else video.width,
                                            height = if (h > 0) h else video.height,
                                            resolution = res,
                                            frameRate = fps,
                                            embeddedSubtitles = embedded,
                                            externalSubtitles = external
                                        )
                                    } else {
                                        video
                                    }
                                }
                            }
                            _rawVideosByFolder.value = updatedByFolder
                            _rawVideosFlat.value = updatedByFolder.values.flatten()
                        } catch (e: Exception) {
                            android.util.Log.e("VideoListViewModel", "Background metadata extraction error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoListViewModel", "Failed to load videos", e)
                _loadError.value = e.localizedMessage ?: "Failed to load videos"
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }

            if (_rawVideosFlat.value.any { it.duration <= 0L || it.width <= 0 || it.height <= 0 }) {
                repairZeroDurationVideos()
            }

            if (viewSettingsRepo.viewSettingsFlow.value.showHiddenFolders) {
                discoverHiddenVideos()
            }
        }
    }

    fun discoverHiddenVideos() {
        hiddenDiscoveryJob?.cancel()
        hiddenDiscoveryJob = viewModelScope.launch(Dispatchers.IO) {
            _isDiscoveringHidden.value = true
            try {
                val blacklisted = playbackSettingsRepo.playbackSettingsFlow.value.blacklistedFolders.toList()
                val metadataDao = repository.videoMetadataDao
                val cachedMap = try {
                    metadataDao.getAllMetadataSync().associateBy { it.uri }.toMutableMap()
                } catch (_: Exception) {
                    mutableMapOf()
                }

                repository.scanHiddenVideos(blacklisted) { batch ->
                    if (batch.isEmpty()) return@scanHiddenVideos

                    val currentRawMap = _rawVideosByFolder.value.toMutableMap()
                    val existingUris = _rawVideosFlat.value.map { it.uri }.toHashSet()
                    val newItems = batch.filter { existingUris.add(it.uri.toString()) }
                    if (newItems.isEmpty()) return@scanHiddenVideos

                    val metadataToInsert = newItems.map { item ->
                        CachedVideoMetadata(
                            uri = item.uri.toString(),
                            size = item.size,
                            dateModified = item.dateModified * 1000L,
                            duration = item.duration,
                            width = item.width,
                            height = item.height
                        )
                    }
                    metadataDao.insertAll(metadataToInsert)

                    val newVideos = newItems.map { item ->
                        val uriStr = item.uri.toString()
                        val cached = cachedMap[uriStr]
                        Video(
                            uri = uriStr,
                            title = item.title,
                            duration = if (item.duration > 0L) item.duration else (cached?.duration ?: 0L),
                            folderName = item.folderName,
                            path = item.path,
                            size = if (item.size > 0L) item.size else (cached?.size ?: 0L),
                            width = item.width,
                            height = item.height,
                            dateAdded = item.dateModified * 1000L,
                            dateModified = item.dateModified * 1000L,
                            playedTime = null,
                            lastPlayedAt = null,
                            resolution = if (item.width > 0 && item.height > 0) "${item.width}x${item.height}" else "",
                            frameRate = cached?.frameRate?.takeIf { it > 0f },
                            thumbnailUri = item.thumbnailUri?.toString(),
                            embeddedSubtitles = emptyList(),
                            externalSubtitles = emptyList()
                        )
                    }

                    val newGrouped = newVideos.groupBy { video ->
                        val lastSlash = video.path.lastIndexOf('/')
                        if (lastSlash > 0) video.path.substring(0, lastSlash) else video.folderName
                    }

                    val activeStorage = _selectedStorage.value
                    val activeCleanRoot = activeStorage?.rootPath?.trimEnd('/')

                    for ((parentPath, videos) in newGrouped) {
                        val cleanParent = parentPath.trimEnd('/')
                        val folderName = if (activeCleanRoot != null && cleanParent == activeCleanRoot) {
                            activeStorage?.name ?: cleanParent.substringAfterLast('/').ifEmpty { cleanParent }
                        } else {
                            cleanParent.substringAfterLast('/').ifEmpty { cleanParent }
                        }
                        val folderKey = currentRawMap.keys.firstOrNull { it.id == parentPath }
                            ?: VideoFolder(id = parentPath, name = folderName)
                        val existingInFolder = currentRawMap[folderKey] ?: emptyList()
                        currentRawMap[folderKey] = existingInFolder + videos
                    }

                    _rawVideosByFolder.value = currentRawMap.toMap()
                    _rawVideosFlat.value = currentRawMap.values.flatten()
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("VideoListViewModel", "Error in discoverHiddenVideos", e)
 }
 } finally {
 _isDiscoveringHidden.value = false
 }
 }
 }

 fun updateShowHiddenFolders(show: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateShowHiddenFolders(show)
        }
    }

    fun repairZeroDurationVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val zeroItems = _rawVideosFlat.value.filter { it.duration <= 0L || it.width <= 0 || it.height <= 0 }
            if (zeroItems.isEmpty()) return@launch
            val dispatcher = Dispatchers.IO.limitedParallelism(4)
            val repaired = zeroItems.map { video ->
                async(dispatcher) {
                    val file = java.io.File(video.path)
                    var dur = 0L
                    var w = 0
                    var h = 0

                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        if (file.exists() && file.canRead()) {
                            retriever.setDataSource(file.absolutePath)
                        } else {
                            retriever.setDataSource(video.path)
                        }
                        dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (rotation == 90 || rotation == 270) {
                            val temp = w
                            w = h
                            h = temp
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }

                    if (dur <= 0L || w <= 0 || h <= 0) {
                        val extractor = android.media.MediaExtractor()
                        try {
                            if (file.exists() && file.canRead()) {
                                extractor.setDataSource(file.absolutePath)
                            } else {
                                extractor.setDataSource(video.path)
                            }
                            for (i in 0 until extractor.trackCount) {
                                val format = extractor.getTrackFormat(i)
                                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                                if (mime.startsWith("video/")) {
                                    if (w <= 0 && format.containsKey(android.media.MediaFormat.KEY_WIDTH)) {
                                        w = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
                                    }
                                    if (h <= 0 && format.containsKey(android.media.MediaFormat.KEY_HEIGHT)) {
                                        h = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                                    }
                                    if (dur <= 0L && format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                                        dur = format.getLong(android.media.MediaFormat.KEY_DURATION) / 1000L
                                    }
                                    break
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            try { extractor.release() } catch (_: Exception) {}
                        }
                    }

                    if (dur > 0L || (w > 0 && h > 0)) {
                        val finalW = if (w > 0) w else video.width
                        val finalH = if (h > 0) h else video.height
                        val res = if (finalW > 0 && finalH > 0) "${finalW}x${finalH}" else ""
                        val updated = video.copy(
                            duration = if (dur > 0L) dur else video.duration,
                            width = finalW,
                            height = finalH,
                            resolution = res
                        )
                        try {
                            repository.videoMetadataDao.insertOrUpdate(
                                CachedVideoMetadata(
                                    uri = video.uri,
                                    size = video.size,
                                    dateModified = video.dateModified,
                                    duration = updated.duration,
                                    width = updated.width,
                                    height = updated.height
                                )
                            )
                        } catch (_: Exception) {}
                        updated
                    } else null
                }
            }.awaitAll().filterNotNull()

            if (repaired.isNotEmpty()) {
                val repMap = repaired.associateBy { it.uri }
                val currentByFolder = _rawVideosByFolder.value
                val updatedByFolder = currentByFolder.mapValues { (_, folderVideos) ->
                    folderVideos.map { video -> repMap[video.uri] ?: video }
                }
                _rawVideosByFolder.value = updatedByFolder
                _rawVideosFlat.value = updatedByFolder.values.flatten()
            }
        }
    }

    fun selectFolder(folder: VideoFolder?) {
        _selectedFolder.value = folder
        if (folder != null) {
            val folderVideos = _rawVideosByFolder.value[folder] ?: emptyList()
            if (folderVideos.any { it.duration <= 0L || it.width <= 0 || it.height <= 0 }) {
                repairZeroDurationVideos()
            }
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
            val allTitles = _rawVideosFlat.value.map { it.title }
            _searchSuggestions.value = SearchUtils.getSuggestions(allTitles, query, limit = 6)
        }
    }

    fun getSearchResults(query: String): List<Video> {
        if (query.isBlank()) return emptyList()
        return SearchUtils.filterAndRank(_rawVideosFlat.value, query) { it.title }
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

    fun resetExplorerToRoot() {
        _currentExplorerPath.value = currentBaseRoot()
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

    fun updateAutoScrollToLastPlayed(enabled: Boolean) {
        viewModelScope.launch {
            viewSettingsRepo.updateAutoScrollToLastPlayed(enabled)
        }
    }

    private val _lastClosedVideoUri = MutableStateFlow<String?>(null)
    val lastClosedVideoUri: StateFlow<String?> = _lastClosedVideoUri.asStateFlow()

    fun setLastClosedVideoUri(uri: String?) {
        _lastClosedVideoUri.value = uri
    }

    fun clearLastClosedVideoUri() {
        _lastClosedVideoUri.value = null
    }
}

package com.devson.nvplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.database.EpisodeEntity
import com.devson.nvplayer.data.database.MovieEntity
import com.devson.nvplayer.data.database.SeasonEntity
import com.devson.nvplayer.data.database.SeriesEntity
import com.devson.nvplayer.data.database.WatchHistoryEntity
import com.devson.nvplayer.data.model.LibraryCategory
import com.devson.nvplayer.data.model.LibraryMediaItem
import com.devson.nvplayer.data.model.LibraryMediaType
import com.devson.nvplayer.data.model.LibraryUiState
import com.devson.nvplayer.data.model.SeasonWithEpisodes
import com.devson.nvplayer.data.model.SeriesDetail
import com.devson.nvplayer.data.parser.MediaFilenameParser
import com.devson.nvplayer.data.parser.ParsedMediaInfo
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.util.SearchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    application: Application,
    val videoListViewModel: VideoListViewModel
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val mediaLibraryDao = db.mediaLibraryDao()
    private val watchHistoryDao = db.watchHistoryDao()

    private val _selectedCategory = MutableStateFlow(LibraryCategory.ALL)
    val selectedCategory: StateFlow<LibraryCategory> = _selectedCategory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
        } else {
            val allTitles = _parsedItems.value.map { it.title } + videoListViewModel.rawVideosFlat.value.map { it.title }
            _searchSuggestions.value = SearchUtils.getSuggestions(allTitles, query, limit = 6)
        }
    }

    fun clearSearchSuggestions() {
        _searchSuggestions.value = emptyList()
    }

    private val _parsedItems = MutableStateFlow<List<LibraryMediaItem>>(emptyList())
    private val parsedInfoCache = java.util.concurrent.ConcurrentHashMap<String, ParsedMediaInfo>()

    val uiState: StateFlow<LibraryUiState> = combine(
        videoListViewModel.isLoading,
        _parsedItems,
        _selectedCategory,
        watchHistoryDao.getActiveHistoryFlow(),
        videoListViewModel.activeVideosFlat
    ) { isLoading, items, category, activeHistory, activeVideos ->
        if (isLoading && items.isEmpty()) {
            LibraryUiState.Loading
        } else {
            val filteredItems = when (category) {
                LibraryCategory.ALL -> items
                LibraryCategory.MOVIES -> items.filter { it.type == LibraryMediaType.MOVIE }
                LibraryCategory.SHOWS -> items.filter { it.type == LibraryMediaType.TV_SHOW }
            }

            val continueWatching = activeHistory
                .asSequence()
                .filter { hist ->
                    val video = activeVideos.find {
                        it.uri == hist.uri ||
                        (!hist.originalPath.isNullOrBlank() && (it.path == hist.originalPath || it.uri == hist.originalPath)) ||
                        (it.path.isNotBlank() && (it.path == hist.uri || it.uri == hist.uri))
                    }
                    val dur = if (hist.durationMs > 0L) hist.durationMs else (video?.duration ?: 0L)
                    !hist.isCompleted &&
                    hist.lastPositionMs > 1000L &&
                    (dur <= 0L || hist.lastPositionMs < (dur * 0.95).toLong())
                }
                .distinctBy { hist ->
                    hist.originalPath?.takeIf { it.isNotBlank() } ?: hist.uri
                }
                .take(20)
                .map { hist ->
                    val video = activeVideos.find {
                        it.uri == hist.uri ||
                        (!hist.originalPath.isNullOrBlank() && (it.path == hist.originalPath || it.uri == hist.originalPath)) ||
                        (it.path.isNotBlank() && (it.path == hist.uri || it.uri == hist.uri))
                    }
                    val parsed = if (video != null) {
                        parsedInfoCache["${video.title}_${video.duration}"]
                            ?: MediaFilenameParser.parse(video.title, durationMillis = video.duration)
                    } else null

                    val displayTitle = when {
                        parsed is ParsedMediaInfo.TvShow -> {
                            val epText = "S%02dE%02d".format(parsed.seasonNumber ?: 1, parsed.episodeNumber ?: 1)
                            "${parsed.cleanedTitle} $epText"
                        }
                        parsed is ParsedMediaInfo.Movie -> parsed.cleanedTitle
                        !hist.videoTitle.isNullOrBlank() -> hist.videoTitle
                        video != null -> video.title
                        else -> {
                            try {
                                android.net.Uri.parse(hist.uri).lastPathSegment?.substringBeforeLast('.') ?: "Video"
                            } catch (_: Exception) {
                                "Video"
                            }
                        }
                    }

                    val mediaType = when (parsed) {
                        is ParsedMediaInfo.Movie -> LibraryMediaType.MOVIE
                        is ParsedMediaInfo.TvShow -> LibraryMediaType.TV_SHOW
                        else -> LibraryMediaType.UNCLASSIFIED
                    }

                    val duration = if (hist.durationMs > 0L) hist.durationMs else (video?.duration ?: 0L)
                    val effectiveUri = video?.uri ?: hist.uri
                    val poster = video?.thumbnailUri ?: hist.originalPath ?: effectiveUri

                    LibraryMediaItem(
                        id = effectiveUri,
                        title = displayTitle,
                        cleanedTitle = displayTitle,
                        videoUri = effectiveUri,
                        posterUri = poster,
                        backdropUri = poster,
                        type = mediaType,
                        seasonNumber = (parsed as? ParsedMediaInfo.TvShow)?.seasonNumber,
                        episodeNumber = (parsed as? ParsedMediaInfo.TvShow)?.episodeNumber,
                        durationMs = duration,
                        playbackPositionMs = hist.lastPositionMs,
                        isWatched = false,
                        lastPlayedAt = hist.lastPlayedAt
                    )
                }
                .toList()

            val recentlyAdded = filteredItems
                .take(15)

            val heroItems = items
                .filter { it.type == LibraryMediaType.MOVIE || it.type == LibraryMediaType.TV_SHOW }
                .take(5)

            LibraryUiState.Success(
                heroItems = heroItems,
                continueWatching = continueWatching,
                recentlyAdded = recentlyAdded,
                allItems = filteredItems,
                selectedCategory = category
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, LibraryUiState.Loading)

    private var lastProcessedSignature: String? = null

    init {
        // 1. Instant Room cache hydration on startup (~15ms)
        viewModelScope.launch(Dispatchers.IO) {
            loadCachedLibraryFromDb()
        }

        // 2. Background MediaStore sync with parallel parsing
        viewModelScope.launch {
            var isFirstEmission = true
            videoListViewModel.activeVideosFlat.collectLatest { videos ->
                if (videos.isEmpty()) {
                    if (_parsedItems.value.isNotEmpty()) {
                        _parsedItems.value = emptyList()
                    }
                    return@collectLatest
                }
                if (!isFirstEmission) {
                    kotlinx.coroutines.delay(200L)
                }
                isFirstEmission = false

                val signature = "${videos.size}_${videos.sumOf { it.size }}_${videos.firstOrNull()?.uri}_${videos.lastOrNull()?.uri}_${videos.firstOrNull()?.dateModified}"
                if (signature == lastProcessedSignature && _parsedItems.value.isNotEmpty()) {
                    return@collectLatest
                }
                lastProcessedSignature = signature
                processVideos(videos)
            }
        }
    }

    private suspend fun loadCachedLibraryFromDb() = withContext(Dispatchers.IO) {
        try {
            val movies = mediaLibraryDao.getAllMoviesSync()
            val series = mediaLibraryDao.getAllSeriesSync()
            if (movies.isEmpty() && series.isEmpty()) return@withContext

            val historyMap = try {
                watchHistoryDao.getAllHistorySync().associateBy { it.uri }
            } catch (_: Exception) {
                emptyMap()
            }

            val allSeasons = try {
                mediaLibraryDao.getAllSeasonsSync().groupBy { it.seriesId }
            } catch (_: Exception) {
                emptyMap()
            }

            val allEpisodes = try {
                mediaLibraryDao.getAllEpisodesSync().groupBy { it.seasonId }
            } catch (_: Exception) {
                emptyMap()
            }

            val items = ArrayList<LibraryMediaItem>(movies.size + series.size)

            for (movie in movies) {
                val history = historyMap[movie.fileUri]
                val pos = history?.lastPositionMs ?: movie.lastPlaybackPosition
                val isWatched = movie.durationMillis > 0 && pos > (movie.durationMillis * 0.9)
                items.add(
                    LibraryMediaItem(
                        id = movie.fileUri,
                        title = movie.title,
                        cleanedTitle = movie.title,
                        videoUri = movie.fileUri,
                        posterUri = movie.fileUri,
                        backdropUri = movie.fileUri,
                        type = LibraryMediaType.MOVIE,
                        year = movie.year,
                        durationMs = movie.durationMillis,
                        playbackPositionMs = pos,
                        isWatched = isWatched,
                        synopsis = "Local movie file in high definition."
                    )
                )
            }

            for (s in series) {
                val seasons = allSeasons[s.id] ?: emptyList()
                var totalEpisodes = 0
                var firstEpUri: String? = null
                var firstEpDuration = 0L
                var validSeasonsCount = 0
                for (season in seasons) {
                    val eps = allEpisodes[season.id] ?: emptyList()
                    if (eps.isNotEmpty()) {
                        validSeasonsCount++
                        totalEpisodes += eps.size
                        if (firstEpUri == null) {
                            firstEpUri = eps.first().fileUri
                            firstEpDuration = eps.first().durationMillis
                        }
                    }
                }

                if (totalEpisodes == 0) continue

                val fallbackUri = firstEpUri ?: s.posterUri ?: ""
                val history = if (firstEpUri != null) historyMap[firstEpUri] else null

                items.add(
                    LibraryMediaItem(
                        id = s.id.toString(),
                        title = s.title,
                        cleanedTitle = s.title,
                        videoUri = fallbackUri,
                        posterUri = s.posterUri ?: fallbackUri,
                        backdropUri = s.posterUri ?: fallbackUri,
                        type = LibraryMediaType.TV_SHOW,
                        seasonCount = validSeasonsCount.coerceAtLeast(1),
                        episodeCount = totalEpisodes,
                        durationMs = firstEpDuration,
                        playbackPositionMs = history?.lastPositionMs ?: 0L,
                        seriesId = s.id,
                        synopsis = s.synopsis
                    )
                )
            }

            if (_parsedItems.value.isEmpty() && items.isNotEmpty()) {
                _parsedItems.value = items
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryViewModel", "Error loading cached library", e)
        }
    }

    fun selectCategory(category: LibraryCategory) {
        _selectedCategory.value = category
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            lastProcessedSignature = null
            videoListViewModel.loadVideos(forceRefresh = true)
            val currentVideos = videoListViewModel.activeVideosFlat.value
            if (currentVideos.isNotEmpty()) {
                processVideos(currentVideos)
            }
            _isRefreshing.value = false
        }
    }

    fun removeFromHistory(videoUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                watchHistoryDao.deleteHistory(videoUri)
                val byPath = watchHistoryDao.getHistoryByPath(videoUri)
                if (byPath != null) {
                    watchHistoryDao.deleteHistory(byPath.uri)
                }
                mediaLibraryDao.updateMoviePlayback(videoUri, 0L, false)
                mediaLibraryDao.updateEpisodePlayback(videoUri, 0L, false)
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error deleting history for $videoUri", e)
            }
            val updated = _parsedItems.value.map { item ->
                if (item.videoUri == videoUri || item.id == videoUri) {
                    item.copy(playbackPositionMs = 0L)
                } else {
                    item
                }
            }
            _parsedItems.value = updated
        }
    }

    private suspend fun processVideos(videos: List<Video>) = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) {
            _parsedItems.value = emptyList()
            return@withContext
        }

        try {
            // 1. Parallelize filename parsing across Dispatchers.Default for any uncached videos
            val unparsed = videos.filter { !parsedInfoCache.containsKey("${it.title}_${it.duration}") }
            if (unparsed.isNotEmpty()) {
                withContext(Dispatchers.Default) {
                    val chunks = unparsed.chunked(64)
                    val jobs = chunks.map { chunk ->
                        async {
                            for (v in chunk) {
                                parsedInfoCache["${v.title}_${v.duration}"] =
                                    MediaFilenameParser.parse(v.title, durationMillis = v.duration)
                            }
                        }
                    }
                    jobs.forEach { it.await() }
                }
            }

            val historyList = watchHistoryDao.getAllHistorySync()
            val historyMap = mutableMapOf<String, WatchHistoryEntity>()
            historyList.forEach { h ->
                historyMap[h.uri] = h
                if (!h.originalPath.isNullOrBlank()) {
                    historyMap[h.originalPath] = h
                }
            }

            val existingSeriesMap = try {
                mediaLibraryDao.getAllSeriesSync().associateBy { it.title }.toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }

            val mediaItems = mutableListOf<LibraryMediaItem>()
            val seriesMap = mutableMapOf<String, MutableList<Pair<Video, ParsedMediaInfo>>>()
            val newMovies = mutableListOf<MovieEntity>()

            for (video in videos) {
                val parsed = parsedInfoCache["${video.title}_${video.duration}"]
                    ?: MediaFilenameParser.parse(video.title, durationMillis = video.duration)
                val history = historyMap[video.uri]
                val positionMs = history?.lastPositionMs ?: 0L
                val isWatched = video.duration > 0 && positionMs > (video.duration * 0.9)

                when (parsed) {
                    is ParsedMediaInfo.Movie -> {
                        val item = LibraryMediaItem(
                            id = video.uri,
                            title = parsed.cleanedTitle,
                            cleanedTitle = parsed.cleanedTitle,
                            videoUri = video.uri,
                            posterUri = video.thumbnailUri ?: video.uri,
                            backdropUri = video.thumbnailUri ?: video.uri,
                            type = LibraryMediaType.MOVIE,
                            year = parsed.year,
                            durationMs = video.duration,
                            playbackPositionMs = positionMs,
                            isWatched = isWatched,
                            synopsis = "Local movie file in high definition."
                        )
                        mediaItems.add(item)
                        newMovies.add(
                            MovieEntity(
                                title = parsed.cleanedTitle,
                                year = parsed.year,
                                fileUri = video.uri,
                                durationMillis = video.duration,
                                lastPlaybackPosition = positionMs,
                                isWatched = isWatched
                            )
                        )
                    }
                    is ParsedMediaInfo.TvShow -> {
                        seriesMap.getOrPut(parsed.cleanedTitle) { mutableListOf() }.add(video to parsed)
                    }
                    is ParsedMediaInfo.Unclassified -> {
                        val item = LibraryMediaItem(
                            id = video.uri,
                            title = video.title,
                            cleanedTitle = parsed.cleanedTitle,
                            videoUri = video.uri,
                            posterUri = video.thumbnailUri ?: video.uri,
                            backdropUri = video.thumbnailUri ?: video.uri,
                            type = LibraryMediaType.UNCLASSIFIED,
                            durationMs = video.duration,
                            playbackPositionMs = positionMs,
                            isWatched = isWatched
                        )
                        mediaItems.add(item)
                    }
                }
            }

            // Batch insert missing Series
            val newSeriesToInsert = mutableListOf<SeriesEntity>()
            for ((title, episodes) in seriesMap) {
                if (!existingSeriesMap.containsKey(title)) {
                    val seasons = episodes.mapNotNull {
                        (it.second as? ParsedMediaInfo.TvShow)?.seasonNumber ?: 1
                    }.distinct()
                    newSeriesToInsert.add(
                        SeriesEntity(
                            title = title,
                            synopsis = if (seasons.size > 1) "Show with ${episodes.size} episodes across ${seasons.size} seasons." else "Show with ${episodes.size} episodes."
                        )
                    )
                }
            }
            if (newSeriesToInsert.isNotEmpty()) {
                try {
                    val ids = mediaLibraryDao.insertSeriesList(newSeriesToInsert)
                    newSeriesToInsert.forEachIndexed { idx, sEntity ->
                        val id = ids.getOrNull(idx) ?: 0L
                        existingSeriesMap[sEntity.title] = sEntity.copy(id = id)
                    }
                } catch (_: Exception) {}
            }

            // Process Series / Shows groups
            for ((title, episodes) in seriesMap) {
                val firstPair = episodes.firstOrNull() ?: continue
                val seasons = episodes.mapNotNull {
                    (it.second as? ParsedMediaInfo.TvShow)?.seasonNumber ?: 1
                }.distinct()

                val seriesEntity = existingSeriesMap[title]
                val latestVideo = episodes.first().first
                val latestHistory = historyMap[latestVideo.uri]

                val item = LibraryMediaItem(
                    id = seriesEntity?.id?.toString() ?: title,
                    title = title,
                    cleanedTitle = title,
                    videoUri = latestVideo.uri,
                    posterUri = latestVideo.thumbnailUri ?: latestVideo.uri,
                    backdropUri = latestVideo.thumbnailUri ?: latestVideo.uri,
                    type = LibraryMediaType.TV_SHOW,
                    seasonCount = seasons.size.coerceAtLeast(1),
                    episodeCount = episodes.size,
                    durationMs = latestVideo.duration,
                    playbackPositionMs = latestHistory?.lastPositionMs ?: 0L,
                    seriesId = seriesEntity?.id,
                    synopsis = seriesEntity?.synopsis
                )
                mediaItems.add(item)
            }

            // Instant UI emission
            _parsedItems.value = mediaItems

            // Background batch Room sync for detail screens
            launch(Dispatchers.IO) {
                try {
                    val currentVideoUris = videos.map { it.uri }.toSet()

                    // 1. Prune deleted movies
                    val allMovies = mediaLibraryDao.getAllMoviesSync()
                    val staleMovies = allMovies.filter { it.fileUri !in currentVideoUris }
                    if (staleMovies.isNotEmpty()) {
                        mediaLibraryDao.deleteMovies(staleMovies)
                    }

                    // 2. Insert missing movies
                    val remainingMovieUris = (allMovies - staleMovies.toSet()).map { it.fileUri }.toSet()
                    val moviesToInsert = newMovies.filter { it.fileUri !in remainingMovieUris }
                    if (moviesToInsert.isNotEmpty()) {
                        mediaLibraryDao.insertMovies(moviesToInsert)
                    }

                    // 3. Prune deleted episodes
                    val allEpisodes = mediaLibraryDao.getAllEpisodesSync()
                    val staleEpisodes = allEpisodes.filter { it.fileUri !in currentVideoUris }
                    if (staleEpisodes.isNotEmpty()) {
                        mediaLibraryDao.deleteEpisodes(staleEpisodes)
                    }

                    // 4. Prune orphaned seasons & series (seasons with no episodes, series with no seasons)
                    mediaLibraryDao.deleteOrphanedSeasons()
                    mediaLibraryDao.deleteOrphanedSeries()

                    // 5. Update synopsis of existing series to reflect current episode & season counts
                    for ((title, episodes) in seriesMap) {
                        val seriesEntity = existingSeriesMap[title] ?: continue
                        val seasons = episodes.mapNotNull {
                            (it.second as? ParsedMediaInfo.TvShow)?.seasonNumber ?: 1
                        }.distinct()
                        val updatedSynopsis = if (seasons.size > 1) {
                            "Show with ${episodes.size} episodes across ${seasons.size} seasons."
                        } else {
                            "Show with ${episodes.size} episodes."
                        }
                        if (seriesEntity.synopsis != updatedSynopsis) {
                            mediaLibraryDao.updateSeriesSynopsis(seriesEntity.id, updatedSynopsis)
                        }
                    }

                    // 6. Batch insert missing seasons
                    val allExistingSeasons = mediaLibraryDao.getAllSeasonsSync().groupBy { it.seriesId }
                    val seasonsToInsert = mutableListOf<SeasonEntity>()
                    for ((title, episodes) in seriesMap) {
                        val seriesEntity = existingSeriesMap[title] ?: continue
                        val seasons = episodes.mapNotNull {
                            (it.second as? ParsedMediaInfo.TvShow)?.seasonNumber ?: 1
                        }.distinct()
                        val seriesSeasons = (allExistingSeasons[seriesEntity.id] ?: emptyList()).associateBy { it.seasonNumber }
                        for (seasonNum in seasons) {
                            if (!seriesSeasons.containsKey(seasonNum)) {
                                seasonsToInsert.add(SeasonEntity(seriesId = seriesEntity.id, seasonNumber = seasonNum))
                            }
                        }
                    }
                    if (seasonsToInsert.isNotEmpty()) {
                        mediaLibraryDao.insertSeasons(seasonsToInsert)
                    }

                    // 7. Batch insert missing episodes
                    val updatedSeasons = mediaLibraryDao.getAllSeasonsSync().groupBy { it.seriesId }
                    val allRemainingEpisodes = mediaLibraryDao.getAllEpisodesSync().associateBy { it.fileUri }
                    val newEpisodes = mutableListOf<EpisodeEntity>()

                    for ((title, episodes) in seriesMap) {
                        val seriesEntity = existingSeriesMap[title] ?: continue
                        val seriesSeasons = (updatedSeasons[seriesEntity.id] ?: emptyList()).associateBy { it.seasonNumber }

                        for (epPair in episodes) {
                            val epVideo = epPair.first
                            if (allRemainingEpisodes.containsKey(epVideo.uri)) continue

                            val epInfo = epPair.second
                            val sNum = (epInfo as? ParsedMediaInfo.TvShow)?.seasonNumber ?: 1
                            val sEntity = seriesSeasons[sNum] ?: continue
                            val epNum = (epInfo as? ParsedMediaInfo.TvShow)?.episodeNumber ?: 1
                            val epHistory = historyMap[epVideo.uri]
                            val epPos = epHistory?.lastPositionMs ?: 0L
                            val epWatched = epVideo.duration > 0 && epPos > (epVideo.duration * 0.9)

                            newEpisodes.add(
                                EpisodeEntity(
                                    seasonId = sEntity.id,
                                    episodeNumber = epNum,
                                    title = epVideo.title,
                                    fileUri = epVideo.uri,
                                    durationMillis = epVideo.duration,
                                    lastPlaybackPosition = epPos,
                                    isWatched = epWatched
                                )
                            )
                        }
                    }
                    if (newEpisodes.isNotEmpty()) {
                        mediaLibraryDao.insertEpisodes(newEpisodes)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibraryViewModel", "Error syncing library details to DB", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryViewModel", "Failed to process library videos", e)
        }
    }

    suspend fun getSeriesDetails(seriesId: Long): SeriesDetail? = withContext(Dispatchers.IO) {
        val series = mediaLibraryDao.getSeriesById(seriesId) ?: return@withContext null
        val seasons = mediaLibraryDao.getSeasonsForSeries(seriesId).firstOrNull() ?: emptyList()
        val seasonsWithEpisodes = mutableListOf<SeasonWithEpisodes>()
        var totalEpisodes = 0

        val activeVideos = videoListViewModel.activeVideosFlat.value
        val activeUris = if (activeVideos.isNotEmpty()) activeVideos.map { it.uri }.toSet() else null
        val staleEpisodes = mutableListOf<EpisodeEntity>()

        for (season in seasons) {
            val dbEpisodes = mediaLibraryDao.getEpisodesForSeason(season.id).firstOrNull() ?: emptyList()
            val episodes = if (activeUris != null) {
                val (valid, stale) = dbEpisodes.partition { it.fileUri in activeUris }
                if (stale.isNotEmpty()) {
                    staleEpisodes.addAll(stale)
                }
                valid
            } else {
                dbEpisodes
            }

            if (episodes.isNotEmpty()) {
                seasonsWithEpisodes.add(SeasonWithEpisodes(season, episodes))
                totalEpisodes += episodes.size
            }
        }

        if (staleEpisodes.isNotEmpty()) {
            try {
                mediaLibraryDao.deleteEpisodes(staleEpisodes)
                mediaLibraryDao.deleteOrphanedSeasons()
                mediaLibraryDao.deleteOrphanedSeries()
            } catch (_: Exception) {}
        }

        if (seasonsWithEpisodes.isEmpty()) return@withContext null

        SeriesDetail(
            series = series,
            seasonsWithEpisodes = seasonsWithEpisodes,
            totalEpisodes = totalEpisodes,
            type = LibraryMediaType.TV_SHOW
        )
    }

    class Factory(
        private val application: Application,
        private val videoListViewModel: VideoListViewModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(application, videoListViewModel) as T
        }
    }
}

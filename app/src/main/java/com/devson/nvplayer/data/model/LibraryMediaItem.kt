package com.devson.nvplayer.data.model

import androidx.compose.runtime.Immutable
import com.devson.nvplayer.data.database.EpisodeEntity
import com.devson.nvplayer.data.database.SeasonEntity
import com.devson.nvplayer.data.database.SeriesEntity

enum class LibraryMediaType {
    MOVIE,
    TV_SHOW,
    UNCLASSIFIED
}

enum class LibraryCategory(val displayName: String) {
    ALL("All"),
    MOVIES("Movies"),
    SHOWS("Shows")
}

@Immutable
data class LibraryMediaItem(
    val id: String,
    val title: String,
    val cleanedTitle: String,
    val videoUri: String,
    val posterUri: String? = null,
    val backdropUri: String? = null,
    val type: LibraryMediaType = LibraryMediaType.UNCLASSIFIED,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val seasonCount: Int = 1,
    val episodeCount: Int = 1,
    val year: Int? = null,
    val durationMs: Long = 0L,
    val playbackPositionMs: Long = 0L,
    val isWatched: Boolean = false,
    val seriesId: Long? = null,
    val synopsis: String? = null
)

@Immutable
data class SeasonWithEpisodes(
    val season: SeasonEntity,
    val episodes: List<EpisodeEntity>
)

@Immutable
data class SeriesDetail(
    val series: SeriesEntity,
    val seasonsWithEpisodes: List<SeasonWithEpisodes>,
    val totalEpisodes: Int = 0,
    val type: LibraryMediaType = LibraryMediaType.TV_SHOW
)

@Immutable
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Success(
        val heroItems: List<LibraryMediaItem> = emptyList(),
        val continueWatching: List<LibraryMediaItem> = emptyList(),
        val recentlyAdded: List<LibraryMediaItem> = emptyList(),
        val allItems: List<LibraryMediaItem> = emptyList(),
        val selectedCategory: LibraryCategory = LibraryCategory.ALL
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}

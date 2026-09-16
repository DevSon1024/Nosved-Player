package com.devson.nvplayer.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaLibraryDao {

    // --- Series Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: SeriesEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeriesList(series: List<SeriesEntity>): List<Long>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun getSeriesById(id: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE title = :title LIMIT 1")
    suspend fun getSeriesByTitle(title: String): SeriesEntity?

    @Query("SELECT * FROM series ORDER BY title ASC")
    fun getAllSeries(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series")
    suspend fun getAllSeriesSync(): List<SeriesEntity>

    @Delete
    suspend fun deleteSeries(series: SeriesEntity)

    // --- Season Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeason(season: SeasonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<SeasonEntity>): List<Long>

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY seasonNumber ASC")
    fun getSeasonsForSeries(seriesId: Long): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons")
    suspend fun getAllSeasonsSync(): List<SeasonEntity>

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber LIMIT 1")
    suspend fun getSeasonByNumber(seriesId: Long, seasonNumber: Int): SeasonEntity?

    @Delete
    suspend fun deleteSeason(season: SeasonEntity)

    // --- Episode Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long>

    @Query("SELECT * FROM episodes")
    suspend fun getAllEpisodesSync(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    fun getEpisodesForSeason(seasonId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE fileUri = :fileUri LIMIT 1")
    suspend fun getEpisodeByUri(fileUri: String): EpisodeEntity?

    @Query("UPDATE episodes SET lastPlaybackPosition = :position, isWatched = :isWatched WHERE fileUri = :fileUri")
    suspend fun updateEpisodePlayback(fileUri: String, position: Long, isWatched: Boolean)

    @Delete
    suspend fun deleteEpisode(episode: EpisodeEntity)

    // --- Movie Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: MovieEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>): List<Long>

    @Query("SELECT * FROM movies WHERE fileUri = :fileUri LIMIT 1")
    suspend fun getMovieByUri(fileUri: String): MovieEntity?

    @Query("SELECT * FROM movies")
    suspend fun getAllMoviesSync(): List<MovieEntity>

    @Query("SELECT * FROM movies ORDER BY title ASC")
    fun getAllMovies(): Flow<List<MovieEntity>>

    @Query("UPDATE movies SET lastPlaybackPosition = :position, isWatched = :isWatched WHERE fileUri = :fileUri")
    suspend fun updateMoviePlayback(fileUri: String, position: Long, isWatched: Boolean)

    @Delete
    suspend fun deleteMovie(movie: MovieEntity)
}

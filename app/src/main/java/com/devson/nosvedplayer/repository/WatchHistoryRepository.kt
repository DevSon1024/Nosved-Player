package com.devson.nosvedplayer.repository

import android.content.Context
import com.devson.nosvedplayer.data.NosvedDatabase
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.model.Video
import kotlinx.coroutines.flow.Flow

class WatchHistoryRepository(context: Context) {

    private val dao = NosvedDatabase.getInstance(context).watchHistoryDao()

    /** Observe history list (most recently played first). */
    val historyFlow: Flow<List<WatchHistory>> = dao.observeHistory()

    /** Call when a video starts playing to record/update it in history. */
    suspend fun recordPlay(video: Video) {
        // Preserve existing path & position so DB entry is never lost by path-based filtering
        val existing = dao.getHistoryByUri(video.uri)
        dao.upsert(
            WatchHistory(
                uri = video.uri,
                title = video.title,
                duration = video.duration,
                size = video.size,
                folderName = video.folderName,
                path = video.path.ifBlank { existing?.path ?: "" },
                lastPositionMs = existing?.lastPositionMs ?: 0L,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    /** Call when playback pauses/stops to save the current resume position. */
    suspend fun savePosition(uri: String, positionMs: Long) {
        dao.updatePosition(
            uri = uri,
            positionMs = positionMs,
            playedAt = System.currentTimeMillis()
        )
    }

    /** Set a custom watch status for a video */
    suspend fun setWatchStatus(video: Video, positionMs: Long) {
        val existing = dao.getHistoryByUri(video.uri)
        if (existing != null) {
            dao.updatePosition(video.uri, positionMs, System.currentTimeMillis())
        } else {
            dao.upsert(
                WatchHistory(
                    uri = video.uri,
                    title = video.title,
                    duration = video.duration,
                    size = video.size,
                    folderName = video.folderName,
                    path = video.path,
                    lastPositionMs = positionMs,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Delete a single history item. */
    suspend fun delete(uri: String) = dao.delete(uri)

    /** Wipe all history. */
    suspend fun clearAll() = dao.clearAll()
}

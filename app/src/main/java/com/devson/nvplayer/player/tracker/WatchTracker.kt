package com.devson.nvplayer.player.tracker

import android.util.Log
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.database.DailyWatchDao
import com.devson.nvplayer.data.database.StreakDao
import com.devson.nvplayer.data.database.StreakStateEntity
import com.devson.nvplayer.data.database.WatchHistoryDao
import com.devson.nvplayer.data.database.WatchHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class WatchTracker(
    private val watchHistoryDao: WatchHistoryDao,
    private val dailyWatchDao: DailyWatchDao,
    private val streakDao: StreakDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    val dateProvider: () -> String = { LocalDate.now().toString() },
    val clock: () -> Long = { System.currentTimeMillis() }
) {
    constructor(
        database: AppDatabase,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        dateProvider: () -> String = { LocalDate.now().toString() },
        clock: () -> Long = { System.currentTimeMillis() }
    ) : this(
        watchHistoryDao = database.watchHistoryDao(),
        dailyWatchDao = database.dailyWatchDao(),
        streakDao = database.streakDao(),
        scope = scope,
        dateProvider = dateProvider,
        clock = clock
    )

    companion object {
        private const val TAG = "WatchTracker"
        const val THROTTLE_INTERVAL_MS = 5000L
        const val SEEK_TOLERANCE_MS = 500L
    }

    var currentUri: String? = null
        private set
    var currentTitle: String? = null
        private set
    var currentFolderName: String? = null
        private set
    var currentOriginalPath: String? = null
        private set
    var durationMs: Long = 0L
        private set
    var isNetworkStream: Boolean = false
        private set

    var accumulatedWatchedMs: Long = 0L
        private set
    var hasQualified: Boolean = false
        private set

    private var lastPositionMs: Long = -1L
    private var lastWallClockTimeMs: Long = -1L
    private var lastSavedPositionMs: Long = 0L
    private var lastSaveWallClockTimeMs: Long = 0L
    private var isSeeking: Boolean = false

    fun calculateThreshold(duration: Long): Long {
        if (duration <= 0L) return Long.MAX_VALUE
        return (duration * 0.10).toLong()
    }

    fun onVideoChanged(
        uri: String,
        title: String?,
        folderName: String?,
        durationMs: Long,
        isNetworkStream: Boolean,
        originalPath: String? = null
    ) {
        flush()

        currentUri = uri
        currentTitle = title
        currentFolderName = folderName
        currentOriginalPath = originalPath
        this.durationMs = durationMs
        this.isNetworkStream = isNetworkStream
        accumulatedWatchedMs = 0L
        hasQualified = false
        lastPositionMs = -1L
        lastWallClockTimeMs = -1L
        lastSavedPositionMs = 0L
        lastSaveWallClockTimeMs = clock()
        isSeeking = false

        // Load any existing record on Dispatchers.IO to preserve progress across recreation
        scope.launch {
            try {
                val existing = watchHistoryDao.getHistory(uri)
                if (existing != null) {
                    accumulatedWatchedMs = existing.totalPlaybackTimeMs
                    val threshold = calculateThreshold(if (durationMs > 0L) durationMs else existing.durationMs)
                    if (threshold > 0L && accumulatedWatchedMs >= threshold) {
                        hasQualified = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading existing history for $uri", e)
            }
        }
    }

    fun updateVideoMetadata(title: String?, folderName: String?, durationMs: Long, path: String? = null) {
        if (!title.isNullOrBlank()) currentTitle = title
        if (!folderName.isNullOrBlank()) currentFolderName = folderName
        if (durationMs > 0L) this.durationMs = durationMs
        if (!path.isNullOrBlank()) currentOriginalPath = path
    }

    fun onSeek(targetPositionMs: Long) {
        isSeeking = true
        lastPositionMs = -1L
        lastWallClockTimeMs = -1L
    }

    fun onPause(currentPositionMs: Long? = null) {
        lastPositionMs = -1L
        lastWallClockTimeMs = -1L
        val pos = currentPositionMs ?: lastSavedPositionMs
        scope.launch {
            saveProgressInternal(pos, isQualifiedNow = false)
        }
    }

    fun onPlaybackTick(
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        playbackSpeed: Float = 1.0f,
        wallClockMs: Long = clock()
    ) {
        val uri = currentUri ?: return

        if (durationMs > 0L && this.durationMs <= 0L) {
            this.durationMs = durationMs
        }

        if (!isPlaying) {
            lastPositionMs = -1L
            lastWallClockTimeMs = -1L
            return
        }

        if (isSeeking) {
            lastPositionMs = positionMs
            lastWallClockTimeMs = wallClockMs
            isSeeking = false
            return
        }

        if (lastPositionMs < 0L || lastWallClockTimeMs <= 0L) {
            lastPositionMs = positionMs
            lastWallClockTimeMs = wallClockMs
            return
        }

        val deltaPos = positionMs - lastPositionMs
        val deltaWall = wallClockMs - lastWallClockTimeMs

        if (deltaWall <= 0L) {
            return
        }

        if (deltaPos <= 0L) {
            // Seek backwards, loop restart, or buffering at same position
            lastPositionMs = positionMs
            lastWallClockTimeMs = wallClockMs
            return
        }

        // Validate that position advance is consistent with wall-clock time and playback speed
        val speedFactor = if (playbackSpeed > 0f) playbackSpeed else 1.0f
        val maxAllowedDelta = (deltaWall * speedFactor).toLong() + SEEK_TOLERANCE_MS

        if (deltaPos > maxAllowedDelta) {
            // Forward seek detected: do not credit skipped time
            lastPositionMs = positionMs
            lastWallClockTimeMs = wallClockMs
            return
        }

        // Legitimate continuous playback advancement
        val actualDelta = minOf(deltaPos, (deltaWall * speedFactor).toLong() + 50L)
        accumulatedWatchedMs += actualDelta
        lastPositionMs = positionMs
        lastWallClockTimeMs = wallClockMs

        // Check if 10% qualification threshold reached
        val threshold = calculateThreshold(this.durationMs)
        if (!hasQualified && this.durationMs > 0L && accumulatedWatchedMs >= threshold) {
            hasQualified = true
            scope.launch {
                handleQualification(positionMs)
            }
        } else if (wallClockMs - lastSaveWallClockTimeMs >= THROTTLE_INTERVAL_MS) {
            lastSaveWallClockTimeMs = wallClockMs
            scope.launch {
                saveProgressInternal(positionMs, isQualifiedNow = false)
            }
        }
    }

    private suspend fun handleQualification(positionMs: Long) {
        val uri = currentUri ?: return
        val today = dateProvider()

        try {
            // Record in daily watch records (idempotently avoids duplicate counting)
            dailyWatchDao.recordQualifyingWatch(today, uri)

            // Check streak status
            val streak = streakDao.getStreak() ?: StreakStateEntity(id = 1)
            val lastDateStr = streak.lastQualifyingWatchDate

            if (lastDateStr != today) {
                val isConsecutive = try {
                    if (lastDateStr.isNullOrBlank()) {
                        false
                    } else {
                        val lastDate = LocalDate.parse(lastDateStr)
                        val currentDate = LocalDate.parse(today)
                        lastDate.plusDays(1) == currentDate
                    }
                } catch (_: Exception) {
                    false
                }

                val newCurrentStreak = if (isConsecutive) {
                    streak.currentStreak + 1
                } else {
                    1
                }
                val newLongestStreak = maxOf(streak.longestStreak, newCurrentStreak)

                streakDao.updateStreak(
                    currentStreak = newCurrentStreak,
                    longestStreak = newLongestStreak,
                    lastQualifyingDate = today,
                    timestamp = clock()
                )
            }

            saveProgressInternal(positionMs, isQualifiedNow = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling video qualification", e)
        }
    }

    private suspend fun saveProgressInternal(positionMs: Long, isQualifiedNow: Boolean) {
        val uri = currentUri ?: return
        try {
            val existing = watchHistoryDao.getHistory(uri)
            val today = dateProvider()
            val dur = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L)
            val progress = if (dur > 0L) (positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val isCompleted = dur > 0L && (positionMs > dur * 0.95f || dur - positionMs < 5000L)

            val entity = WatchHistoryEntity(
                uri = uri,
                lastPositionMs = positionMs,
                lastPlayedAt = clock(),
                isNetworkStream = isNetworkStream,
                videoTitle = currentTitle ?: existing?.videoTitle,
                historyId = uri,
                originalPath = currentOriginalPath ?: existing?.originalPath,
                folderName = currentFolderName ?: existing?.folderName,
                durationMs = dur,
                totalPlaybackTimeMs = accumulatedWatchedMs,
                firstWatchedAt = existing?.firstWatchedAt?.takeIf { it > 0L } ?: clock(),
                watchDate = today,
                isDeleted = false,
                isCompleted = isCompleted || (existing?.isCompleted == true),
                playbackProgress = progress
            )
            watchHistoryDao.insert(entity)
            lastSavedPositionMs = positionMs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save watch progress", e)
        }
    }

    fun flush(currentPositionMs: Long? = null) {
        val uri = currentUri ?: return
        val pos = currentPositionMs ?: lastSavedPositionMs
        scope.launch {
            saveProgressInternal(pos, isQualifiedNow = false)
        }
    }
}

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
        const val ONE_MINUTE_MS = 60_000L
        const val FIXED_THRESHOLD_MS = 30_000L

        /**
         * Calculates required actual watch time in milliseconds to qualify for daily streak progress.
         *
         * Hybrid rule:
         * - If duration is invalid or unknown (<= 0), returns Long.MAX_VALUE (cannot qualify).
         * - If duration < 60 seconds (60,000 ms):
         *       required actual watch time = 10% of video duration: (durationMs * 10) / 100
         * - If duration >= 60 seconds (60,000 ms):
         *       required actual watch time = 30 seconds (30,000 ms)
         * - Very short videos (> 0 ms) have a minimum threshold of at least 1 ms to prevent zero/negative thresholds.
         */
        fun requiredWatchTimeMs(durationMs: Long): Long {
            if (durationMs <= 0L) return Long.MAX_VALUE
            return if (durationMs < ONE_MINUTE_MS) {
                maxOf(1L, (durationMs * 10L) / 100L)
            } else {
                FIXED_THRESHOLD_MS
            }
        }
    }

    @Volatile
    var currentUri: String? = null
        private set
    @Volatile
    var currentTitle: String? = null
        private set
    @Volatile
    var currentFolderName: String? = null
        private set
    @Volatile
    var currentOriginalPath: String? = null
        private set
    @Volatile
    var durationMs: Long = 0L
        private set
    @Volatile
    var isNetworkStream: Boolean = false
        private set

    @Volatile
    var accumulatedWatchedMs: Long = 0L
        private set
    @Volatile
    var hasQualified: Boolean = false
        private set

    @Volatile
    private var todayWatchedMs: Long = 0L
    @Volatile
    private var sessionWatchedMs: Long = 0L

    private var lastPositionMs: Long = -1L
    private var lastWallClockTimeMs: Long = -1L
    private var lastSavedPositionMs: Long = 0L
    private var lastSaveWallClockTimeMs: Long = 0L
    private var isSeeking: Boolean = false

    fun requiredWatchTimeMs(durationMs: Long): Long = Companion.requiredWatchTimeMs(durationMs)

    fun calculateThreshold(duration: Long): Long = requiredWatchTimeMs(duration)

    fun onVideoChanged(
        uri: String,
        title: String?,
        folderName: String?,
        durationMs: Long,
        isNetworkStream: Boolean,
        originalPath: String? = null
    ) {
        flush()

        synchronized(this) {
            currentUri = uri
            currentTitle = title
            currentFolderName = folderName
            currentOriginalPath = originalPath
            this.durationMs = durationMs
            this.isNetworkStream = isNetworkStream
            accumulatedWatchedMs = 0L
            todayWatchedMs = 0L
            sessionWatchedMs = 0L
            hasQualified = false
            lastPositionMs = -1L
            lastWallClockTimeMs = -1L
            lastSavedPositionMs = 0L
            lastSaveWallClockTimeMs = clock()
            isSeeking = false
        }

        // Load existing record and verify qualification status on Dispatchers.IO
        scope.launch {
            try {
                val existing = watchHistoryDao.getHistory(uri)
                val today = dateProvider()
                val todayWatch = dailyWatchDao.getDailyWatch(today)
                val alreadyQualifiedToday = todayWatch?.getWatchedUrisSet()?.contains(uri) == true

                synchronized(this@WatchTracker) {
                    if (currentUri == uri) {
                        if (existing != null) {
                            accumulatedWatchedMs = maxOf(accumulatedWatchedMs, existing.totalPlaybackTimeMs)
                            if (existing.watchDate == today) {
                                todayWatchedMs = maxOf(todayWatchedMs, existing.totalPlaybackTimeMs)
                            }
                        }
                        if (alreadyQualifiedToday) {
                            hasQualified = true
                        } else {
                            val effectiveDuration = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L)
                            val threshold = requiredWatchTimeMs(effectiveDuration)
                            if (effectiveDuration > 0L && threshold != Long.MAX_VALUE) {
                                val currentWatched = maxOf(todayWatchedMs, sessionWatchedMs)
                                if (currentWatched >= threshold) {
                                    hasQualified = true
                                    val effectivePos = existing?.lastPositionMs ?: 0L
                                    val posToPass = if (lastPositionMs >= 0L) lastPositionMs else effectivePos
                                    scope.launch { handleQualification(posToPass) }
                                }
                            }
                        }
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

        if (deltaWall > 3000L) {
            // Gap in ticks (e.g. backgrounding, sleep, or thread freeze): do not credit discontinuous time
            lastPositionMs = positionMs
            lastWallClockTimeMs = wallClockMs
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
        val threshold = requiredWatchTimeMs(this.durationMs)

        val shouldQualify = synchronized(this) {
            accumulatedWatchedMs += actualDelta
            todayWatchedMs += actualDelta
            sessionWatchedMs += actualDelta
            lastPositionMs = positionMs
            lastWallClockTimeMs = wallClockMs

            if (!hasQualified && this.durationMs > 0L && threshold != Long.MAX_VALUE) {
                val currentWatched = maxOf(todayWatchedMs, sessionWatchedMs)
                if (currentWatched >= threshold) {
                    hasQualified = true
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        if (shouldQualify) {
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
            val existing = watchHistoryDao.getHistory(uri)
            val validPosition = if (positionMs >= 0L) positionMs else (existing?.lastPositionMs ?: 0L)

            // Record in daily watch records (idempotently avoids duplicate counting)
            dailyWatchDao.recordQualifyingWatch(today, uri)

            // Check streak status
            val streak = streakDao.getStreak() ?: StreakStateEntity(id = 1)
            val lastDateStr = streak.lastQualifyingWatchDate
            val yesterday = try {
                LocalDate.parse(today).minusDays(1).toString()
            } catch (_: Exception) {
                null
            }

            if (lastDateStr != today) {
                val isConsecutive = try {
                    if (!lastDateStr.isNullOrBlank() && LocalDate.parse(lastDateStr).plusDays(1) == LocalDate.parse(today)) {
                        true
                    } else if (yesterday != null) {
                        val yesterdayWatch = dailyWatchDao.getDailyWatch(yesterday)
                        val yesterdayHistory = watchHistoryDao.getHistoryForDate(yesterday)
                        (yesterdayWatch != null && yesterdayWatch.qualifyingVideoCount > 0) ||
                                yesterdayHistory.any {
                                    val d = it.durationMs
                                    val t = requiredWatchTimeMs(d)
                                    it.isCompleted || it.playbackProgress >= 0.10f || (d > 0L && t != Long.MAX_VALUE && (it.totalPlaybackTimeMs >= t || it.lastPositionMs >= t))
                                }
                    } else {
                        false
                    }
                } catch (_: Exception) {
                    false
                }

                val newCurrentStreak = if (isConsecutive) {
                    maxOf(streak.currentStreak, 1) + 1
                } else {
                    1
                }
                val newLongestStreak = maxOf(streak.longestStreak, newCurrentStreak)

                streakDao.insertOrUpdate(
                    StreakStateEntity(
                        id = 1,
                        currentStreak = newCurrentStreak,
                        longestStreak = newLongestStreak,
                        lastQualifyingWatchDate = today,
                        lastUpdatedTimestamp = clock()
                    )
                )
            }

            saveProgressInternal(validPosition, isQualifiedNow = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling video qualification", e)
        }
    }

    private suspend fun saveProgressInternal(positionMs: Long, isQualifiedNow: Boolean) {
        val uri = currentUri ?: return
        try {
            val existing = watchHistoryDao.getHistory(uri)
            val effectivePos = if (positionMs >= 0L) positionMs else (existing?.lastPositionMs ?: 0L)
            val today = dateProvider()
            val dur = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L)
            val progress = if (dur > 0L) (effectivePos.toFloat() / dur).coerceIn(0f, 1f) else (existing?.playbackProgress ?: 0f)
            val isCompleted = dur > 0L && (effectivePos > dur * 0.95f || (dur > 10000L && dur - effectivePos < 5000L))
            val effectiveWatchedMs = maxOf(accumulatedWatchedMs, existing?.totalPlaybackTimeMs ?: 0L)

            val entity = WatchHistoryEntity(
                uri = uri,
                lastPositionMs = effectivePos,
                lastPlayedAt = clock(),
                isNetworkStream = isNetworkStream,
                videoTitle = currentTitle ?: existing?.videoTitle,
                historyId = uri,
                originalPath = currentOriginalPath ?: existing?.originalPath,
                folderName = currentFolderName ?: existing?.folderName,
                durationMs = dur,
                totalPlaybackTimeMs = effectiveWatchedMs,
                firstWatchedAt = existing?.firstWatchedAt?.takeIf { it > 0L } ?: clock(),
                watchDate = today,
                isDeleted = false,
                isCompleted = isCompleted || (existing?.isCompleted == true),
                playbackProgress = progress
            )
            watchHistoryDao.insert(entity)
            lastSavedPositionMs = effectivePos
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

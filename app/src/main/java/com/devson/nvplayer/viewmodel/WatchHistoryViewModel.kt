package com.devson.nvplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.database.DailyWatchDao
import com.devson.nvplayer.data.database.DailyWatchEntity
import com.devson.nvplayer.data.database.StreakDao
import com.devson.nvplayer.data.database.StreakStateEntity
import com.devson.nvplayer.data.database.WatchHistoryDao
import com.devson.nvplayer.data.repository.WatchHistoryRepository
import com.devson.nvplayer.domain.model.VideoHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class HistoryFilter {
    ALL,
    AVAILABLE,
    DELETED
}

enum class TodayStreakStatus {
    COMPLETED,
    PENDING,
    NOT_STARTED
}

data class StreakDayActivity(
    val date: String,
    val dayOfWeek: String,
    val dayOfMonth: Int,
    val isQualified: Boolean,
    val isToday: Boolean
)

data class StreakUiState(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastQualifyingDate: String? = null,
    val isQualifiedToday: Boolean = false,
    val todayStatus: TodayStreakStatus = TodayStreakStatus.NOT_STARTED,
    val recentDays: List<StreakDayActivity> = emptyList()
)

class WatchHistoryViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
    private val watchHistoryDao: WatchHistoryDao,
    private val dailyWatchDao: DailyWatchDao,
    private val streakDao: StreakDao,
    private val dateProvider: () -> String = {
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
) : ViewModel() {

    class Factory(
        private val context: Context,
        private val dateProvider: () -> String = {
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext)
            val repo = runCatching {
                WatchHistoryRepository(appContext)
            }.getOrElse {
                WatchHistoryRepository(context = appContext, watchHistoryDao = db.watchHistoryDao())
            }
            return WatchHistoryViewModel(
                watchHistoryRepository = repo,
                watchHistoryDao = db.watchHistoryDao(),
                dailyWatchDao = db.dailyWatchDao(),
                streakDao = db.streakDao(),
                dateProvider = dateProvider
            ) as T
        }
    }

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    val historyItems: StateFlow<List<VideoHistoryItem>> = watchHistoryRepository.getVideoHistoryFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredHistory: StateFlow<List<VideoHistoryItem>> = combine(
        historyItems,
        _selectedFilter
    ) { items, filter ->
        when (filter) {
            HistoryFilter.ALL -> items
            HistoryFilter.AVAILABLE -> items.filter { !it.isDeleted }
            HistoryFilter.DELETED -> items.filter { it.isDeleted }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val streakEntityFlow: StateFlow<StreakStateEntity?> = streakDao.getStreakFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val dailyWatchesFlow: StateFlow<List<DailyWatchEntity>> = dailyWatchDao.getAllDailyWatchesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val streakUiState: StateFlow<StreakUiState> = combine(
        streakEntityFlow,
        dailyWatchesFlow,
        historyItems
    ) { streakEntity, dailyWatches, historyList ->
        calculateStreakUiState(streakEntity, dailyWatches, historyList, healDatabase = true)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StreakUiState()
    )

    private fun triggerStreakSelfHealing(
        todayStr: String,
        effectiveCurrentStreak: Int,
        longestStreak: Int,
        streakEntity: StreakStateEntity?,
        todayWatch: DailyWatchEntity?,
        qualifyingUri: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (todayWatch == null || todayWatch.qualifyingVideoCount <= 0) {
                    val uri = qualifyingUri ?: "nosved://qualified_video"
                    dailyWatchDao.recordQualifyingWatch(todayStr, uri)
                }
                if (streakEntity?.lastQualifyingWatchDate != todayStr) {
                    val updatedLongest = maxOf(longestStreak, effectiveCurrentStreak)
                    streakDao.insertOrUpdate(
                        StreakStateEntity(
                            id = 1,
                            currentStreak = effectiveCurrentStreak,
                            longestStreak = updatedLongest,
                            lastQualifyingWatchDate = todayStr,
                            lastUpdatedTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (_: Exception) {
                // Ignore Room concurrency conflicts during self-healing
            }
        }
    }

    fun calculateStreakUiState(
        streakEntity: StreakStateEntity?,
        dailyWatches: List<DailyWatchEntity>,
        historyList: List<VideoHistoryItem> = emptyList(),
        healDatabase: Boolean = false
    ): StreakUiState {
        val todayStr = dateProvider()
        val todayDate = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }

        val qualifyingDateSet = HashSet<String>()
        var todayQualifyingUri: String? = null

        for (watch in dailyWatches) {
            if (watch.qualifyingVideoCount > 0) {
                qualifyingDateSet.add(watch.date)
                if (watch.date == todayStr && todayQualifyingUri == null) {
                    todayQualifyingUri = watch.getWatchedUrisSet().firstOrNull()
                }
            }
        }

        for (item in historyList) {
            val duration = item.durationMs
            val watched = item.totalPlaybackTimeMs
            val threshold = com.devson.nvplayer.player.tracker.WatchTracker.requiredWatchTimeMs(duration)
            val isQualified = item.isCompleted || item.progress >= 0.10f ||
                (duration > 0L && threshold != Long.MAX_VALUE && (watched >= threshold || item.lastPositionMs >= threshold))
            if (isQualified) {
                val candidateDate = if (item.watchedDate.isNotBlank()) {
                    item.watchedDate
                } else if (item.lastPlayedAt > 0L) {
                    runCatching {
                        java.time.Instant.ofEpochMilli(item.lastPlayedAt)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }.getOrNull()
                } else null

                if (candidateDate != null) {
                    qualifyingDateSet.add(candidateDate)
                    if (candidateDate == todayStr && todayQualifyingUri == null) {
                        todayQualifyingUri = item.uri
                    }
                }
            }
        }

        val todayWatch = dailyWatches.find { it.date == todayStr }
        val isQualifiedToday = qualifyingDateSet.contains(todayStr) ||
                (todayWatch != null && todayWatch.qualifyingVideoCount > 0) ||
                (streakEntity?.lastQualifyingWatchDate == todayStr)

        if (isQualifiedToday) {
            qualifyingDateSet.add(todayStr)
        }

        val rawCurrent = streakEntity?.currentStreak ?: 0
        val lastDateStr = streakEntity?.lastQualifyingWatchDate

        val rawCurrentStreakFromEntity = if (rawCurrent <= 0 || lastDateStr.isNullOrBlank()) {
            0
        } else {
            val lastDate = runCatching { LocalDate.parse(lastDateStr) }.getOrNull()
            if (lastDate != null) {
                val daysBetween = ChronoUnit.DAYS.between(lastDate, todayDate)
                if (daysBetween <= 1L) {
                    rawCurrent
                } else {
                    // Missed calendar day: streak has lapsed
                    0
                }
            } else {
                rawCurrent
            }
        }

        var consecutiveDays = 0
        var checkDate = if (isQualifiedToday) todayDate else todayDate.minusDays(1)
        while (qualifyingDateSet.contains(checkDate.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
            consecutiveDays++
            checkDate = checkDate.minusDays(1)
        }

        val effectiveCurrentStreak = if (isQualifiedToday) {
            maxOf(rawCurrentStreakFromEntity, consecutiveDays, 1)
        } else {
            maxOf(rawCurrentStreakFromEntity, consecutiveDays)
        }
        val longestStreak = maxOf(streakEntity?.longestStreak ?: 0, effectiveCurrentStreak)

        val todayStatus = when {
            isQualifiedToday -> TodayStreakStatus.COMPLETED
            effectiveCurrentStreak > 0 -> TodayStreakStatus.PENDING
            else -> TodayStreakStatus.NOT_STARTED
        }

        val recentDays = (6 downTo 0).map { offset ->
            val date = todayDate.minusDays(offset.toLong())
            val dateFormatted = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
            StreakDayActivity(
                date = dateFormatted,
                dayOfWeek = dayName,
                dayOfMonth = date.dayOfMonth,
                isQualified = qualifyingDateSet.contains(dateFormatted),
                isToday = (offset == 0)
            )
        }

        if (healDatabase && isQualifiedToday && (streakEntity?.lastQualifyingWatchDate != todayStr || todayWatch == null || todayWatch.qualifyingVideoCount <= 0)) {
            triggerStreakSelfHealing(
                todayStr = todayStr,
                effectiveCurrentStreak = effectiveCurrentStreak,
                longestStreak = longestStreak,
                streakEntity = streakEntity,
                todayWatch = todayWatch,
                qualifyingUri = todayQualifyingUri
            )
        }

        return StreakUiState(
            currentStreak = effectiveCurrentStreak,
            longestStreak = longestStreak,
            lastQualifyingDate = if (isQualifiedToday) todayStr else (if (consecutiveDays > 0) todayDate.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) else lastDateStr?.takeIf { it.isNotBlank() }),
            isQualifiedToday = isQualifiedToday,
            todayStatus = todayStatus,
            recentDays = recentDays
        )
    }

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    fun removeFromHistory(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            watchHistoryRepository.deleteHistoryPermanently(uri)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            watchHistoryDao.deleteAll()
        }
    }
}

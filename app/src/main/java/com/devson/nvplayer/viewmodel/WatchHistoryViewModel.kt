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
            val repo = WatchHistoryRepository(appContext)
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
        dailyWatchesFlow
    ) { streakEntity, dailyWatches ->
        calculateStreakUiState(streakEntity, dailyWatches)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StreakUiState()
    )

    fun calculateStreakUiState(
        streakEntity: StreakStateEntity?,
        dailyWatches: List<DailyWatchEntity>
    ): StreakUiState {
        val todayStr = dateProvider()
        val todayDate = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }

        val todayWatch = dailyWatches.find { it.date == todayStr }
        val isQualifiedToday = (todayWatch != null && todayWatch.qualifyingVideoCount > 0) ||
                (streakEntity?.lastQualifyingWatchDate == todayStr)

        val rawCurrent = streakEntity?.currentStreak ?: 0
        val lastDateStr = streakEntity?.lastQualifyingWatchDate

        val effectiveCurrentStreak = if (rawCurrent <= 0 || lastDateStr.isNullOrBlank()) {
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

        val longestStreak = maxOf(streakEntity?.longestStreak ?: 0, effectiveCurrentStreak)

        val todayStatus = when {
            isQualifiedToday -> TodayStreakStatus.COMPLETED
            effectiveCurrentStreak > 0 -> TodayStreakStatus.PENDING
            else -> TodayStreakStatus.NOT_STARTED
        }

        val qualifyingDateSet = HashSet<String>()
        for (watch in dailyWatches) {
            if (watch.qualifyingVideoCount > 0) {
                qualifyingDateSet.add(watch.date)
            }
        }
        if (isQualifiedToday) {
            qualifyingDateSet.add(todayStr)
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

        return StreakUiState(
            currentStreak = effectiveCurrentStreak,
            longestStreak = longestStreak,
            lastQualifyingDate = lastDateStr,
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

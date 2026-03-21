package com.devson.nosvedplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.devson.nosvedplayer.utility.formatLogTime

data class LogEntry(
    val timestamp: Long,
    val message: String
) {
    val formattedTime: String
        get() = formatLogTime(timestamp)
}

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(message: String) {
        val entry = LogEntry(System.currentTimeMillis(), message)
        _logs.value = listOf(entry) + _logs.value // Prepend latest log
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

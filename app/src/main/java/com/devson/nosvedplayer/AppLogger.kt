package com.devson.nosvedplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val message: String
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
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

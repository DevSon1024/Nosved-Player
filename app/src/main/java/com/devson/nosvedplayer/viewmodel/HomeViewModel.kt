package com.devson.nosvedplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.repository.WatchHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepo = WatchHistoryRepository(application.applicationContext)

    /** History list sorted by most recently played, limited to 50 entries. */
    val history: StateFlow<List<WatchHistory>> = historyRepo.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Delete a single history entry by URI. */
    fun deleteHistoryItem(uri: String) {
        viewModelScope.launch { historyRepo.delete(uri) }
    }

    /** Wipe entire history. */
    fun clearAllHistory() {
        viewModelScope.launch { historyRepo.clearAll() }
    }
}

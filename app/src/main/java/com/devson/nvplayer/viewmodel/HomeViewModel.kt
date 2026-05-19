package com.devson.nvplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.model.FolderItem
import com.devson.nvplayer.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: VideoRepository) : ViewModel() {
    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            val fetchedFolders = repository.getFolders()
            _folders.value = fetchedFolders
            _isLoading.value = false
        }
    }
}

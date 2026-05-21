package com.devson.nvplayer.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.devson.nvplayer.player.PlayerEngine
import com.devson.nvplayer.player.PlayerState
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing the media playback state and bridging it to Compose UI.
 */
class PlayerViewModel(
    application: Application,
    private val playerEngine: PlayerEngine
) : AndroidViewModel(application) {

    // Expose flows from the MPV player engine
    val isPlaying: StateFlow<Boolean> = playerEngine.isPlaying
    val currentPosition: StateFlow<Long> = playerEngine.currentPosition
    val duration: StateFlow<Long> = playerEngine.duration
    val playbackState: StateFlow<PlayerState> = playerEngine.playbackState
    val videoWidth: StateFlow<Long> = playerEngine.videoWidth
    val videoHeight: StateFlow<Long> = playerEngine.videoHeight
    val videoRotation: StateFlow<Long> = playerEngine.videoRotation

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _playlist = MutableStateFlow<List<Uri>>(emptyList())
    val playlist: StateFlow<List<Uri>> = _playlist.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val playerPrefs by lazy {
        getApplication<Application>().getSharedPreferences("player_settings_prefs", Context.MODE_PRIVATE)
    }

    private val _savedBrightness = MutableStateFlow(0.5f)
    val savedBrightness: StateFlow<Float> = _savedBrightness.asStateFlow()

    private val _savedVolume = MutableStateFlow(-1)
    val savedVolume: StateFlow<Int> = _savedVolume.asStateFlow()

    init {
        _savedBrightness.value = playerPrefs.getFloat("brightness", 0.5f)
        _savedVolume.value = playerPrefs.getInt("volume", -1)
        viewModelScope.launch {
            playbackState.collect { state ->
                if (state is PlayerState.Playing && !isPositionRestored) {
                    restorePlaybackProgress()
                }
            }
        }
    }

    private fun restorePlaybackProgress() {
        val uri = _currentUri.value ?: return
        val prefs = getApplication<Application>().getSharedPreferences("watch_history_prefs", Context.MODE_PRIVATE)
        val savedPos = prefs.getLong(uri.toString(), 0L)
        if (savedPos > 0) {
            Log.d("PlayerViewModel", "Restoring saved progress: $savedPos for URI: $uri")
            seekTo(savedPos)
        }
        isPositionRestored = true
    }

    fun saveBrightness(brightness: Float) {
        playerPrefs.edit().putFloat("brightness", brightness).apply()
        _savedBrightness.value = brightness
    }

    fun saveVolume(volume: Int) {
        playerPrefs.edit().putInt("volume", volume).apply()
        _savedVolume.value = volume
    }

    private var isVideoLoaded = false
    private var isPositionRestored = false

    private fun updateNavigationStates() {
        val current = _currentUri.value
        val list = _playlist.value
        if (current == null || list.isEmpty()) {
            _hasNext.value = false
            _hasPrevious.value = false
            return
        }
        val index = list.indexOfFirst { it.toString() == current.toString() }
        if (index == -1) {
            _hasNext.value = false
            _hasPrevious.value = false
        } else {
            _hasNext.value = index < list.size - 1
            _hasPrevious.value = index > 0
        }
    }

    /**
     * Prepares media Uri and SAF permissions in advance before loading file in the native engine.
     */
    fun prepareVideo(uri: Uri, playlistUris: List<Uri> = emptyList()) {
        Log.d("PlayerViewModel", "Preparing video URI: $uri, playlist size: ${playlistUris.size}")
        _currentUri.value = uri
        _playlist.value = playlistUris
        updateNavigationStates()
        isVideoLoaded = false
        isPositionRestored = false

        // Save progress as 0 if not already present, and update timestamp
        val prefs = getApplication<Application>().getSharedPreferences("watch_history_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains(uri.toString())) {
            prefs.edit().putLong(uri.toString(), 0L).apply()
        }
        val timePrefs = getApplication<Application>().getSharedPreferences("watch_history_timestamps_prefs", Context.MODE_PRIVATE)
        timePrefs.edit().putLong(uri.toString(), System.currentTimeMillis()).apply()

        try {
            val contentResolver = getApplication<Application>().contentResolver
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.d("PlayerViewModel", "Successfully persisted URI read permission")
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Could not persist URI read permission: ${e.localizedMessage}")
        }
    }

    /**
     * Triggers active native video file loading once rendering surface is created and attached.
     */
    fun loadVideoIfNeeded() {
        val uri = _currentUri.value
        if (uri != null && !isVideoLoaded) {
            Log.d("PlayerViewModel", "Loading prepared video into engine: $uri")
            playerEngine.loadVideo(uri)
            isVideoLoaded = true
        }
    }

    /**
     * Persists URI read permission for Storage Access Framework and plays the file directly.
     */
    fun loadVideo(uri: Uri, playlistUris: List<Uri> = emptyList()) {
        prepareVideo(uri, playlistUris)
        loadVideoIfNeeded()
    }

    fun playNext() {
        val current = _currentUri.value ?: return
        val list = _playlist.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.toString() == current.toString() }
        if (index != -1 && index < list.size - 1) {
            val nextUri = list[index + 1]
            changeVideo(nextUri)
        }
    }

    fun playPrevious() {
        val current = _currentUri.value ?: return
        val list = _playlist.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.toString() == current.toString() }
        if (index > 0) {
            val prevUri = list[index - 1]
            changeVideo(prevUri)
        }
    }

    private fun changeVideo(uri: Uri) {
        savePlaybackProgress()
        Log.d("PlayerViewModel", "Changing video to: $uri")
        _currentUri.value = uri
        isVideoLoaded = false
        isPositionRestored = false

        // Save progress as 0 if not already present, and update timestamp
        val prefs = getApplication<Application>().getSharedPreferences("watch_history_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains(uri.toString())) {
            prefs.edit().putLong(uri.toString(), 0L).apply()
        }
        val timePrefs = getApplication<Application>().getSharedPreferences("watch_history_timestamps_prefs", Context.MODE_PRIVATE)
        timePrefs.edit().putLong(uri.toString(), System.currentTimeMillis()).apply()

        try {
            val contentResolver = getApplication<Application>().contentResolver
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Could not persist URI permission: ${e.localizedMessage}")
        }
        loadVideoIfNeeded()
        updateNavigationStates()
    }

    fun play() {
        playerEngine.play()
    }

    fun savePlaybackProgress() {
        val uri = _currentUri.value ?: return
        val pos = currentPosition.value
        if (pos > 0) {
            val prefs = getApplication<Application>().getSharedPreferences("watch_history_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong(uri.toString(), pos).apply()
            val timePrefs = getApplication<Application>().getSharedPreferences("watch_history_timestamps_prefs", Context.MODE_PRIVATE)
            timePrefs.edit().putLong(uri.toString(), System.currentTimeMillis()).apply()
            Log.d("PlayerViewModel", "Saved progress: $pos for URI: $uri")
        }
    }

    fun pause() {
        playerEngine.pause()
        savePlaybackProgress()
    }

    fun togglePlayback() {
        playerEngine.togglePlayback()
    }

    fun seekTo(position: Long) {
        playerEngine.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playerEngine.setPlaybackSpeed(speed)
    }

    fun cycleSubtitle() {
        playerEngine.cycleSubtitle()
    }

    fun cycleAudio() {
        playerEngine.cycleAudio()
    }

    override fun onCleared() {
        savePlaybackProgress()
        super.onCleared()
        Log.d("PlayerViewModel", "PlayerViewModel cleared, releasing resources")
        playerEngine.release()
    }

    /**
     * Custom Factory to instantiate PlayerViewModel without dependency injection frameworks (Hilt).
     */
    class Factory(
        private val application: Application,
        private val playerEngine: PlayerEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
                return PlayerViewModel(application, playerEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

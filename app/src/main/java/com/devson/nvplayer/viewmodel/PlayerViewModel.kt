package com.devson.nvplayer.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.devson.nvplayer.player.PlayerEngine
import com.devson.nvplayer.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var isVideoLoaded = false

    /**
     * Prepares media Uri and SAF permissions in advance before loading file in the native engine.
     */
    fun prepareVideo(uri: Uri) {
        Log.d("PlayerViewModel", "Preparing video URI: $uri")
        _currentUri.value = uri
        isVideoLoaded = false
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
    fun loadVideo(uri: Uri) {
        prepareVideo(uri)
        loadVideoIfNeeded()
    }

    fun play() {
        playerEngine.play()
    }

    fun pause() {
        playerEngine.pause()
    }

    fun togglePlayback() {
        playerEngine.togglePlayback()
    }

    fun seekTo(position: Long) {
        playerEngine.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerEngine.setPlaybackSpeed(speed)
    }

    fun cycleSubtitle() {
        playerEngine.cycleSubtitle()
    }

    fun cycleAudio() {
        playerEngine.cycleAudio()
    }

    override fun onCleared() {
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

package com.devson.nosvedplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.player.PlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoViewModel : ViewModel() {

    private var playerManager: PlayerManager? = null

    val isPlaying get() = playerManager?.isPlaying
    val currentPosition get() = playerManager?.currentPosition
    val duration get() = playerManager?.duration
    val bufferedPosition get() = playerManager?.bufferedPosition

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    init {
        startProgressUpdate()
    }

    fun initializePlayer(context: Context) {
        if (playerManager == null) {
            playerManager = PlayerManager(context)
            playerManager?.initializePlayer()
            
            // Load a sample video if not already playing
            if (_currentVideo.value == null) {
                val sampleVideo = Video(
                    uri = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
                    title = "Big Buck Bunny"
                )
                playVideo(sampleVideo)
            }
        }
    }

    fun getPlayer() = playerManager?.exoPlayer

    fun playVideo(video: Video) {
        _currentVideo.value = video
        playerManager?.playVideo(video)
        hideControlsWithDelay()
    }

    fun togglePlayPause() {
        playerManager?.playPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager?.seekTo(positionMs)
    }

    fun seekForward() {
        playerManager?.seekForward()
    }

    fun seekBackward() {
        playerManager?.seekBackward()
    }

    fun toggleControlsVisibility() {
        _controlsVisible.value = !_controlsVisible.value
        if (_controlsVisible.value && playerManager?.isPlaying?.value == true) {
            hideControlsWithDelay()
        }
    }

    fun showControlsAndDelayHide() {
        _controlsVisible.value = true
        if (playerManager?.isPlaying?.value == true) {
            hideControlsWithDelay()
        }
    }

    private fun hideControlsWithDelay() {
        viewModelScope.launch {
            delay(3000) // Hide after 3 seconds
            _controlsVisible.value = false
        }
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                if (playerManager?.isPlaying?.value == true) {
                    playerManager?.updateProgress()
                }
                delay(1000) // Update every second
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager?.releasePlayer()
    }
}

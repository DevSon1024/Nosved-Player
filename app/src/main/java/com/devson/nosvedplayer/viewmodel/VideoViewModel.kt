package com.devson.nosvedplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.player.PlayerManager
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoViewModel : ViewModel() {

    private var playerManager: PlayerManager? = null
    private var pendingVideo: Video? = null

    // Exposed so that VideoScreen recomposes when the player becomes ready
    private val _playerInstance = MutableStateFlow<ExoPlayer?>(null)
    val playerInstance: StateFlow<ExoPlayer?> = _playerInstance.asStateFlow()

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

            // Publish the player instance so VideoScreen recomposes and attaches the surface
            _playerInstance.value = playerManager?.exoPlayer

            // Play the video the user selected (stored before the player was ready)
            pendingVideo?.let { video ->
                playerManager?.playVideo(video)
                hideControlsWithDelay()
                pendingVideo = null
            }
        }
    }

    fun getPlayer() = _playerInstance.value

    fun playVideo(video: Video) {
        _currentVideo.value = video
        if (playerManager != null) {
            playerManager?.playVideo(video)
            hideControlsWithDelay()
        } else {
            // Player not ready yet — queue the video to be played in initializePlayer()
            pendingVideo = video
        }
    }

    fun togglePlayPause() {
        playerManager?.playPause()
    }

    /** Always pauses — used when leaving the screen or app goes to background. */
    fun pauseVideo() {
        playerManager?.pause()
    }

    /** Resumes only if there is an active video (prevents auto-play on cold start). */
    fun resumeVideo() {
        if (_currentVideo.value != null) {
            playerManager?.resume()
        }
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

package com.devson.nosvedplayer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.player.PlayerManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
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
    val playerError get() = playerManager?.playerError

    // null = not yet known, true = portrait, false = landscape
    val isPortraitVideo get() = playerManager?.isPortraitVideo
    val videoFps get() = playerManager?.videoFps

    private val _controlsVisible = MutableStateFlow(false)  // hidden until first tap
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _showStats = MutableStateFlow(false)
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

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
                _controlsVisible.value = false
                pendingVideo = null
            }
        }
    }

    fun getPlayer() = _playerInstance.value

    fun playVideo(video: Video) {
        _currentVideo.value = video
        if (playerManager != null) {
            playerManager?.playVideo(video)
            _controlsVisible.value = false
        } else {
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

    fun toggleStats() {
        _showStats.value = !_showStats.value
    }

    fun clearError() {
        playerManager?.clearError()
    }

    fun toggleResizeMode(): String {
        val newMode = when (_resizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        _resizeMode.value = newMode
        return when (newMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit (100%)"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Wide (Stretch)"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom (Cropped)"
            else -> "Original"
        }
    }

    private var hideJob: kotlinx.coroutines.Job? = null

    fun toggleControlsVisibility() {
        if (_controlsVisible.value) {
            // Already visible — hide immediately
            hideJob?.cancel()
            _controlsVisible.value = false
        } else {
            // Show and schedule auto-hide
            _controlsVisible.value = true
            scheduleHide()
        }
    }

    fun showControlsAndDelayHide() {
        _controlsVisible.value = true
        scheduleHide()
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(3000)
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

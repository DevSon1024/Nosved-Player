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

//  Playback Settings Enums 

/** Seek bar visual style: Material3 thumb slider or a flat thin line. */
enum class SeekBarStyle { DEFAULT, FLAT }

/** Control icon size preset applied to play/pause and seek icons. */
enum class ControlIconSize { SMALL, MEDIUM, LARGE }

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

    //  Playback Settings State 
    
    private var settingsRepository: com.devson.nosvedplayer.repository.PlaybackSettingsRepository? = null

    /** Seconds to jump on seek forward/backward gestures or button taps. */
    private val _seekDurationSeconds = MutableStateFlow(10)
    val seekDurationSeconds: StateFlow<Int> = _seekDurationSeconds.asStateFlow()

    /** Visual style of the seek bar. */
    private val _seekBarStyle = MutableStateFlow(SeekBarStyle.DEFAULT)
    val seekBarStyle: StateFlow<SeekBarStyle> = _seekBarStyle.asStateFlow()

    /** Size preset for the playback control icons. */
    private val _controlIconSize = MutableStateFlow(ControlIconSize.MEDIUM)
    val controlIconSize: StateFlow<ControlIconSize> = _controlIconSize.asStateFlow()

    init {
        startProgressUpdate()
    }

    fun initializePlayer(context: Context) {
        if (settingsRepository == null) {
            settingsRepository = com.devson.nosvedplayer.repository.PlaybackSettingsRepository(context.applicationContext)
            viewModelScope.launch {
                settingsRepository?.playbackSettingsFlow?.collect { settings ->
                    _seekDurationSeconds.value = settings.seekDurationSeconds
                    _seekBarStyle.value = try { SeekBarStyle.valueOf(settings.seekBarStyle) } catch (e: Exception) { SeekBarStyle.DEFAULT }
                    _controlIconSize.value = try { ControlIconSize.valueOf(settings.controlIconSize) } catch (e: Exception) { ControlIconSize.MEDIUM }
                }
            }
        }
        
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
        playerManager?.seekForward(_seekDurationSeconds.value * 1000L)
    }

    fun seekBackward() {
        playerManager?.seekBackward(_seekDurationSeconds.value * 1000L)
    }

    fun seekByOffset(offsetMs: Long) {
        val player = playerManager ?: return
        val current = player.currentPosition.value
        val total = player.duration.value
        val newPos = (current + offsetMs).coerceIn(0L, total)
        player.seekTo(newPos)
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

    //  Playback Settings Setters 

    fun setSeekDuration(seconds: Int) { 
        viewModelScope.launch { settingsRepository?.updateSeekDuration(seconds) }
    }
    fun setSeekBarStyle(style: SeekBarStyle) { 
        viewModelScope.launch { settingsRepository?.updateSeekBarStyle(style.name) }
    }
    fun setControlIconSize(size: ControlIconSize) { 
        viewModelScope.launch { settingsRepository?.updateControlIconSize(size.name) }
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

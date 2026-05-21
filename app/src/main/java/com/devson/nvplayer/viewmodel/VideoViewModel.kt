package com.devson.nvplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.player.PlayerManager
import com.devson.nvplayer.repository.WatchHistoryRepository
import com.devson.nvplayer.repository.DecoderMode
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch

//  Playback Settings Enums 

/** Seek bar visual style: Material3 thumb slider or a flat thin line. */
enum class SeekBarStyle { DEFAULT, FLAT }

/** Control icon size preset applied to play/pause and seek icons. */
enum class ControlIconSize { SMALL, MEDIUM, LARGE }

@UnstableApi
class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepo = WatchHistoryRepository(application.applicationContext)
    private var playerManager: PlayerManager? = null
    private var pendingVideo: Video? = null
    private var pendingPlaylist: List<Video> = emptyList()
    private var pendingResumeMs: Long = 0L

    // Exposed so that VideoScreen recomposes when the player becomes ready
    private val _playerInstance = MutableStateFlow<ExoPlayer?>(null)
    val playerInstance: StateFlow<ExoPlayer?> = _playerInstance.asStateFlow()

    // Add this field
    private val _playingIntent = MutableStateFlow(true)
    val playingIntent: StateFlow<Boolean> = _playingIntent.asStateFlow()

    val isPlaying get() = playerManager?.isPlaying
    val currentPosition get() = playerManager?.currentPosition
    val duration get() = playerManager?.duration
    val bufferedPosition get() = playerManager?.bufferedPosition
    val playerError get() = playerManager?.playerError

    // null = not yet known, true = portrait, false = landscape
    val isPortraitVideo get() = playerManager?.isPortraitVideo
    val videoFps get() = playerManager?.videoFps
    val videoDecoderName get() = playerManager?.videoDecoderName

    // --- Audio & Subtitle Tracks from Manager ---
    val audioTracks get() = playerManager?.audioTracks
    val selectedAudioIndex get() = playerManager?.selectedAudioIndex
    val subtitleTracks get() = playerManager?.subtitleTracks
    val selectedSubtitleIndex get() = playerManager?.selectedSubtitleIndex

    val isAudioBoostEnabled get() = playerManager?.isAudioBoostEnabled

    // --- Seek Preview State (MX-style 3-phase seek) ---
    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    private val _seekPreviewPosition = MutableStateFlow(0L)
    val seekPreviewPosition: StateFlow<Long> = _seekPreviewPosition.asStateFlow()

    fun toggleAudioBoost(enabled: Boolean) {
        playerManager?.toggleAudioBoost(enabled)
    }

    private val _controlsVisible = MutableStateFlow(false)  // hidden until first tap
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _showStats = MutableStateFlow(false)
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<Video>>(emptyList())
    val currentPlaylist: StateFlow<List<Video>> = _currentPlaylist.asStateFlow()
    
    private val _currentPlaylistIndex = MutableStateFlow(-1)
    val currentPlaylistIndex: StateFlow<Int> = _currentPlaylistIndex.asStateFlow()

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    //  Playback Settings State 
    
    private var settingsRepository: com.devson.nvplayer.repository.PlaybackSettingsRepository? = null

    /** Seconds to jump on seek forward/backward gestures or button taps. */
    private val _seekDurationSeconds = MutableStateFlow(10)
    val seekDurationSeconds: StateFlow<Int> = _seekDurationSeconds.asStateFlow()

    /** Visual style of the seek bar. */
    private val _seekBarStyle = MutableStateFlow(SeekBarStyle.DEFAULT)
    val seekBarStyle: StateFlow<SeekBarStyle> = _seekBarStyle.asStateFlow()

    /** Size preset for the playback control icons. */
    private val _controlIconSize = MutableStateFlow(ControlIconSize.MEDIUM)
    val controlIconSize: StateFlow<ControlIconSize> = _controlIconSize.asStateFlow()

    private val _autoPlayEnabled = MutableStateFlow(false)
    val autoPlayEnabled: StateFlow<Boolean> = _autoPlayEnabled.asStateFlow()

    private val _showSeekButtons = MutableStateFlow(true)
    val showSeekButtons: StateFlow<Boolean> = _showSeekButtons.asStateFlow()

    private val _fastplaySpeed = MutableStateFlow(2.0f)
    val fastplaySpeed: StateFlow<Float> = _fastplaySpeed.asStateFlow()

    private val _currentPlaybackSpeed = MutableStateFlow(1.0f)
    val currentPlaybackSpeed: StateFlow<Float> = _currentPlaybackSpeed.asStateFlow()

    // --- Subtitle Customization State ---
    
    private val _subtitleTextSizeScale = MutableStateFlow(1f)
    val subtitleTextSizeScale: StateFlow<Float> = _subtitleTextSizeScale.asStateFlow()

    // 0 = None, 1 = Semi-transparent, 2 = Opaque
    private val _subtitleBgStyle = MutableStateFlow(0)
    val subtitleBgStyle: StateFlow<Int> = _subtitleBgStyle.asStateFlow()

    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs.asStateFlow()

    private val _subtitleVerticalOffset = MutableStateFlow(0f)
    val subtitleVerticalOffset: StateFlow<Float> = _subtitleVerticalOffset.asStateFlow()

    private val _currentDecoderMode = MutableStateFlow(DecoderMode.HW_PLUS)
    val currentDecoderMode: StateFlow<DecoderMode> = _currentDecoderMode.asStateFlow()

    private val _defaultAudioLang = MutableStateFlow("")
    private val _defaultSubtitleLang = MutableStateFlow("")

    init {
        startProgressUpdate()
    }

    fun initializePlayer(context: android.content.Context, playbackSettings: com.devson.nvplayer.repository.PlaybackSettings? = null) {
        if (settingsRepository == null) {
            settingsRepository = com.devson.nvplayer.repository.PlaybackSettingsRepository(context.applicationContext)
            viewModelScope.launch {
                settingsRepository?.playbackSettingsFlow?.collect { settings ->
                    _seekDurationSeconds.value = settings.seekDurationSeconds
                    _seekBarStyle.value = try { SeekBarStyle.valueOf(settings.seekBarStyle) } catch (e: Exception) { SeekBarStyle.DEFAULT }
                    _controlIconSize.value = try { ControlIconSize.valueOf(settings.controlIconSize) } catch (e: Exception) { ControlIconSize.MEDIUM }
                    _autoPlayEnabled.value = settings.autoPlayEnabled
                    _showSeekButtons.value = settings.showSeekButtons
                    _fastplaySpeed.value = settings.fastplaySpeed
                    _currentDecoderMode.value = settings.decoderMode
                    _defaultAudioLang.value = settings.defaultAudioLanguage
                    _defaultSubtitleLang.value = settings.defaultSubtitleLanguage
                }
            }
            viewModelScope.launch {
                _defaultAudioLang.collect { audio ->
                    playerManager?.updatePreferredLanguages(audio, _defaultSubtitleLang.value)
                }
            }
            viewModelScope.launch {
                _defaultSubtitleLang.collect { sub ->
                    playerManager?.updatePreferredLanguages(_defaultAudioLang.value, sub)
                }
            }
        }

        if (playerManager == null) {
            playerManager = PlayerManager(context)
            playerManager?.initializePlayer(
                _currentDecoderMode.value,
                _defaultAudioLang.value,
                _defaultSubtitleLang.value,
                playbackSettings
            )
            _playerInstance.value = playerManager?.exoPlayer

            playerManager?.onPlayNext = {
                playNextVideo()
            }
            playerManager?.onPlayPrevious = {
                playPreviousVideo()
            }

            playerManager?.onVideoEnded = {
                val uri = _currentVideo.value?.uri
                val pos = resolvePositionToSave()
                if (uri != null && pos > 0L) {
                    viewModelScope.launch { historyRepo.savePosition(uri, pos) }
                }
                if (_autoPlayEnabled.value) {
                    playNextVideo()
                }
            }

            playerManager?.onPlaybackError = {
                playNextVideo()
            }

            pendingVideo?.let { video ->
                val resumeMs = pendingResumeMs
                val playlist = pendingPlaylist

                _currentPlaylist.value = playlist
                _currentPlaylistIndex.value = playlist.indexOfFirst { it.uri == video.uri }

                playerManager?.playVideo(video)
                val dur = video.duration
                val startPos = if (dur > 0 && resumeMs >= dur * 0.95f) 0L else resumeMs
                if (startPos > 0L) playerManager?.seekTo(startPos)
                _controlsVisible.value = false
                pendingVideo = null
                pendingPlaylist = emptyList()
                pendingResumeMs = 0L
            }
        }
    }

    fun toggleDecoderMode() {
        val next = when (_currentDecoderMode.value) {
            DecoderMode.HW      -> DecoderMode.HW_PLUS
            DecoderMode.HW_PLUS -> DecoderMode.SW
            DecoderMode.SW      -> DecoderMode.HW
        }
        setDecoderMode(next)
    }

    fun setDecoderMode(mode: DecoderMode) {
        if (_currentDecoderMode.value == mode) return
        viewModelScope.launch {
            settingsRepository?.updateDecoderMode(mode)
            _currentDecoderMode.value = mode
            val video = _currentVideo.value ?: return@launch
            val (savedPos, wasPlaying) = playerManager?.releasePlayerAndKeepState() ?: return@launch
            _playerInstance.value = null
            playerManager = PlayerManager(getApplication())
            playerManager?.initializePlayer(
                mode,
                _defaultAudioLang.value,
                _defaultSubtitleLang.value
            )
            _playerInstance.value = playerManager?.exoPlayer
            playerManager?.onPlayNext = { playNextVideo() }
            playerManager?.onPlayPrevious = { playPreviousVideo() }
            playerManager?.onVideoEnded = {
                val uri = _currentVideo.value?.uri
                val pos = resolvePositionToSave()
                if (uri != null && pos > 0L) {
                    viewModelScope.launch { historyRepo.savePosition(uri, pos) }
                }
                if (_autoPlayEnabled.value) playNextVideo()
            }
            playerManager?.onPlaybackError = { playNextVideo() }
            playerManager?.playVideo(video)
            if (savedPos > 0L) playerManager?.seekTo(savedPos)
            if (!wasPlaying) playerManager?.pause()
        }
    }


    fun getPlayer() = _playerInstance.value

    fun playVideo(video: Video, playlist: List<Video> = emptyList(), resumePositionMs: Long = 0L) {
        _currentVideo.value = video

        val actualPlaylist = if (playlist.isEmpty()) listOf(video) else playlist
        _currentPlaylist.value = actualPlaylist
        _currentPlaylistIndex.value = actualPlaylist.indexOfFirst { it.uri == video.uri }

        viewModelScope.launch { historyRepo.recordPlay(video) }
        if (playerManager != null) {
            playerManager?.playVideo(video)
            val dur = video.duration
            val startPos = if (dur > 0 && resumePositionMs >= dur * 0.95f) 0L else resumePositionMs
            if (startPos > 0L) playerManager?.seekTo(startPos)
            _controlsVisible.value = false
        } else {
            pendingVideo = video
            pendingPlaylist = actualPlaylist
            pendingResumeMs = resumePositionMs
        }
    }

    fun playNextVideo() {
        val playlist = _currentPlaylist.value
        val currentIndex = _currentPlaylistIndex.value
        if (playlist.isNotEmpty() && currentIndex in 0 until playlist.lastIndex) {
            val nextVideo = playlist[currentIndex + 1]
            playVideo(nextVideo, playlist, 0L)
        }
    }

    fun playPreviousVideo() {
        val playlist = _currentPlaylist.value
        val currentIndex = _currentPlaylistIndex.value
        if (playlist.isNotEmpty() && currentIndex > 0) {
            val prevVideo = playlist[currentIndex - 1]
            playVideo(prevVideo, playlist, 0L)
        }
    }

    fun togglePlayPause() {
        val willPlay = playerManager?.exoPlayer?.isPlaying == false
        _playingIntent.value = willPlay
        playerManager?.playPause()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4.0f)
        _currentPlaybackSpeed.value = clamped
        playerManager?.setPlaybackSpeed(clamped)
    }

    fun updateVideoFilters(settings: com.devson.nvplayer.repository.PlaybackSettings) {
        playerManager?.updateVideoFilters(settings)
    }

    private fun resolvePositionToSave(): Long {
        val player = playerManager?.exoPlayer ?: return 0L
        val dur = player.duration.coerceAtLeast(0L)
        return if (player.playbackState == Player.STATE_ENDED && dur > 0L) dur
        else player.currentPosition.coerceAtLeast(0L)
    }

    /** Always pauses - used when leaving the screen or app goes to background. */
   fun pauseVideo() {
        _playingIntent.value = false
        val uri = _currentVideo.value?.uri
        val pos = resolvePositionToSave()
        if (uri != null && pos > 0L) {
            viewModelScope.launch { historyRepo.savePosition(uri, pos) }
        }
        playerManager?.pause()
    }

    fun stopVideo() {
        val uri = _currentVideo.value?.uri
        val pos = resolvePositionToSave()
        if (uri != null && pos > 0L) {
            viewModelScope.launch { historyRepo.savePosition(uri, pos) }
        }
        playerManager?.exoPlayer?.stop()
        playerManager?.exoPlayer?.clearMediaItems()
        _currentVideo.value = null
    }

    /** Resumes only if there is an active video (prevents auto-play on cold start). */
    fun resumeVideo() {
        if (_currentVideo.value != null) {
            _playingIntent.value = true
            playerManager?.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        seekJob?.cancel()
        accumulatedSeekTarget = null
        playerManager?.seekTo(positionMs)
    }

    fun beginSeek() {
        val player = playerManager?.exoPlayer ?: return
        _isSeeking.value = true
        _seekPreviewPosition.value = player.currentPosition.coerceAtLeast(0L)
        playerManager?.pause()
    }

    fun updateSeekPreview(positionMs: Long) {
        val duration = playerManager?.exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
        _seekPreviewPosition.value = positionMs.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
    }

    fun commitSeek() {
        val target = _seekPreviewPosition.value
        _isSeeking.value = false
        playerManager?.seekSmooth(target)
        if (_currentVideo.value != null) {
            playerManager?.resume()
        }
    }

    fun seekForward() {
        debounceSeekByOffset(_seekDurationSeconds.value * 1000L)
    }

    fun seekBackward() {
        debounceSeekByOffset(-(_seekDurationSeconds.value * 1000L))
    }

    fun seekByOffset(offsetMs: Long) {
        debounceSeekByOffset(offsetMs)
    }

    fun setScrubbingMode(enabled: Boolean) {
        playerManager?.setScrubbingMode(enabled)
    }

    private fun debounceSeekByOffset(offsetMs: Long) {
        val player = playerManager?.exoPlayer ?: return
        
        val startPos = accumulatedSeekTarget ?: player.currentPosition
        val totalDuration = player.duration.coerceAtLeast(0L)
        val newPos = (startPos + offsetMs).coerceIn(0L, totalDuration)
        
        accumulatedSeekTarget = newPos
        
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            delay(400)
            accumulatedSeekTarget?.let { target ->
                playerManager?.seekTo(target)
            }
            accumulatedSeekTarget = null
        }
    }

    fun toggleStats() {
        _showStats.value = !_showStats.value
    }

    fun setShowStats(show: Boolean) {
        _showStats.value = show
    }

    fun clearError() {
        playerManager?.clearError()
    }

    @OptIn(UnstableApi::class)
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
        _seekDurationSeconds.value = seconds
        viewModelScope.launch { settingsRepository?.updateSeekDuration(seconds) }
    }
    fun setSeekBarStyle(style: SeekBarStyle) { 
        _seekBarStyle.value = style
        viewModelScope.launch { settingsRepository?.updateSeekBarStyle(style.name) }
    }
    fun setControlIconSize(size: ControlIconSize) { 
        _controlIconSize.value = size
        viewModelScope.launch { settingsRepository?.updateControlIconSize(size.name) }
    }
    fun setAutoPlayEnabled(enabled: Boolean) {
        _autoPlayEnabled.value = enabled
        viewModelScope.launch { settingsRepository?.updateAutoPlayEnabled(enabled) }
    }
    fun setShowSeekButtons(show: Boolean) {
        _showSeekButtons.value = show
        viewModelScope.launch { settingsRepository?.updateShowSeekButtons(show) }
    }
    fun setFastplaySpeed(speed: Float) {
        _fastplaySpeed.value = speed
        viewModelScope.launch { settingsRepository?.updateFastplaySpeed(speed) }
    }

    // --- Audio & Subtitle Control Bridges ---

    fun selectAudioTrack(index: Int) {
        playerManager?.selectAudioTrack(index)
    }

    fun selectSubtitleTrack(index: Int?) {
        playerManager?.selectSubtitleTrack(index ?: -1)
    }

    fun loadExternalSubtitle(uri: Uri, mimeType: String, encodingLabel: String = "UTF-8") {
        playerManager?.loadExternalSubtitle(uri, mimeType, encodingLabel)
    }

    fun updateSubtitleTextSizeScale(scale: Float) {
        _subtitleTextSizeScale.value = scale
    }

    fun updateSubtitleBgStyle(style: Int) {
        _subtitleBgStyle.value = style
    }

    fun setSubtitleDelayMs(delayMs: Long) {
        _subtitleDelayMs.value = delayMs
    }

    fun setSubtitleVerticalOffset(offset: Float) {
        _subtitleVerticalOffset.value = offset
    }

    private var hideJob: kotlinx.coroutines.Job? = null
    private var seekJob: kotlinx.coroutines.Job? = null
    private var accumulatedSeekTarget: Long? = null

    fun toggleControlsVisibility() {
        if (_controlsVisible.value) {
            // Already visible - hide immediately
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
                if (!_isSeeking.value && playerManager?.isPlaying?.value == true) {
                    playerManager?.updateProgress()
                }
                delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager?.releasePlayer()
    }
}

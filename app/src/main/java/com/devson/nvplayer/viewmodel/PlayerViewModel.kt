package com.devson.nvplayer.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.provider.DocumentsContract
import android.os.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.devson.nvplayer.player.engine.PlayerEngine
import com.devson.nvplayer.player.engine.PlayerState
import com.devson.nvplayer.player.model.TrackInfo
import com.devson.nvplayer.player.model.ChapterInfo
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.player.ytdlp.YtdlpManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import com.devson.nvplayer.data.repository.EnhanceMode
import com.devson.nvplayer.data.repository.PlaybackSettings
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.database.WatchHistoryEntity
import com.devson.nvplayer.data.repository.PlaybackSettingsRepository
import com.devson.nvplayer.player.model.AspectMode

/**
 * ViewModel managing the media playback state and bridging it to Compose UI.
 */
@kotlin.OptIn(kotlinx.coroutines.FlowPreview::class)
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
    val currentSubtitleText: StateFlow<String> = playerEngine.currentSubtitleText
    val subtitleTracks: StateFlow<List<TrackInfo>> = playerEngine.subtitleTracks
    val audioTracks: StateFlow<List<TrackInfo>> = playerEngine.audioTracks
    val chapters: StateFlow<List<ChapterInfo>> = playerEngine.chapters
    val hwdecCurrent: StateFlow<String> = playerEngine.hwdecCurrent
    val bufferedPosition: StateFlow<Long> = playerEngine.bufferedPosition
    val mediaTitle: StateFlow<String> = playerEngine.mediaTitle

    val networkSpeedBytesPerSec = MutableStateFlow(0L)
    val bufferDurationSeconds = MutableStateFlow(0.0)
    val isNetworkStream = MutableStateFlow(false)

    private val _isHwSupported = MutableStateFlow(true)
    val isHwSupported: StateFlow<Boolean> = _isHwSupported.asStateFlow()

    // Tracks whether the HW decoder was confirmed active at least once for the current video.
    // Prevents false fallback detection from the initial "no" value or end-of-file teardown.
    private var hwdecEverActiveForCurrentVideo = false

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

    private val _isDynamicSpeedActive = MutableStateFlow(false)
    val isDynamicSpeedActive: StateFlow<Boolean> = _isDynamicSpeedActive.asStateFlow()

    fun setDynamicSpeedActive(active: Boolean) {
        _isDynamicSpeedActive.value = active
    }

    private val playerPrefs by lazy {
        getApplication<Application>().getSharedPreferences("player_settings_prefs", Context.MODE_PRIVATE)
    }

    private val _savedBrightness = MutableStateFlow(0.5f)
    val savedBrightness: StateFlow<Float> = _savedBrightness.asStateFlow()

    private val _savedVolume = MutableStateFlow(-1)
    val savedVolume: StateFlow<Int> = _savedVolume.asStateFlow()

    private val _audioBoosterEnabled = MutableStateFlow(false)
    val audioBoosterEnabled: StateFlow<Boolean> = _audioBoosterEnabled.asStateFlow()

    private val _audioBoostVolume = MutableStateFlow(100)
    val audioBoostVolume: StateFlow<Int> = _audioBoostVolume.asStateFlow()

    private val settingsRepo = PlaybackSettingsRepository(application.applicationContext)
    val playbackSettings = settingsRepo.playbackSettingsFlow

    init {
        _savedBrightness.value = playerPrefs.getFloat("brightness", 0.5f)
        _savedVolume.value = playerPrefs.getInt("volume", -1)
        _audioBoosterEnabled.value = playerPrefs.getBoolean("audio_booster_enabled", false)
        _audioBoostVolume.value = playerPrefs.getInt("audio_boost_volume", 200)

        // Observe mediaTitle changes to update the network stream history title in the database
        viewModelScope.launch {
            (mediaTitle as kotlinx.coroutines.flow.Flow<String>)
                .distinctUntilChanged()
                .debounce(500L)
                .collectLatest { title ->
                    val uri = _currentUri.value
                    if (uri != null && !title.isNullOrBlank() && isNetworkStream.value) {
                        withContext(Dispatchers.IO) {
                            val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
                            dao.insertOrUpdateStream(uri.toString(), title)
                        }
                    }
                }
        }
        viewModelScope.launch {
            playbackState.collect { state ->
                if (state is PlayerState.Playing) {
                    if (!isPositionRestored) {
                        restorePlaybackProgress()
                    }
                    if (!isExternalSubtitleLoaded) {
                        loadSavedExternalSubtitle()
                    }
                } else if (state is PlayerState.Ended) {
                    clearPlaybackProgress()
                }
            }
        }
        // Auto-play next video when current finishes, if setting is enabled
        viewModelScope.launch {
            playbackState.collect { state ->
                if (state is PlayerState.Ended && playbackSettings.value.autoPlayEnabled) {
                    playNext()
                }
            }
        }

        // Observe decoder and aspect mode changes from settings and apply immediately if video is active
        viewModelScope.launch {
            var lastDecoderMode: DecoderMode? = null
            var lastAspectMode: AspectMode? = null
            playbackSettings.collect { settings ->
                if (lastDecoderMode != settings.decoderMode) {
                    lastDecoderMode = settings.decoderMode
                    if (isVideoLoaded) {
                        Log.d("PlayerViewModel", "Applying decoder change immediately: ${settings.decoderMode}")
                        playerEngine.setDecoder(settings.decoderMode)
                    }
                }
                if (lastAspectMode != settings.aspectMode) {
                    lastAspectMode = settings.aspectMode
                    if (isVideoLoaded) {
                        Log.d("PlayerViewModel", "Applying aspect mode change immediately: ${settings.aspectMode}")
                        playerEngine.setAspectMode(settings.aspectMode)
                    }
                }
            }
        }

        // Coroutine 1: Detect RUNTIME fallback (HW was active, then dropped to SW mid-playback).
        // Uses collectLatest so any in-flight delay is cancelled when hwdecCurrent changes.
        // NOTE: This does NOT handle the "HW never became active" case - StateFlow won't
        // re-emit "no" if hwdecCurrent was already "no" before playback started.
        viewModelScope.launch {
            playerEngine.hwdecCurrent.collectLatest { actual ->
                val preferred = playbackSettings.value.decoderMode
                val preferredIsHw = preferred == DecoderMode.HW || preferred == DecoderMode.HW_PLUS || preferred == DecoderMode.AUTO

                if (preferredIsHw && actual != "no") {
                    // HW decoder confirmed active - mark it for this video session
                    if (!hwdecEverActiveForCurrentVideo) {
                        Log.d("PlayerViewModel", "HW decoder confirmed active: $actual")
                        hwdecEverActiveForCurrentVideo = true
                    }
                    _isHwSupported.value = true

                } else if (preferredIsHw && actual == "no" && hwdecEverActiveForCurrentVideo) {
                    // HW was confirmed active before, but just dropped to "no" - potential runtime fallback.
                    // Guard: only act if actively playing (not a teardown/close event).
                    val state = playerEngine.playbackState.value
                    if (!isVideoLoaded || state !is PlayerState.Playing) return@collectLatest

                    delay(1500L) // Short wait to let decoder settle

                    // Re-verify all conditions after delay
                    if (!isVideoLoaded) return@collectLatest
                    if (playerEngine.playbackState.value !is PlayerState.Playing) return@collectLatest
                    if (playerEngine.hwdecCurrent.value != "no") return@collectLatest // recovered
                    val prefNow = playbackSettings.value.decoderMode
                    val prefNowIsHw = prefNow == DecoderMode.HW || prefNow == DecoderMode.HW_PLUS || prefNow == DecoderMode.AUTO
                    if (!prefNowIsHw) return@collectLatest // user switched to SW manually

                    Log.w("PlayerViewModel", "Runtime HW→SW fallback detected after 1.5s confirmation.")
                    triggerSwFallback()
                }
                // If preferredIsHw && actual == "no" && !hwdecEverActiveForCurrentVideo:
                // This is handled by Coroutine 2 below (StateFlow won't re-emit here on video start).
            }
        }

        // Coroutine 2: Detect INITIAL failure (HW decoder never became active for this video).
        // Triggered by playbackState → Playing transition, not by hwdecCurrent changes.
        // This correctly handles videos where HW is unsupported because hwdecCurrent stays "no"
        // and never re-emits, so collectLatest would never fire again after the initial Loading check.
        viewModelScope.launch {
            var prevState: PlayerState = PlayerState.Idle
            playbackState.collect { state ->
                if (state is PlayerState.Playing && prevState !is PlayerState.Playing) {
                    // State just transitioned to Playing - spawn a one-shot check after stabilization
                    viewModelScope.launch {
                        delay(2500L) // Give HW decoder time to start up

                        if (!isVideoLoaded) return@launch
                        if (hwdecEverActiveForCurrentVideo) return@launch // HW already confirmed active, nothing to do
                        if (playerEngine.playbackState.value !is PlayerState.Playing) return@launch

                        val actual = playerEngine.hwdecCurrent.value
                        val preferred = playbackSettings.value.decoderMode
                        val preferredIsHw = preferred == DecoderMode.HW || preferred == DecoderMode.HW_PLUS || preferred == DecoderMode.AUTO

                        if (preferredIsHw && actual == "no") {
                            Log.w("PlayerViewModel", "HW decoder never became active after 2.5s of playback. Marking unsupported.")
                            triggerSwFallback()
                        }
                    }
                }
                prevState = state
            }
        }

        // Observe Smart Enhance settings and apply immediately if video is active
        viewModelScope.launch {
            var lastMode: EnhanceMode? = null
            var lastSat: Int? = null
            var lastCon: Int? = null
            var lastBright: Int? = null
            var lastGam: Int? = null
            var lastHue: Int? = null

            playbackSettings.collect { settings ->
                val changed = lastMode != settings.enhanceMode ||
                        lastSat != settings.enhanceSaturation ||
                        lastCon != settings.enhanceContrast ||
                        lastBright != settings.enhanceBrightness ||
                        lastGam != settings.enhanceGamma ||
                        lastHue != settings.enhanceHue

                if (changed) {
                    lastMode = settings.enhanceMode
                    lastSat = settings.enhanceSaturation
                    lastCon = settings.enhanceContrast
                    lastBright = settings.enhanceBrightness
                    lastGam = settings.enhanceGamma
                    lastHue = settings.enhanceHue

                    if (isVideoLoaded) {
                        applyEnhanceSettings(settings)
                    }
                }
            }
        }

        viewModelScope.launch {
            playerEngine.networkSpeedBytesPerSec.collect { speed ->
                networkSpeedBytesPerSec.value = speed
            }
        }
        viewModelScope.launch {
            playerEngine.bufferDurationSeconds.collect { buffer ->
                bufferDurationSeconds.value = buffer
            }
        }
    }

    private fun restorePlaybackProgress() {
        val uri = _currentUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
            val entity = dao.getHistory(uri.toString())
            val savedPos = entity?.lastPositionMs ?: 0L
            if (savedPos > 0) {
                Log.d("PlayerViewModel", "Restoring saved progress: $savedPos for URI: $uri")
                seekTo(savedPos)
            }
            isPositionRestored = true
        }
    }

    /**
     * Switches active decoder to SW and shows a user-facing toast.
     * Called when HW decoding is confirmed unavailable for the current video.
     * Must be called from a coroutine context.
     */
    private suspend fun triggerSwFallback() {
        val wasHwSupported = _isHwSupported.value
        _isHwSupported.value = false
        playerEngine.setDecoder(DecoderMode.SW)
        if (wasHwSupported) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    getApplication(),
                    "Hardware decoding is not supported for this video. Falling back to Software decoding.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun saveBrightness(brightness: Float) {
        playerPrefs.edit().putFloat("brightness", brightness).apply()
        _savedBrightness.value = brightness
    }

    fun saveVolume(volume: Int) {
        playerPrefs.edit().putInt("volume", volume).apply()
        _savedVolume.value = volume
    }

    fun toggleAudioBooster(enabled: Boolean) {
        _audioBoosterEnabled.value = enabled
        playerPrefs.edit().putBoolean("audio_booster_enabled", enabled).apply()
        if (enabled) {
            playerEngine.setMpvVolume(_audioBoostVolume.value.toDouble())
        } else {
            playerEngine.setMpvVolume(100.0)
        }
    }

    fun setAudioBoostVolume(volume: Int) {
        val clamped = volume.coerceIn(100, 200)
        _audioBoostVolume.value = clamped
        playerPrefs.edit().putInt("audio_boost_volume", clamped).apply()
        if (_audioBoosterEnabled.value) {
            playerEngine.setMpvVolume(clamped.toDouble())
        }
    }

    fun cycleAspectMode() {
        val current = playbackSettings.value.aspectMode
        val nextMode = current.next()
        viewModelScope.launch {
            settingsRepo.updateAspectMode(nextMode)
        }
    }

    private var isVideoLoaded = false
    private var isPositionRestored = false
    private var isExternalSubtitleLoaded = false

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
        isExternalSubtitleLoaded = false
        _isHwSupported.value = true
        hwdecEverActiveForCurrentVideo = false
        val scheme = uri.scheme
        isNetworkStream.value = scheme == "http" || scheme == "https"

        // Save progress as 0 if not already present, and update timestamp
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
            if (isNetworkStream.value) {
                dao.insertOrUpdateStream(uri.toString(), null)
            } else {
                val existing = dao.getHistory(uri.toString())
                if (existing == null) {
                    dao.insert(
                        WatchHistoryEntity(
                            uri = uri.toString(),
                            lastPositionMs = 0L,
                            lastPlayedAt = System.currentTimeMillis(),
                            isNetworkStream = false
                        )
                    )
                } else {
                    dao.insert(
                        WatchHistoryEntity(
                            uri = uri.toString(),
                            lastPositionMs = existing.lastPositionMs,
                            lastPlayedAt = System.currentTimeMillis(),
                            isNetworkStream = existing.isNetworkStream,
                            videoTitle = existing.videoTitle
                        )
                    )
                }
            }
        }

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
            
            // Set playback speed to customPlaybackSpeed preference value
            val customSpeed = settingsRepo.playbackSettingsFlow.value.customPlaybackSpeed
            setPlaybackSpeed(customSpeed)

            // Apply saved audio boost
            if (_audioBoosterEnabled.value) {
                playerEngine.setMpvVolume(_audioBoostVolume.value.toDouble())
            } else {
                playerEngine.setMpvVolume(100.0)
            }

            // Apply saved decoder setting
            playerEngine.setDecoder(playbackSettings.value.decoderMode)

            // Apply saved aspect mode
            playerEngine.setAspectMode(playbackSettings.value.aspectMode)

            // Apply smart enhance settings
            applyEnhanceSettings(playbackSettings.value)
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
            play()
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
            play()
        }
    }

    private fun changeVideo(uri: Uri) {
        savePlaybackProgress()
        Log.d("PlayerViewModel", "Changing video to: $uri")
        _currentUri.value = uri
        isVideoLoaded = false
        isPositionRestored = false
        isExternalSubtitleLoaded = false
        _isHwSupported.value = true
        hwdecEverActiveForCurrentVideo = false
        val scheme = uri.scheme
        isNetworkStream.value = scheme == "http" || scheme == "https"

        // Save progress as 0 if not already present, and update timestamp
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
            if (isNetworkStream.value) {
                dao.insertOrUpdateStream(uri.toString(), null)
            } else {
                val existing = dao.getHistory(uri.toString())
                if (existing == null) {
                    dao.insert(
                        WatchHistoryEntity(
                            uri = uri.toString(),
                            lastPositionMs = 0L,
                            lastPlayedAt = System.currentTimeMillis(),
                            isNetworkStream = false
                        )
                    )
                } else {
                    dao.insert(
                        WatchHistoryEntity(
                            uri = uri.toString(),
                            lastPositionMs = existing.lastPositionMs,
                            lastPlayedAt = System.currentTimeMillis(),
                            isNetworkStream = existing.isNetworkStream,
                            videoTitle = existing.videoTitle
                        )
                    )
                }
            }
        }

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
        if (playbackState.value is PlayerState.Ended) {
            seekTo(0L)
        }
        playerEngine.play()
    }

    fun savePlaybackProgress() {
        val uri = _currentUri.value ?: return
        var pos = currentPosition.value
        val dur = duration.value
        
        // If the video is close to the end (e.g. less than 5 seconds remaining, or past 98% of duration),
        // clear the progress so that it starts from the beginning next time.
        val isNearEnd = dur > 0 && (dur - pos < 5000 || pos > dur * 0.98f)
        
        if (isNearEnd) {
            clearPlaybackProgress()
            return
        }
        
        var shouldSave = pos > 0
        if (dur > 0 && pos > dur * 0.95) {
            pos = 0L
            shouldSave = true
        }
        
        if (shouldSave) {
            viewModelScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
                val existing = dao.getHistory(uri.toString())
                dao.insert(
                    WatchHistoryEntity(
                        uri = uri.toString(),
                        lastPositionMs = pos,
                        lastPlayedAt = System.currentTimeMillis(),
                        isNetworkStream = existing?.isNetworkStream ?: isNetworkStream.value,
                        videoTitle = existing?.videoTitle ?: mediaTitle.value
                    )
                )
                Log.d("PlayerViewModel", "Saved progress: $pos for URI: $uri")
            }
        }
    }

    fun clearPlaybackProgress() {
        val uri = _currentUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
            dao.deleteHistory(uri.toString())
            Log.d("PlayerViewModel", "Cleared watch progress for URI: $uri")
        }
    }

    fun pause() {
        playerEngine.pause()
        savePlaybackProgress()
    }

    fun togglePlayback() {
        if (playbackState.value is PlayerState.Ended) {
            seekTo(0L)
            play()
        } else {
            playerEngine.togglePlayback()
        }
    }

    fun seekTo(position: Long, precise: Boolean = true) {
        playerEngine.seekTo(position, precise)
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

    fun selectSubtitleTrack(id: Int) {
        playerEngine.selectSubtitleTrack(id)
    }

    fun importSubtitle(uri: Uri, select: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = if (uri.scheme == "file") {
                    File(uri.path ?: "")
                } else if (uri.scheme == null) {
                    File(uri.toString())
                } else {
                    null
                }
                
                if (file != null && (!file.exists() || !file.canRead())) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Subtitle file is not readable", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                val absolutePath = file?.absolutePath ?: uri.toString()
                val fileName = file?.name ?: uri.lastPathSegment ?: "External Subtitle"
                
                // Save to database
                val videoUriStr = _currentUri.value?.toString()
                if (videoUriStr != null) {
                    val dao = AppDatabase.getDatabase(getApplication()).videoMetadataDao()
                    dao.saveExternalSubtitle(videoUriStr, uri.toString())
                }
                
                withContext(Dispatchers.Main) {
                    playerEngine.addExternalSubtitle(absolutePath, select)
                    if (select) {
                        Toast.makeText(getApplication(), "Subtitle imported: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.e("PlayerViewModel", "Error importing subtitle", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to import subtitle: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadSavedExternalSubtitle() {
        val uri = _currentUri.value ?: return
        isExternalSubtitleLoaded = true
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(getApplication()).videoMetadataDao()
            val metadata = dao.getMetadataByUri(uri.toString())
            val savedSubUriStr = metadata?.externalSubtitleUri
            if (!savedSubUriStr.isNullOrBlank()) {
                Log.d("PlayerViewModel", "Auto-loading saved subtitle: $savedSubUriStr")
                withContext(Dispatchers.Main) {
                    importSubtitle(Uri.parse(savedSubUriStr), select = false)
                }
            }
        }
    }

    fun selectAudioTrack(id: Int) {
        playerEngine.selectAudioTrack(id)
    }

    fun selectChapter(index: Int) {
        playerEngine.selectChapter(index)
    }

    fun changeYtdlQuality(quality: Int) {
        viewModelScope.launch {
            settingsRepo.updateYtdlQuality(quality)
            val updatedSettings = settingsRepo.playbackSettingsFlow.value.copy(ytdlQuality = quality)
            
            val uri = _currentUri.value
            if (uri != null && isNetworkStream.value) {
                val currentPos = playerEngine.currentPosition.value
                val isPlayingBefore = playerEngine.isPlaying.value
                
                Log.d("PlayerViewModel", "Reloading stream with new quality: $quality at pos: $currentPos")
                
                // Save progress to watch history first so it is restored on reload
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
                    val existing = dao.getHistory(uri.toString())
                    dao.insert(
                        WatchHistoryEntity(
                            uri = uri.toString(),
                            lastPositionMs = currentPos,
                            lastPlayedAt = System.currentTimeMillis(),
                            isNetworkStream = existing?.isNetworkStream ?: isNetworkStream.value,
                            videoTitle = existing?.videoTitle ?: mediaTitle.value
                        )
                    )
                }
                
                isPositionRestored = false
                
                // Re-apply options in the running MPV engine
                YtdlpManager.setupMpvOptions(getApplication(), updatedSettings)
                
                // Force reload of the video
                playerEngine.loadVideo(uri)
                
                if (isPlayingBefore) {
                    playerEngine.play()
                }
            }
        }
    }

    fun toggleDataSaver(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateDataSaverEnabled(enabled)
            val updatedSettings = settingsRepo.playbackSettingsFlow.value.copy(isDataSaverEnabled = enabled)
            
            val uri = _currentUri.value
            if (uri != null && isNetworkStream.value) {
                val currentPos = playerEngine.currentPosition.value
                val isPlayingBefore = playerEngine.isPlaying.value
                
                Log.d("PlayerViewModel", "Reloading stream with Data Saver = $enabled at pos: $currentPos")
                
                // Save progress to watch history first so it is restored on reload
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getDatabase(getApplication()).watchHistoryDao()
                    val existing = dao.getHistory(uri.toString())
                    dao.insert(
                        WatchHistoryEntity(
                            uri = uri.toString(),
                            lastPositionMs = currentPos,
                            lastPlayedAt = System.currentTimeMillis(),
                            isNetworkStream = existing?.isNetworkStream ?: isNetworkStream.value,
                            videoTitle = existing?.videoTitle ?: mediaTitle.value
                        )
                    )
                }
                
                isPositionRestored = false
                
                // Re-apply options in the running MPV engine
                YtdlpManager.setupMpvOptions(getApplication(), updatedSettings)
                
                // Update cache and buffer limits dynamically
                try {
                    val readaheadSecs = if (enabled) 60 else 300
                    val maxBytes = if (enabled) 50 * 1024 * 1024 else 400 * 1024 * 1024
                    `is`.xyz.mpv.MPVLib.setPropertyString("demuxer-readahead-secs", "$readaheadSecs")
                    `is`.xyz.mpv.MPVLib.setPropertyString("demuxer-max-bytes", "$maxBytes")
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Failed to update demuxer cache options dynamically", e)
                }

                // Force reload of the video
                playerEngine.loadVideo(uri)
                
                if (isPlayingBefore) {
                    playerEngine.play()
                }
            }
        }
    }

    fun cycleDecoder() {
        val currentMode = playbackSettings.value.decoderMode
        var nextMode = currentMode.next()
        if (!_isHwSupported.value && (nextMode == DecoderMode.HW || nextMode == DecoderMode.HW_PLUS || nextMode == DecoderMode.AUTO)) {
            nextMode = DecoderMode.SW
            Toast.makeText(getApplication(), "HW skipped (Unsupported)", Toast.LENGTH_SHORT).show()
        }
        // Reset the HW-ever-active flag when switching to SW so Coroutine 1 doesn't mistake
        // the intentional hwdec-current="no" for a runtime fallback (release timing race).
        if (nextMode == DecoderMode.SW) {
            hwdecEverActiveForCurrentVideo = false
        }
        playerEngine.setDecoder(nextMode)
        viewModelScope.launch {
            settingsRepo.updateDecoderMode(nextMode)
        }
    }

    fun updateDecoderMode(mode: DecoderMode) {
        if (!_isHwSupported.value && (mode == DecoderMode.HW || mode == DecoderMode.HW_PLUS || mode == DecoderMode.AUTO)) {
            Toast.makeText(
                getApplication(),
                "Hardware decoding not supported for this video",
                Toast.LENGTH_LONG
            ).show()
            viewModelScope.launch {
                settingsRepo.updateDecoderMode(DecoderMode.SW)
            }
        } else {
            // Reset the HW-ever-active flag when the user intentionally changes decoder mode.
            // This prevents Coroutine 1 from misinterpreting the intentional hwdec-current="no"
            // (caused by switching to SW) as a runtime HW fallback event - a race condition that
            // is especially pronounced in Release builds where R8 tightens coroutine scheduling.
            if (mode == DecoderMode.SW) {
                hwdecEverActiveForCurrentVideo = false
            }
            viewModelScope.launch {
                settingsRepo.updateDecoderMode(mode)
            }
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        playerEngine.setSubtitleDelay(delayMs)
    }

    fun setSubtitleStyle(scale: Float, font: String, bold: Boolean) {
        playerEngine.setSubtitleStyle(scale, font, bold)
    }

    fun seekNextSubtitle() {
        playerEngine.seekNextSubtitle()
    }

    fun seekPrevSubtitle() {
        playerEngine.seekPrevSubtitle()
    }

    fun updateCustomPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepo.updateCustomPlaybackSpeed(speed)
            setPlaybackSpeed(speed)
        }
    }

    fun updateIsBottomLayoutEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateIsBottomLayoutEnabled(enabled)
        }
    }

    fun updateShowControlGradients(show: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateShowControlGradients(show)
        }
    }

    fun updateTapAndHoldSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepo.updateTapAndHoldSpeed(speed)
        }
    }

    fun updateDoubleTapSeekDuration(durationMs: Long) {
        viewModelScope.launch {
            settingsRepo.updateDoubleTapSeekDuration(durationMs)
        }
    }

    fun updateEnhanceMode(mode: EnhanceMode) {
        viewModelScope.launch {
            settingsRepo.updateEnhanceMode(mode)
        }
    }

    fun updateEnhanceSaturation(value: Int) {
        viewModelScope.launch {
            settingsRepo.updateEnhanceSaturation(value)
        }
    }

    fun updateEnhanceContrast(value: Int) {
        viewModelScope.launch {
            settingsRepo.updateEnhanceContrast(value)
        }
    }

    fun updateEnhanceBrightness(value: Int) {
        viewModelScope.launch {
            settingsRepo.updateEnhanceBrightness(value)
        }
    }

    fun updateEnhanceGamma(value: Int) {
        viewModelScope.launch {
            settingsRepo.updateEnhanceGamma(value)
        }
    }

    fun updateEnhanceHue(value: Int) {
        viewModelScope.launch {
            settingsRepo.updateEnhanceHue(value)
        }
    }

    fun takeVideoScreenshot() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val location = playbackSettings.value.screenshotLocation
            val resolvedPath = getPhysicalPathFromTreeUri(context, location)
            
            try {
                withContext(Dispatchers.IO) {
                    `is`.xyz.mpv.MPVLib.setOptionString("screenshot-directory", resolvedPath)
                    `is`.xyz.mpv.MPVLib.command("screenshot", "video")
                }
                Toast.makeText(context, "Screenshot saved to: $resolvedPath", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to take video screenshot", e)
                Toast.makeText(context, "Screenshot failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getPhysicalPathFromTreeUri(context: Context, uriString: String): String {
        if (!uriString.startsWith("content://")) {
            val extDir = Environment.getExternalStorageDirectory()
            val file = File(extDir, uriString)
            if (!file.exists()) {
                file.mkdirs()
            }
            return file.absolutePath
        }

        try {
            val uri = Uri.parse(uriString)
            if (uri.authority == "com.android.externalstorage.documents") {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    if ("primary".equals(type, ignoreCase = true)) {
                        val file = File(Environment.getExternalStorageDirectory(), relativePath)
                        if (!file.exists()) {
                            file.mkdirs()
                        }
                        return file.absolutePath
                    } else {
                        val extDirs = context.getExternalFilesDirs(null)
                        for (extDir in extDirs) {
                            if (extDir != null) {
                                val path = extDir.absolutePath
                                val index = path.indexOf("/Android/data")
                                if (index != -1) {
                                    val root = path.substring(0, index)
                                    val file = File(root, relativePath)
                                    if (!file.exists()) {
                                        file.mkdirs()
                                    }
                                    return file.absolutePath
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error resolving SAF URI: $uriString", e)
        }

        val defaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Nosved Player/Screenshot"
        )
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir.absolutePath
    }

    private fun applyEnhanceSettings(settings: PlaybackSettings) {
        val sat: Int
        val con: Int
        val bright: Int
        val gam: Int
        val hue: Int
        when (settings.enhanceMode) {
            EnhanceMode.OFF -> {
                sat = 0
                con = 0
                bright = 0
                gam = 0
                hue = 0
            }
            EnhanceMode.DEFAULT -> {
                sat = 25
                con = 8
                bright = 0
                gam = 3
                hue = 0
            }
            EnhanceMode.CUSTOM -> {
                sat = settings.enhanceSaturation
                con = settings.enhanceContrast
                bright = settings.enhanceBrightness
                gam = settings.enhanceGamma
                hue = settings.enhanceHue
            }
        }
        try {
            Log.d("PlayerViewModel", "Applying enhance settings: mode=${settings.enhanceMode}, sat=$sat, con=$con, bright=$bright, gam=$gam, hue=$hue")
            `is`.xyz.mpv.MPVLib.setPropertyInt("saturation", sat)
            `is`.xyz.mpv.MPVLib.setPropertyInt("contrast", con)
            `is`.xyz.mpv.MPVLib.setPropertyInt("brightness", bright)
            `is`.xyz.mpv.MPVLib.setPropertyInt("gamma", gam)
            `is`.xyz.mpv.MPVLib.setPropertyInt("hue", hue)
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Failed to apply enhance settings", e)
        }
    }

    override fun onCleared() {
        savePlaybackProgress()
        super.onCleared()
        Log.d("PlayerViewModel", "PlayerViewModel cleared, releasing resources")
        // RELEASE FIX: Unregister the SharedPreferences listener to break the strong reference
        // that SharedPreferences holds to the repository lambda (prevents GC leak in release).
        settingsRepo.close()
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

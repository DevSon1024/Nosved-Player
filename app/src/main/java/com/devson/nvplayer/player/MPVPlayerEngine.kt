package com.devson.nvplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import com.devson.nvplayer.player.ytdlp.YtdlpManager
import com.devson.nvplayer.repository.PlaybackSettingsRepository

class MPVPlayerEngine(private val context: Context) : PlayerEngine, MPVLib.EventObserver {

    companion object {
        @Volatile
        var isInitialized = false

        @Volatile
        var activeInstance: MPVPlayerEngine? = null
    }

    private val settingsRepo = PlaybackSettingsRepository(context)

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val playbackState: StateFlow<PlayerState> = _playbackState.asStateFlow()

    private val _videoWidth = MutableStateFlow(0L)
    override val videoWidth: StateFlow<Long> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(0L)
    override val videoHeight: StateFlow<Long> = _videoHeight.asStateFlow()

    private val _videoRotation = MutableStateFlow(0L)
    override val videoRotation: StateFlow<Long> = _videoRotation.asStateFlow()

    private val _currentSubtitleText = MutableStateFlow("")
    override val currentSubtitleText: StateFlow<String> = _currentSubtitleText.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    override val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    override val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChapterInfo>>(emptyList())
    override val chapters: StateFlow<List<ChapterInfo>> = _chapters.asStateFlow()

    private val _hwdecCurrent = MutableStateFlow("no")
    override val hwdecCurrent: StateFlow<String> = _hwdecCurrent.asStateFlow()

    private val _networkSpeedBytesPerSec = MutableStateFlow(0L)
    override val networkSpeedBytesPerSec: StateFlow<Long> = _networkSpeedBytesPerSec.asStateFlow()

    private val _bufferDurationSeconds = MutableStateFlow(0.0)
    override val bufferDurationSeconds: StateFlow<Double> = _bufferDurationSeconds.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    override val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _mediaTitle = MutableStateFlow("")
    override val mediaTitle: StateFlow<String> = _mediaTitle.asStateFlow()

    init {
        try {
            if (isInitialized) {
                // RELEASE FIX: Process was reused (activity recreation, PiP, back-stack pop).
                // MPVLib is a C++ singleton — calling create() twice without destroy() is UB/crash.
                // Destroy the old instance cleanly before re-creating.
                Log.w("MPVPlayerEngine", "MPVLib already initialized — destroying old instance before re-init")
                MPVLib.removeObserver(this)
                MPVLib.destroy()
                isInitialized = false
            }

            Log.d("MPVPlayerEngine", "Initializing MPVLib instance")
            MPVLib.create(context.applicationContext)

            // Configure standard MPV playback options for modern android
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("force-window", "yes")
            
            // Prioritize mediacodec (hardware decoding) and use fast profile
            MPVLib.setOptionString("hwdec", "mediacodec")
            MPVLib.setOptionString("profile", "fast")
            
            // Keep native player responsive and smooth
            MPVLib.setOptionString("keep-open", "yes")

            // Ensure cacert.pem is copied for SSL/TLS verification
            val cacertFile = File(context.filesDir, "cacert.pem")
            if (!cacertFile.exists()) {
                try {
                    context.assets.open("cacert.pem").use { input ->
                        FileOutputStream(cacertFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("MPVPlayerEngine", "Copied cacert.pem successfully")
                } catch (e: Exception) {
                    Log.e("MPVPlayerEngine", "Failed to copy cacert.pem", e)
                }
            }

            // Configure TLS options
            MPVLib.setOptionString("tls-verify", "yes")
            MPVLib.setOptionString("tls-ca-file", cacertFile.absolutePath)

            // Set up yt-dlp environment and options
            val settings = settingsRepo.playbackSettingsFlow.value

            // Enable cache
            MPVLib.setOptionString("cache", "yes")

            // Configure cache sizes and read-ahead thresholds based on Data Saver state
            if (settings.isDataSaverEnabled) {
                MPVLib.setOptionString("demuxer-max-bytes", "${50 * 1024 * 1024}")
                MPVLib.setOptionString("demuxer-readahead-secs", "60")
            } else {
                MPVLib.setOptionString("demuxer-max-bytes", "${400 * 1024 * 1024}")
                MPVLib.setOptionString("demuxer-readahead-secs", "300")
            }

            // Keep a healthy back-buffer (for rewinding)
            MPVLib.setOptionString("demuxer-max-back-bytes", "${50 * 1024 * 1024}")

            YtdlpManager.setupMpvOptions(context, settings)

            MPVLib.init()
            isInitialized = true
            activeInstance = this

            // Register event listener
            MPVLib.addObserver(this)

            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            
            // Observe video dimension properties as standard long integers
            MPVLib.observeProperty("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("video-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("video-params/rotate", MPVLib.MpvFormat.MPV_FORMAT_INT64)

            // Observe subtitle text changes
            MPVLib.observeProperty("sub-text", MPVLib.MpvFormat.MPV_FORMAT_STRING)

            // Observe media title changes
            MPVLib.observeProperty("media-title", MPVLib.MpvFormat.MPV_FORMAT_STRING)

            // Observe active hardware decoder changes
            MPVLib.observeProperty("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)

            // Observe cache and network properties for streaming statistics
            MPVLib.observeProperty("cache-speed", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("demux-cache-duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("demuxer-cache-duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)

            Log.d("MPVPlayerEngine", "MPVLib initialized successfully")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to initialize MPVLib", e)
            _playbackState.value = PlayerState.Error("Initialization error: ${e.localizedMessage}")
        }
    }

    override fun loadVideo(uri: Uri) {
        _playbackState.value = PlayerState.Loading
        _mediaTitle.value = ""
        _currentPosition.value = 0L
        _duration.value = 0L
        _videoWidth.value = 0L
        _videoHeight.value = 0L
        _videoRotation.value = 0L
        _chapters.value = emptyList()
        _networkSpeedBytesPerSec.value = 0L
        _bufferDurationSeconds.value = 0.0
        _bufferedPosition.value = 0L
        
        val uriString = uri.toString()
        Log.d("MPVPlayerEngine", "Loading media file: $uriString")
        try {
            MPVLib.command("loadfile", uriString)
            MPVLib.setPropertyBoolean("pause", false) // Auto-play the loaded media file
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to load file via MPVLib", e)
            _playbackState.value = PlayerState.Error("Load error: ${e.localizedMessage}")
        }
    }

    override fun play() {
        try {
            MPVLib.setPropertyBoolean("pause", false)
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to resume playback", e)
        }
    }

    override fun pause() {
        try {
            MPVLib.setPropertyBoolean("pause", true)
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to pause playback", e)
        }
    }

    override fun togglePlayback() {
        try {
            val isCurrentlyPaused = MPVLib.getPropertyBoolean("pause") ?: false
            MPVLib.setPropertyBoolean("pause", !isCurrentlyPaused)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to toggle playback", e)
        }
    }

    override fun seekTo(position: Long, precise: Boolean) {
        val seconds = position / 1000.0
        Log.d("MPVPlayerEngine", "Seeking to position: $position ms ($seconds s), precise: $precise")
        try {
            val mode = if (precise) "absolute" else "absolute+keyframes"
            MPVLib.command("seek", seconds.toString(), mode)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to seek to position", e)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        Log.d("MPVPlayerEngine", "Setting playback speed to: $speed")
        try {
            MPVLib.setPropertyDouble("speed", speed.toDouble())
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set playback speed", e)
        }
    }

    override fun cycleSubtitle() {
        Log.d("MPVPlayerEngine", "Cycling subtitle")
        try {
            MPVLib.command("cycle", "sid")
            updateTracks()
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to cycle subtitle", e)
        }
    }

    override fun cycleAudio() {
        Log.d("MPVPlayerEngine", "Cycling audio")
        try {
            MPVLib.command("cycle", "aid")
            updateTracks()
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to cycle audio", e)
        }
    }

    override fun selectSubtitleTrack(id: Int) {
        Log.d("MPVPlayerEngine", "Selecting subtitle track ID: $id")
        try {
            if (id == -1) {
                MPVLib.setPropertyString("sid", "no")
            } else {
                MPVLib.setPropertyInt("sid", id)
            }
            updateTracks()
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to select subtitle track: $id", e)
        }
    }

    override fun selectAudioTrack(id: Int) {
        Log.d("MPVPlayerEngine", "Selecting audio track ID: $id")
        try {
            if (id == -1) {
                MPVLib.setPropertyString("aid", "no")
            } else {
                MPVLib.setPropertyInt("aid", id)
            }
            updateTracks()
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to select audio track: $id", e)
        }
    }

    override fun selectChapter(index: Int) {
        Log.d("MPVPlayerEngine", "Selecting chapter index: $index")
        try {
            MPVLib.setPropertyInt("chapter", index)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to select chapter: $index", e)
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        Log.d("MPVPlayerEngine", "Setting subtitle delay: $delayMs ms")
        try {
            MPVLib.setPropertyDouble("sub-delay", delayMs / 1000.0)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set subtitle delay: $delayMs", e)
        }
    }

    override fun setSubtitleStyle(scale: Float, font: String, bold: Boolean) {
        Log.d("MPVPlayerEngine", "Setting subtitle style: scale=$scale, font=$font, bold=$bold")
        try {
            MPVLib.setPropertyDouble("sub-scale", scale.toDouble())
            if (font.isNotEmpty()) {
                MPVLib.setPropertyString("sub-font", font)
            }
            MPVLib.setPropertyString("sub-bold", if (bold) "yes" else "no")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set subtitle style", e)
        }
    }

    override fun seekNextSubtitle() {
        Log.d("MPVPlayerEngine", "Seeking to next subtitle")
        try {
            MPVLib.command("sub-seek", "1")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to seek to next subtitle", e)
        }
    }

    override fun seekPrevSubtitle() {
        Log.d("MPVPlayerEngine", "Seeking to previous subtitle")
        try {
            MPVLib.command("sub-seek", "-1")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to seek to previous subtitle", e)
        }
    }

    private fun updateTracks() {
        try {
            val count = MPVLib.getPropertyInt("track-list/count") ?: 0
            val subs = mutableListOf<TrackInfo>()
            val audios = mutableListOf<TrackInfo>()
            
            // Add fallback/disable option for subtitle
            subs.add(TrackInfo(-1, "sub", "None (Disabled)", false))
            
            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type") ?: ""
                val id = MPVLib.getPropertyInt("track-list/$i/id") ?: -1
                val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false
                
                val trackName = when {
                    title.isNotEmpty() && lang.isNotEmpty() -> "$title ($lang)"
                    title.isNotEmpty() -> title
                    lang.isNotEmpty() -> lang
                    else -> "${type.replaceFirstChar { it.uppercaseChar() }} Track #$id"
                }
                
                val track = TrackInfo(id, type, trackName, selected)
                if (type == "sub") {
                    subs.add(track)
                } else if (type == "audio") {
                    audios.add(track)
                }
            }
            
            // Set "None" selected status if no other subtitle track is active
            val anySubSelected = subs.filter { it.id != -1 }.any { it.selected }
            if (!anySubSelected && subs.isNotEmpty()) {
                subs[0] = subs[0].copy(selected = true)
            }
            
            _subtitleTracks.value = subs
            _audioTracks.value = audios
            Log.d("MPVPlayerEngine", "Tracks updated. Subs: ${subs.size}, Audios: ${audios.size}")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to update track list", e)
        }
    }

    private fun updateChapters() {
        try {
            val count = MPVLib.getPropertyInt("chapter-list/count") ?: 0
            val list = mutableListOf<ChapterInfo>()
            for (i in 0 until count) {
                val title = MPVLib.getPropertyString("chapter-list/$i/title") ?: "Chapter ${i + 1}"
                val timeSecStr = MPVLib.getPropertyString("chapter-list/$i/time") ?: "0.0"
                val timeSec = timeSecStr.toDoubleOrNull() ?: 0.0
                list.add(ChapterInfo(i, title, (timeSec * 1000).toLong()))
            }
            _chapters.value = list
            Log.d("MPVPlayerEngine", "Chapters updated: ${list.size}")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to update chapters", e)
        }
    }

    override fun setAudioBoost(boost: Boolean) {
        Log.d("MPVPlayerEngine", "Setting audio boost: $boost")
        try {
            val volumeValue = if (boost) 200.0 else 100.0
            MPVLib.setPropertyDouble("volume", volumeValue)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set audio boost property", e)
        }
    }

    override fun setMpvVolume(volume: Double) {
        Log.d("MPVPlayerEngine", "Setting MPV volume: $volume")
        try {
            MPVLib.setPropertyDouble("volume", volume)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set MPV volume", e)
        }
    }

    override fun setDecoder(mode: DecoderMode) {
        Log.d("MPVPlayerEngine", "Setting decoder to: ${mode.displayName} (${mode.value})")
        try {
            MPVLib.setPropertyString("hwdec", mode.value)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set decoder", e)
        }
    }

    override fun setAspectMode(mode: AspectMode) {
        Log.d("MPVPlayerEngine", "Setting aspect mode to: ${mode.name}")
        try {
            when (mode) {
                AspectMode.FIT -> {
                    MPVLib.setPropertyString("keepaspect", "yes")
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyString("video-unscaled", "no")
                }
                AspectMode.STRETCH -> {
                    MPVLib.setPropertyString("keepaspect", "no")
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyString("video-unscaled", "no")
                }
                AspectMode.CROP -> {
                    MPVLib.setPropertyString("keepaspect", "yes")
                    MPVLib.setPropertyDouble("panscan", 1.0)
                    MPVLib.setPropertyString("video-unscaled", "no")
                }
                AspectMode.ORIGINAL -> {
                    MPVLib.setPropertyString("keepaspect", "yes")
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyString("video-unscaled", "yes")
                }
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set aspect mode", e)
        }
    }

    override fun setVideoTrackEnabled(enabled: Boolean) {
        Log.d("MPVPlayerEngine", "Setting video track enabled: $enabled")
        try {
            if (enabled) {
                MPVLib.setPropertyString("vid", "auto")
            } else {
                MPVLib.setPropertyString("vid", "no")
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set video track enabled", e)
        }
    }

    override fun release() {
        Log.d("MPVPlayerEngine", "Releasing MPVPlayerEngine resources")
        activeInstance = null
        try {
            // Instantly sever network connections and halt the demuxer
            MPVLib.command("stop")
            
            MPVLib.removeObserver(this)
            MPVLib.destroy()
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to release MPVLib resources", e)
        }
    }

    // EventObserver Implementations
    override fun eventProperty(property: String) {
        // No-op for empty property triggers
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "video-params/w" -> _videoWidth.value = value
            "video-params/h" -> _videoHeight.value = value
            "video-params/rotate" -> _videoRotation.value = value
            "cache-speed" -> _networkSpeedBytesPerSec.value = value
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") {
            Log.d("MPVPlayerEngine", "Property Changed: pause = $value")
            _isPlaying.value = !value
            val isEof = try {
                MPVLib.getPropertyBoolean("eof-reached") ?: false
            } catch (e: Exception) {
                false
            }
            _playbackState.value = if (isEof) {
                PlayerState.Ended
            } else {
                if (value) PlayerState.Paused else PlayerState.Playing
            }
        } else if (property == "eof-reached") {
            Log.d("MPVPlayerEngine", "Property Changed: eof-reached = $value")
            if (value) {
                _playbackState.value = PlayerState.Ended
                _isPlaying.value = false
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        val safeVal: String? = value
        if (property == "sub-text") {
            _currentSubtitleText.value = safeVal ?: ""
        } else if (property == "media-title") {
            _mediaTitle.value = safeVal ?: ""
        } else if (property == "hwdec-current") {
            _hwdecCurrent.value = safeVal ?: "no"
            Log.d("MPVPlayerEngine", "Active hardware decoder changed: $safeVal")
        }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> {
                _currentPosition.value = (value * 1000).toLong()
            }
            "duration" -> {
                _duration.value = (value * 1000).toLong()
            }
            "demux-cache-duration" -> {
                _bufferDurationSeconds.value = value
            }
            "demuxer-cache-duration" -> {
                _bufferedPosition.value = _currentPosition.value + (value * 1000).toLong()
            }
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {
        // No-op for complex MPVNode properties
    }

    override fun event(eventId: Int, node: MPVNode) {
        Log.d("MPVPlayerEngine", "Event received from MPV: $eventId")
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                _playbackState.value = PlayerState.Loading
            }
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                _playbackState.value = PlayerState.Playing
                _isPlaying.value = true
                updateTracks()
                updateChapters()
            }
            16, 21 -> {
                Log.d("MPVPlayerEngine", "Tracks changed/restart event received ($eventId)")
                updateTracks()
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                _playbackState.value = PlayerState.Ended
                _isPlaying.value = false
                _currentPosition.value = 0L
            }
        }
    }
}

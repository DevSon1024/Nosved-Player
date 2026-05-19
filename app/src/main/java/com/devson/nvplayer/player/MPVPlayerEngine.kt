package com.devson.nvplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MPVPlayerEngine(private val context: Context) : PlayerEngine, MPVLib.EventObserver {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val playbackState: StateFlow<PlayerState> = _playbackState.asStateFlow()

    init {
        try {
            Log.d("MPVPlayerEngine", "Initializing MPVLib instance")
            MPVLib.create(context.applicationContext)

            // Configure standard MPV playback options for modern android
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("hwdec", "auto")
            MPVLib.setOptionString("force-window", "yes")
            
            // Keep native player responsive and smooth
            MPVLib.setOptionString("keep-open", "yes")

            MPVLib.init()

            // Register event listener
            MPVLib.addObserver(this)

            // Register standard property observers
            // Format 5 is MPV_FORMAT_DOUBLE, Format 3 is MPV_FORMAT_FLAG (boolean)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)

            Log.d("MPVPlayerEngine", "MPVLib initialized successfully")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to initialize MPVLib", e)
            _playbackState.value = PlayerState.Error("Initialization error: ${e.localizedMessage}")
        }
    }

    override fun loadVideo(uri: Uri) {
        _playbackState.value = PlayerState.Loading
        _currentPosition.value = 0L
        _duration.value = 0L
        
        val uriString = uri.toString()
        Log.d("MPVPlayerEngine", "Loading media file: $uriString")
        try {
            MPVLib.command("loadfile", uriString)
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

    override fun seekTo(position: Long) {
        val seconds = position / 1000.0
        Log.d("MPVPlayerEngine", "Seeking to position: $position ms ($seconds s)")
        try {
            MPVLib.command("seek", seconds.toString(), "absolute")
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
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to cycle subtitle", e)
        }
    }

    override fun cycleAudio() {
        Log.d("MPVPlayerEngine", "Cycling audio")
        try {
            MPVLib.command("cycle", "aid")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to cycle audio", e)
        }
    }

    override fun release() {
        Log.d("MPVPlayerEngine", "Releasing MPVPlayerEngine resources")
        try {
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
        // No-op for default long property events
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") {
            Log.d("MPVPlayerEngine", "Property Changed: pause = $value")
            _isPlaying.value = !value
            _playbackState.value = if (value) PlayerState.Paused else PlayerState.Playing
        }
    }

    override fun eventProperty(property: String, value: String) {
        // No-op for default string property events
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> {
                _currentPosition.value = (value * 1000).toLong()
            }
            "duration" -> {
                _duration.value = (value * 1000).toLong()
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
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                _playbackState.value = PlayerState.Ended
                _isPlaying.value = false
                _currentPosition.value = 0L
            }
        }
    }
}

package com.devson.nvplayer.player.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.devson.nvplayer.data.repository.PlaybackSettingsRepository
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.player.model.ChapterInfo
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.player.model.TrackInfo
import com.devson.nvplayer.player.ytdlp.YtdlpManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import com.devson.nvplayer.data.model.StreamType
import com.devson.nvplayer.data.model.VideoQualityOption
import com.devson.nvplayer.util.StreamQualityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

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
                // MPVLib is a C++ singleton - calling create() twice without destroy() is UB/crash.
                // Destroy the old instance cleanly before re-creating.
                Log.w("MPVPlayerEngine", "MPVLib already initialized - destroying old instance before re-init")
                MPVLib.removeObserver(this)
                MPVLib.destroy()
                isInitialized = false
            }

            Log.d("MPVPlayerEngine", "Initializing MPVLib instance")
            MPVLib.create(context.applicationContext)

            // Configure MPV config directory
            MPVLib.setOptionString("config-dir", context.filesDir.path)
            MPVLib.setOptionString("config", "yes")

            // Configure standard MPV playback options for modern android
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("force-window", "yes")

            // Prioritize hardware decoding with auto-safe fallback and use fast profile
            MPVLib.setOptionString("hwdec", "auto-safe")
            MPVLib.setOptionString("profile", "fast")

            // Subtitle rendering options: render natively on MPV surface and disable secondary-sid mirroring
            MPVLib.setOptionString("sub-visibility", "yes")
            MPVLib.setOptionString("secondary-sid", "no")
            MPVLib.setOptionString("sub-auto", "fuzzy")
            MPVLib.setOptionString("sub-fonts-dir", "/system/fonts")
            MPVLib.setOptionString("sub-ass-override", "scale")

            // Keep native player responsive and smooth
            MPVLib.setOptionString("keep-open", "yes")

            // Ensure required assets (subfont.ttf, cacert.pem) are copied for subtitles and TLS
            copyRequiredAssets(context)

            val cacertFile = File(context.filesDir, "cacert.pem")
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

            // Load and apply custom options from mpv.conf if it exists
            val mpvConfFile = File(context.filesDir, "mpv.conf")
            if (mpvConfFile.exists()) {
                try {
                    mpvConfFile.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val eqIdx = trimmed.indexOf('=')
                            if (eqIdx != -1) {
                                val key = trimmed.substring(0, eqIdx).trim()
                                val value = trimmed.substring(eqIdx + 1).trim()
                                if (key.isNotEmpty()) {
                                    Log.d("MPVPlayerEngine", "Applying custom mpv option from mpv.conf: $key = $value")
                                    try {
                                        MPVLib.setOptionString(key, value)
                                    } catch (e: Exception) {
                                        Log.e("MPVPlayerEngine", "Failed to set custom option $key = $value", e)
                                    }
                                }
                            } else {
                                val key = trimmed
                                Log.d("MPVPlayerEngine", "Applying custom mpv flag option from mpv.conf: $key")
                                try {
                                    MPVLib.setOptionString(key, "yes")
                                } catch (e: Exception) {
                                    try {
                                        MPVLib.setOptionString(key, "")
                                    } catch (ex: Exception) {
                                        Log.e("MPVPlayerEngine", "Failed to set custom flag option $key", ex)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MPVPlayerEngine", "Error reading or applying mpv.conf", e)
                }
            }

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
            MPVLib.observeProperty("osd-width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("osd-height", MPVLib.MpvFormat.MPV_FORMAT_INT64)

            // Observe subtitle text changes
            MPVLib.observeProperty("sub-text", MPVLib.MpvFormat.MPV_FORMAT_STRING)

            // Observe media title changes
            MPVLib.observeProperty("media-title", MPVLib.MpvFormat.MPV_FORMAT_STRING)

            // Observe active hardware decoder changes
            MPVLib.observeProperty("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)

            // Observe cache and network properties for streaming statistics
            MPVLib.observeProperty("cache-speed", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("demux-cache-duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.setOptionString("glsl-shaders", "")
            MPVLib.setPropertyString("glsl-shaders", "")
            MPVLib.setPropertyDouble("video-scale-x", 1.0)
            MPVLib.setPropertyDouble("video-scale-y", 1.0)
            MPVLib.setPropertyString("sub-visibility", "yes")
            MPVLib.setPropertyString("secondary-sid", "no")

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

        applyMpvConfOptions()

        val uriString = uri.toString()
        Log.d("MPVPlayerEngine", "Loading media file: $uriString")
        try {
            try { MPVLib.command("stop") } catch (_: Exception) {}
            MPVLib.command("loadfile", uriString)
            MPVLib.setPropertyBoolean("pause", false) // Auto-play the loaded media file
            MPVLib.setPropertyString("secondary-sid", "no")
            val settings = settingsRepo.playbackSettingsFlow.value
            applySubtitleSettings(settings)
            setAmbientMode(settings.isAmbientModeEnabled, settings.ambientBlurStyle)
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
            MPVLib.setPropertyString("secondary-sid", "no")
            MPVLib.command("cycle", "sid")
            val currentSid = MPVLib.getPropertyString("sid")
            if (currentSid == "no" || currentSid.isNullOrEmpty()) {
                MPVLib.setPropertyString("sub-visibility", "no")
            } else {
                MPVLib.setPropertyString("sub-visibility", "yes")
            }
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
            MPVLib.setPropertyString("secondary-sid", "no")
            if (id == -1) {
                MPVLib.setPropertyString("sid", "no")
                MPVLib.setPropertyString("sub-visibility", "no")
                _currentSubtitleText.value = ""
            } else {
                MPVLib.setPropertyInt("sid", id)
                MPVLib.setPropertyString("sub-visibility", "yes")
            }
            updateTracks()
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to select subtitle track: $id", e)
        }
    }

    override fun addExternalSubtitle(path: String, select: Boolean) {
        Log.d("MPVPlayerEngine", "Adding external subtitle: $path, select: $select")
        try {
            val file = java.io.File(path)
            val title = file.name.substringBeforeLast('.')
            val flag = if (select) "select" else "auto"
            MPVLib.setPropertyString("secondary-sid", "no")
            MPVLib.command("sub-add", path, flag, title)
            if (select) {
                MPVLib.setPropertyString("secondary-sid", "no")
                MPVLib.setPropertyString("sub-visibility", "yes")
            }
            updateTracks()
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to add external subtitle: $path", e)
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

    override fun applySubtitleSettings(settings: com.devson.nvplayer.data.repository.PlaybackSettings) {
        Log.d("MPVPlayerEngine", "Applying subtitle settings to native MPV engine: $settings")
        try {
            // Subtitle size scaling
            MPVLib.setPropertyDouble("sub-scale", settings.subtitleTextSizeScale.toDouble())
            MPVLib.setPropertyDouble("sub-font-size", 55.0)

            // Subtitle delay
            MPVLib.setPropertyDouble("sub-delay", settings.subtitleDelayMs / 1000.0)

            // Subtitle vertical position (0.0 = bottom (100%), 0.85 = top (15%))
            val pos = (100 - (settings.subtitleVerticalOffset * 100).toInt()).coerceIn(0, 100)
            MPVLib.setPropertyInt("sub-pos", pos)

            // Subtitle font family (empty string lets libass fall back to subfont.ttf)
            val fontName = when (settings.subtitleFont) {
                com.devson.nvplayer.data.repository.SubtitleFont.DEFAULT -> ""
                com.devson.nvplayer.data.repository.SubtitleFont.MONOSPACE -> "monospace"
                com.devson.nvplayer.data.repository.SubtitleFont.SANS_SERIF -> ""
                com.devson.nvplayer.data.repository.SubtitleFont.SERIF -> "serif"
            }
            MPVLib.setPropertyString("sub-font", fontName)

            // Subtitle bold weight
            MPVLib.setPropertyString("sub-bold", if (settings.isSubtitleBold) "yes" else "no")

            // Subtitle force ASS / SSA override
            MPVLib.setPropertyString("sub-ass-override", if (settings.forceAssSubtitleOverride) "force" else "scale")

            // Subtitle background and border styling
            when (settings.subtitleBgStyle) {
                0 -> {
                    // None (transparent background with standard outline & shadow)
                    MPVLib.setPropertyString("sub-color", "#FFFFFFFF")
                    MPVLib.setPropertyString("sub-border-color", "#FF000000")
                    MPVLib.setPropertyDouble("sub-border-size", 2.5)
                    MPVLib.setPropertyDouble("sub-shadow-offset", 1.0)
                    MPVLib.setPropertyString("sub-shadow-color", "#80000000")
                    MPVLib.setPropertyString("sub-back-color", "#00000000")
                }
                1 -> {
                    // Glass / Translucent black background box
                    MPVLib.setPropertyString("sub-color", "#FFFFFFFF")
                    MPVLib.setPropertyString("sub-border-color", "#FF000000")
                    MPVLib.setPropertyDouble("sub-border-size", 1.5)
                    MPVLib.setPropertyDouble("sub-shadow-offset", 0.0)
                    MPVLib.setPropertyString("sub-back-color", "#80000000")
                }
                2 -> {
                    // Solid black background box
                    MPVLib.setPropertyString("sub-color", "#FFFFFFFF")
                    MPVLib.setPropertyString("sub-border-color", "#00000000")
                    MPVLib.setPropertyDouble("sub-border-size", 0.0)
                    MPVLib.setPropertyDouble("sub-shadow-offset", 0.0)
                    MPVLib.setPropertyString("sub-back-color", "#FF000000")
                }
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to apply subtitle settings to MPVLib", e)
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
            subs.add(TrackInfo(-1, "sub", "None (Disabled)", false, isExternal = false))

            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type") ?: ""
                val id = MPVLib.getPropertyInt("track-list/$i/id") ?: -1
                val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
                val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false
                val external = MPVLib.getPropertyBoolean("track-list/$i/external") ?: false

                val trackName = when {
                    title.isNotEmpty() && lang.isNotEmpty() -> "$title ($lang)"
                    title.isNotEmpty() -> title
                    lang.isNotEmpty() -> lang
                    external -> "External Subtitle #$id"
                    else -> "${type.replaceFirstChar { it.uppercaseChar() }} Track #$id"
                }

                val track = TrackInfo(id, type, trackName, selected, isExternal = external, lang = lang)
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
                _currentSubtitleText.value = ""
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

    private fun applyDefaultLanguagePreferences() {
        try {
            val audioLangPref = settingsRepo.defaultAudioLangFlow.value.trim().lowercase()
            val subLangPref = settingsRepo.defaultSubtitleLangFlow.value.trim().lowercase()

            if (audioLangPref.isNotEmpty()) {
                val audios = _audioTracks.value.filter { it.id != -1 }
                val match = audios.firstOrNull { track ->
                    langMatches(track.lang, audioLangPref)
                }
                if (match != null) {
                    Log.d("MPVPlayerEngine", "Auto-selecting audio track: ${match.name} (lang=${match.lang}) for pref=$audioLangPref")
                    selectAudioTrack(match.id)
                } else {
                    Log.d("MPVPlayerEngine", "No audio track matching pref=$audioLangPref, keeping MPV default")
                }
            }

            if (subLangPref.isNotEmpty()) {
                val subs = _subtitleTracks.value.filter { it.id != -1 }
                val match = subs.firstOrNull { track ->
                    langMatches(track.lang, subLangPref)
                }
                if (match != null) {
                    Log.d("MPVPlayerEngine", "Auto-selecting subtitle track: ${match.name} (lang=${match.lang}) for pref=$subLangPref")
                    selectSubtitleTrack(match.id)
                } else {
                    Log.d("MPVPlayerEngine", "No subtitle track matching pref=$subLangPref, keeping MPV default")
                }
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to apply default language preferences", e)
        }
    }

    private fun langMatches(trackLang: String, pref: String): Boolean {
        if (trackLang.isBlank() || pref.isBlank()) return false
        val t = trackLang.trim().lowercase()
        val p = pref.trim().lowercase()
        return t == p || t.startsWith("$p-") || p.startsWith("$t-") ||
            t.take(2) == p.take(2)
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

    override fun stepFrameForward() {
        try {
            Log.d("MPVPlayerEngine", "Stepping frame forward")
            MPVLib.command("frame-step")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to step frame forward", e)
        }
    }

    override fun stepFrameBackward() {
        try {
            Log.d("MPVPlayerEngine", "Stepping frame backward")
            MPVLib.command("frame-back-step")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to step frame backward", e)
        }
    }

    override fun setMute(isMuted: Boolean) {
        try {
            MPVLib.setPropertyBoolean("mute", isMuted)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to set mute state", e)
        }
    }

    override fun isMuted(): Boolean {
        return try {
            MPVLib.getPropertyBoolean("mute") ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun getEstimatedFrameCount(): Long {
        try {
            val count = MPVLib.getPropertyInt("estimated-frame-count")
            if (count != null && count > 0) {
                return count.toLong()
            }
            val fps = getFps()
            val durSec = duration.value / 1000.0
            if (fps > 0.0 && durSec > 0.0) {
                return (durSec * fps).toLong()
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to get estimated frame count", e)
        }
        return 0L
    }

    override fun getEstimatedFrameNumber(): Long {
        try {
            val num = MPVLib.getPropertyInt("estimated-frame-number")
            if (num != null && num >= 0) {
                return num.toLong()
            }
            val fps = getFps()
            val posSec = currentPosition.value / 1000.0
            if (fps > 0.0 && posSec >= 0.0) {
                return (posSec * fps).toLong()
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to get estimated frame number", e)
        }
        return 0L
    }

    private fun getFps(): Double {
        try {
            val fps = MPVLib.getPropertyDouble("container-fps")
                ?: MPVLib.getPropertyDouble("fps")
            if (fps != null && fps > 0.0) {
                return fps
            }
        } catch (_: Exception) {}
        return 24.0
    }

    override fun seekToFrame(targetFrame: Long) {
        try {
            val fps = getFps()
            if (fps > 0.0) {
                val targetTimeSec = targetFrame / fps
                Log.d("MPVPlayerEngine", "Seeking to frame $targetFrame at time $targetTimeSec s (fps=$fps)")
                MPVLib.command("seek", targetTimeSec.toString(), "absolute+exact")
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to seek to frame $targetFrame", e)
        }
    }

    private var ambientShaderFile: File? = null
    private var ambientShaderSeq = 0
    private var lastCompiledShaderCode: String? = null
    private var isAmbientEnabledInternal = false
    private var ambientStyleInternal = com.devson.nvplayer.data.repository.AmbientBlurStyle.GLOW

    override fun setAmbientMode(
        isEnabled: Boolean,
        style: com.devson.nvplayer.data.repository.AmbientBlurStyle
    ) {
        isAmbientEnabledInternal = isEnabled
        ambientStyleInternal = style
        if (!isEnabled) {
            clearAmbientShader()
        } else {
            updateAmbientStretch()
        }
    }

    fun updateAmbientStretch() {
        if (!isAmbientEnabledInternal) {
            clearAmbientShader()
            return
        }

        try {
            var osdW = try { MPVLib.getPropertyInt("osd-width") ?: 0 } catch (e: Exception) { 0 }
            var osdH = try { MPVLib.getPropertyInt("osd-height") ?: 0 } catch (e: Exception) { 0 }
            var vidW = _videoWidth.value.toDouble()
            var vidH = _videoHeight.value.toDouble()

            // Fallback to display metrics if OSD dimensions are not yet reported by MPVLib
            if (osdW <= 0 || osdH <= 0) {
                val metrics = context.resources.displayMetrics
                osdW = metrics.widthPixels.coerceAtLeast(1)
                osdH = metrics.heightPixels.coerceAtLeast(1)
            }

            // Do not compute scale distortion if video or viewport dimensions are uninitialized
            if (osdW <= 0 || osdH <= 0 || vidW <= 0.0 || vidH <= 0.0) {
                return
            }

            val rot = _videoRotation.value
            if (rot == 90L || rot == 270L) {
                val tmp = vidW
                vidW = vidH
                vidH = tmp
            }

            val screenAr = osdW.toDouble() / osdH.toDouble()
            val vidAr = vidW / vidH

            val scaleX = if (screenAr > vidAr) screenAr / vidAr else 1.0
            val scaleY = if (vidAr > screenAr) vidAr / screenAr else 1.0

            MPVLib.setPropertyDouble("video-scale-x", scaleX)
            MPVLib.setPropertyDouble("video-scale-y", scaleY)

            val renderContext = com.devson.nvplayer.player.ambient.AmbientRenderContext(scaleX = scaleX, scaleY = scaleY)
            val sharedConfig = com.devson.nvplayer.player.ambient.AmbientSharedShaderConfig(
                bezelDepth = 0f,
                vignetteStrength = 0.55f,
                opacity = 1.0f
            )

            val spec: com.devson.nvplayer.player.ambient.AmbientShaderSpec = when (ambientStyleInternal.visualMode) {
                com.devson.nvplayer.player.ambient.AmbientVisualMode.GLOW ->
                    com.devson.nvplayer.player.ambient.AmbientGlowShaderSpec(
                        context = renderContext,
                        shared = sharedConfig,
                        blurSamples = 18,
                        maxRadius = 0.28f,
                        glowIntensity = 1.45f,
                        satBoost = 1.25f,
                        warmth = 0.0f,
                        fadeCurve = 1.7f
                    )
                com.devson.nvplayer.player.ambient.AmbientVisualMode.FRAME_EXTEND ->
                    com.devson.nvplayer.player.ambient.AmbientFrameExtendShaderSpec(
                        context = renderContext,
                        shared = sharedConfig,
                        sampleBudget = 24,
                        extendStrength = 0.70f,
                        detailProtection = 0.72f,
                        glowMix = 0.12f,
                        ditherNoise = 0.020f
                    )
            }

            val shaderCode = com.devson.nvplayer.player.ambient.AmbientShaderBuilder.build(spec)
            if (shaderCode == lastCompiledShaderCode && ambientShaderFile?.exists() == true) {
                return
            }
            lastCompiledShaderCode = shaderCode

            val newFile = File(context.cacheDir, "ambient_${++ambientShaderSeq}.glsl")
            newFile.writeText(shaderCode)
            ambientShaderFile?.delete()
            ambientShaderFile = newFile

            MPVLib.setOptionString("glsl-shaders", newFile.absolutePath)
            MPVLib.setPropertyString("glsl-shaders", newFile.absolutePath)
            Log.d("MPVPlayerEngine", "Applied True Ambient Mode shader (${ambientStyleInternal.displayName}) successfully (scaleX=$scaleX, scaleY=$scaleY)")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to update ambient stretch", e)
        }
    }

    private fun clearAmbientShader() {
        try {
            MPVLib.setPropertyDouble("video-scale-x", 1.0)
            MPVLib.setPropertyDouble("video-scale-y", 1.0)
            MPVLib.setOptionString("glsl-shaders", "")
            MPVLib.setPropertyString("glsl-shaders", "")
            ambientShaderFile?.delete()
            ambientShaderFile = null
            lastCompiledShaderCode = null
            Log.d("MPVPlayerEngine", "Cleared ambient mode GLSL shaders successfully")
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to clear ambient shader", e)
        }
    }

    private var pendingRestoreSid: String? = null
    private var pendingRestoreAid: String? = null

    override fun getAdaptiveTracks(): List<VideoQualityOption> {
        val list = mutableListOf<VideoQualityOption>()
        try {
            val count = MPVLib.getPropertyInt("track-list/count") ?: 0
            val currentVid = try { MPVLib.getPropertyString("vid") } catch (_: Exception) { "auto" }

            list.add(
                VideoQualityOption(
                    id = "auto",
                    label = "Auto (Recommended)",
                    isAuto = true,
                    isSelected = (currentVid == "auto" || currentVid.isNullOrBlank())
                )
            )

            val videoTracks = mutableListOf<VideoQualityOption>()
            for (i in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$i/type") ?: ""
                if (type != "video") continue

                val id = MPVLib.getPropertyInt("track-list/$i/id") ?: (i + 1)
                val h = MPVLib.getPropertyInt("track-list/$i/demux-h") ?: 0
                val w = MPVLib.getPropertyInt("track-list/$i/demux-w") ?: 0
                val fps = MPVLib.getPropertyDouble("track-list/$i/demux-fps")?.toInt()
                    ?: MPVLib.getPropertyInt("track-list/$i/demux-fps") ?: 0
                val bitrate = MPVLib.getPropertyLong("track-list/$i/demux-bitrate")
                    ?: (MPVLib.getPropertyInt("track-list/$i/demux-bitrate")?.toLong() ?: 0L)
                val isSelected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false

                if (h > 0) {
                    videoTracks.add(
                        VideoQualityOption(
                            id = id.toString(),
                            label = StreamQualityHelper.formatResolutionLabel(h, w, fps),
                            height = h,
                            width = w,
                            bitrate = bitrate,
                            fps = fps,
                            isSelected = isSelected || (currentVid == id.toString())
                        )
                    )
                }
            }

            val distinctTracks = videoTracks
                .groupBy { it.height }
                .map { (_, options) ->
                    options.maxByOrNull { it.bitrate } ?: options.first()
                }
                .sortedByDescending { it.height }

            list.addAll(distinctTracks)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to extract adaptive tracks", e)
        }
        return list
    }

    override fun changeStreamQuality(
        quality: VideoQualityOption,
        streamUrl: String,
        streamType: StreamType
    ) {
        when (streamType) {
            StreamType.ADAPTIVE_MANIFEST -> {
                try {
                    if (quality.isAuto) {
                        MPVLib.setPropertyString("vid", "auto")
                    } else {
                        val trackId = quality.id.toIntOrNull()
                        if (trackId != null) {
                            MPVLib.setPropertyInt("vid", trackId)
                        } else {
                            MPVLib.setPropertyString("vid", quality.id)
                        }
                    }
                    updateTracks()
                } catch (e: Exception) {
                    Log.e("MPVPlayerEngine", "Failed to change adaptive stream quality", e)
                }
            }
            StreamType.PLATFORM_URL -> {
                try {
                    val currentPos = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                    val currentSid = MPVLib.getPropertyString("sid") ?: "no"
                    val currentAid = MPVLib.getPropertyString("aid") ?: "auto"

                    pendingRestoreSid = currentSid
                    pendingRestoreAid = currentAid

                    val formatString = if (quality.isAuto) {
                        "bestvideo+bestaudio/best"
                    } else {
                        "bestvideo[height<=?${quality.height}]+bestaudio/best[height<=?${quality.height}]"
                    }

                    MPVLib.setPropertyString("ytdl-format", formatString)

                    val options = buildList {
                        add("start=$currentPos")
                        if (currentSid.isNotBlank() && currentSid != "no") add("sid=$currentSid")
                        if (currentAid.isNotBlank() && currentAid != "auto") add("aid=$currentAid")
                    }.joinToString(",")

                    Log.d("MPVPlayerEngine", "Reloading platform stream at pos $currentPos with format: $formatString, options: $options")
                    MPVLib.command("loadfile", streamUrl, "replace", options)
                } catch (e: Exception) {
                    Log.e("MPVPlayerEngine", "Failed to change platform stream quality", e)
                }
            }
            StreamType.DIRECT_FILE, StreamType.UNKNOWN -> {
                // Progressive single-quality files cannot be switched
            }
        }
    }

    private fun restorePendingQualityTracks() {
        try {
            val sid = pendingRestoreSid
            if (!sid.isNullOrBlank()) {
                Log.d("MPVPlayerEngine", "Restoring subtitle track after quality reload: $sid")
                MPVLib.setPropertyString("sid", sid)
                if (sid == "no") {
                    MPVLib.setPropertyString("sub-visibility", "no")
                    _currentSubtitleText.value = ""
                } else {
                    MPVLib.setPropertyString("sub-visibility", "yes")
                }
                pendingRestoreSid = null
            }
            val aid = pendingRestoreAid
            if (!aid.isNullOrBlank()) {
                Log.d("MPVPlayerEngine", "Restoring audio track after quality reload: $aid")
                MPVLib.setPropertyString("aid", aid)
                pendingRestoreAid = null
            }
        } catch (e: Exception) {
            Log.w("MPVPlayerEngine", "Failed to restore tracks after quality reload", e)
        }
    }

    override fun release() {
        Log.d("MPVPlayerEngine", "Releasing MPVPlayerEngine resources")
        activeInstance = null
        try {
            // Instantly stop playback, force hardware decoder release and detach surface
            MPVLib.command("stop")
            MPVLib.setOptionString("hwdec", "no")
            MPVLib.setPropertyString("hwdec", "no")
            MPVLib.setPropertyString("vo", "null")
            MPVLib.detachSurface()

            MPVLib.removeObserver(this)
            MPVLib.destroy()
            isInitialized = false
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
            "video-params/w" -> {
                _videoWidth.value = value
                if (isAmbientEnabledInternal) updateAmbientStretch()
            }
            "video-params/h" -> {
                _videoHeight.value = value
                if (isAmbientEnabledInternal) updateAmbientStretch()
            }
            "osd-width", "osd-height" -> {
                if (isAmbientEnabledInternal) updateAmbientStretch()
            }
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
                applyDefaultLanguagePreferences()
                restorePendingQualityTracks()
                updateChapters()
                togglePerformanceOverlayFromConf()
                val settings = settingsRepo.playbackSettingsFlow.value
                setAmbientMode(settings.isAmbientModeEnabled, settings.ambientBlurStyle)
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

    private fun applyMpvConfOptions() {
        val mpvConfFile = File(context.filesDir, "mpv.conf")
        if (!mpvConfFile.exists()) return
        Log.d("MPVPlayerEngine", "Reading mpv.conf options for dynamic application in loadVideo")
        try {
            mpvConfFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx != -1) {
                        val key = trimmed.substring(0, eqIdx).trim()
                        val value = trimmed.substring(eqIdx + 1).trim()
                        if (key.isNotEmpty()) {
                            try {
                                MPVLib.setPropertyString(key, value)
                                Log.d("MPVPlayerEngine", "Dynamic setPropertyString: $key = $value")
                            } catch (e: Exception) {
                                try {
                                    MPVLib.setOptionString(key, value)
                                    Log.d("MPVPlayerEngine", "Dynamic setOptionString: $key = $value")
                                } catch (ex: Exception) {
                                    Log.e("MPVPlayerEngine", "Failed to dynamically set property/option: $key = $value", ex)
                                }
                            }
                        }
                    } else {
                        val key = trimmed
                        try {
                            MPVLib.setPropertyString(key, "yes")
                        } catch (e: Exception) {
                            try {
                                MPVLib.setPropertyString(key, "")
                            } catch (ex: Exception) {
                                try {
                                    MPVLib.setOptionString(key, "yes")
                                } catch (ex2: Exception) {
                                    Log.e("MPVPlayerEngine", "Failed to dynamically set flag: $key", ex2)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Error dynamically applying mpv.conf in loadVideo", e)
        }
    }

    private fun togglePerformanceOverlayFromConf() {
        val mpvConfFile = File(context.filesDir, "mpv.conf")
        if (!mpvConfFile.exists()) return
        try {
            var showStats = false
            mpvConfFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("stats-ring=yes")) {
                    showStats = true
                }
            }
            if (showStats) {
                MPVLib.command("script-binding", "stats/display-stats")
                Log.d("MPVPlayerEngine", "Performance overlay displayed via script-binding")
            }
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Failed to toggle performance overlay", e)
        }
    }

    private fun copyRequiredAssets(context: Context) {
        try {
            `is`.xyz.mpv.Utils.copyAssets(context)
        } catch (e: Exception) {
            Log.e("MPVPlayerEngine", "Utils.copyAssets failed, falling back to manual copy", e)
        }

        listOf("subfont.ttf", "cacert.pem").forEach { fileName ->
            val destFile = File(context.filesDir, fileName)
            if (!destFile.exists() || destFile.length() == 0L) {
                try {
                    context.assets.open(fileName).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d("MPVPlayerEngine", "Copied asset $fileName to ${destFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e("MPVPlayerEngine", "Failed to copy asset: $fileName", e)
                }
            }
        }
    }
}